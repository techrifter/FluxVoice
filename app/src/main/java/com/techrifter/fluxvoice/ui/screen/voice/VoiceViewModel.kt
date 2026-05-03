package com.techrifter.fluxvoice.ui.screen.voice

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.core.util.DispatcherProvider
import com.techrifter.fluxvoice.data.audio.SharedAudioCapture
import com.techrifter.fluxvoice.data.audio.VoiceActivityDetector
import com.techrifter.fluxvoice.data.elevenlabs.ElevenLabsVoiceSession
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.VoiceMode
import com.techrifter.fluxvoice.domain.repository.ConversationRepository
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import com.techrifter.fluxvoice.domain.stt.SttEngine
import com.techrifter.fluxvoice.domain.stt.SttState
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import com.techrifter.fluxvoice.domain.tts.TtsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "FluxVoice"

/** Short spoken acknowledgments played while the LLM is thinking. */
private val BACKCHANNELS = listOf("Got it.", "Sure.", "Mm-hmm.", "Okay.", "Let me think.")

/**
 * Orchestrates the full voice conversation pipeline.
 *
 * **Two pipeline modes (selected via [AppSettings.voiceMode]):**
 *
 * 1. [VoiceMode.STANDARD]   — Deepgram STT → LLM streaming → Cartesia/Android TTS
 * 2. [VoiceMode.ELEVENLABS] — ElevenLabs ConvAI WebRTC (all-in-one STT + LLM + TTS)
 *
 * State machine: IDLE → LISTENING → THINKING → SPEAKING → IDLE
 *
 * Key UX features:
 *  - Auto barge-in via [VoiceActivityDetector] (AEC‑equipped mic amplitude monitor)
 *  - Thinking backchannels ("Got it.", "Sure."…) spoken before LLM response arrives
 *  - Retry on transient LLM errors (up to [MAX_LLM_RETRIES] attempts)
 *  - Adaptive response length (short queries → 1–2 sentence replies)
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    private val elevenLabsSession: ElevenLabsVoiceSession,
    private val vadDetector: VoiceActivityDetector,
    /** Shared mic capture — held alive for the duration of the voice session so the
     *  AudioRecord never stops between turns (eliminates the broken-pipe race window). */
    private val sharedAudioCapture: SharedAudioCapture
) : ViewModel() {

    companion object {
        private const val MAX_LLM_RETRIES = 2

        /** Stop listening if no speech arrives within this window (ms). */
        private const val NO_SPEECH_TIMEOUT_MS = 7_000L

        /**
         * How long to wait for the first LLM token before playing a backchannel phrase.
         * Fast providers (Groq) typically respond in < 500 ms, so we only speak a filler
         * when the model is genuinely slow — avoiding jarring double-audio on quick replies.
         */
        private const val BACKCHANNEL_DELAY_MS = 1_500L
    }

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    /** Cached settings — updated reactively. */
    private val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Active LLM streaming job — cancellable for interrupt-and-restart. */
    private var streamingJob: Job? = null

    /** Pending backchannel job — cancelled when the first LLM token arrives. */
    private var backchannelJob: Job? = null

    /**
     * Safety net: if the mic has been open for [NO_SPEECH_TIMEOUT_MS] with no
     * partial transcript arriving, the user has gone silent (or a false barge-in
     * left us stuck in LISTENING).  Force-stop STT so the state machine can
     * progress rather than sitting in LISTENING indefinitely.
     */
    private var noSpeechTimeoutJob: Job? = null

    /**
     * True once [StreamEvent.Done] has been received.
     * Guards against transitioning to IDLE before TTS finishes the last sentence.
     */
    @Volatile private var isStreamingComplete = false

    /**
     * True from when a backchannel filler phrase is queued until the first actual
     * LLM sentence is sent to TTS.
     *
     * **Purpose**: the VAD must NOT run during the pure backchannel phase.
     * The gap between a filler word ending and the LLM audio arriving (Cartesia HTTP
     * latency) is silent. If VAD is active during this window it fires on ambient
     * noise, calls [triggerBargeIn], cancels the LLM response, and opens the mic —
     * causing simultaneous TTS + STT playback the next time the response lands.
     */
    @Volatile private var isBackchannelPhase = false

    init {
        Log.d(TAG, "VoiceViewModel created")
        observeSttState()
        observeTtsState()
        observeSettingsChanges()
        observeElevenLabsSession()
    }

    // ── Active session keep-alive ─────────────────────────────────────────────

    /**
     * True while a STANDARD-mode voice session is in progress.
     * Guards against calling keepAlive/releaseKeepAlive more than once.
     */
    @Volatile private var captureSessionActive = false

    /** Start the shared AudioRecord and keep it alive across turns. */
    private fun acquireCaptureSession() {
        if (!captureSessionActive) {
            captureSessionActive = true
            sharedAudioCapture.keepAlive()
            Log.d(TAG, "SharedAudioCapture: session keep-alive acquired")
        }
    }

    /** Release the keep-alive so AudioRecord can stop after onCleared/clearConversation. */
    private fun releaseCaptureSession() {
        if (captureSessionActive) {
            captureSessionActive = false
            sharedAudioCapture.releaseKeepAlive()
            Log.d(TAG, "SharedAudioCapture: session keep-alive released")
        }
    }

    // ── Activity context (needed for ElevenLabs WebRTC) ───────────────────────

    /**
     * Pass the host Activity so the ElevenLabs SDK can set up its WebRTC peer connection.
     * Call once from VoiceScreen's LaunchedEffect.
     */
    fun attachActivity(activity: Activity) {
        elevenLabsSession.attachActivity(activity)
    }

    // ── STT observation (STANDARD mode only) ──────────────────────────────────

    private fun observeSttState() {
        viewModelScope.launch {
            sttEngine.state.collect { sttState ->
                if (settings.value.voiceMode != VoiceMode.STANDARD) return@collect
                when (sttState) {
                    SttState.Listening -> {
                        _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                        // Safety net: if TTS is somehow still active when mic opens, kill it.
                        interruptTtsIfSpeaking()
                        // Arm the no-speech guard: if nothing arrives for NO_SPEECH_TIMEOUT_MS,
                        // force-close the mic rather than sitting in LISTENING forever.
                        armNoSpeechTimeout()
                    }
                    is SttState.PartialResult -> {
                        _uiState.update { it.copy(partialTranscript = sttState.text) }
                        // User is actively speaking — reset the timeout window.
                        armNoSpeechTimeout()
                        // NOTE: do NOT call interruptTtsIfSpeaking() here.
                        // Doing so creates an echo loop: mic picks up TTS output →
                        // Deepgram emits PartialResult → we stop TTS → echo text becomes
                        // FinalResult → AI replies to its own words → infinite cycle.
                        // Hardware AEC on SharedAudioCapture suppresses this at the source.
                    }
                    is SttState.FinalResult -> {
                        cancelNoSpeechTimeout()
                        Log.d(TAG, "STT FinalResult: '${sttState.text}'")
                        // Close the mic immediately — TTS is about to start.
                        sttEngine.stopListening()
                        // Pre-warm the Deepgram WebSocket NOW, while the AI thinks + speaks
                        // (typically 3–10 s). By the time TTS finishes the socket is ready and
                        // startListening() will open the mic with zero connection latency.
                        sttEngine.preConnect()
                        _uiState.update {
                            it.copy(
                                voiceState        = VoiceState.THINKING,
                                userTranscript    = sttState.text,
                                partialTranscript = "",
                                aiResponse        = ""
                            )
                        }
                        startAiResponse(sttState.text)
                    }
                    is SttState.Error -> {
                        cancelNoSpeechTimeout()
                        _uiState.update {
                            it.copy(voiceState = VoiceState.IDLE, errorMessage = userFacingError(sttState.message))
                        }
                    }
                    SttState.Idle -> {
                        cancelNoSpeechTimeout()
                        if (_uiState.value.voiceState == VoiceState.LISTENING) {
                            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                        }
                    }
                }
            }
        }
    }

    // ── TTS observation + auto barge-in ───────────────────────────────────────

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsEngine.state.collect { ttsState ->
                if (settings.value.voiceMode != VoiceMode.STANDARD) return@collect
                when (ttsState) {
                    TtsState.Speaking -> {
                        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
                        // Hard mutual-exclusion guarantee: STT mic must be OFF while TTS speaks.
                        sttEngine.stopListening()
                        // Start VAD so the user can interrupt without tapping the mic button.
                        // VAD-triggered barge-in is suppressed during the backchannel phase
                        // (see triggerBargeIn), so it is safe to arm it here unconditionally.
                        if (settings.value.vadEnabled && !vadDetector.isRunning) {
                            vadDetector.start(threshold = settings.value.vadSensitivity) {
                                Log.d(TAG, "VAD: auto barge-in triggered")
                                viewModelScope.launch {
                                    triggerBargeIn(fromVad = true)
                                }
                            }
                        }
                    }
                    TtsState.Idle -> {
                        vadDetector.stop()
                        if (_uiState.value.voiceState == VoiceState.SPEAKING) {
                            // Primary condition: isStreamingComplete is set by StreamEvent.Done.
                            // Fallback condition: streamingJob is completed, meaning StreamEvent.Done
                            // was fully processed.  This recovers from the rare race where
                            // finishTurnAndRestart() was called prematurely from StreamEvent.Done
                            // (when TTS happened to be Idle at check-time) and reset
                            // isStreamingComplete=false before the REAL TTS Idle arrived.
                            val jobDone = streamingJob?.isCompleted == true
                            if (isStreamingComplete || jobDone) {
                                Log.d(TAG, "TTS idle → finishing turn " +
                                    "(isStreamingComplete=$isStreamingComplete, jobDone=$jobDone)")
                                finishTurnAndRestart()
                            }
                        }
                    }
                    is TtsState.Error -> {
                        vadDetector.stop()
                        // Only surface TTS errors that require user action (misconfiguration).
                        // Transient network errors are non-fatal — Android TTS handles fallback.
                        val msg = when {
                            ttsState.message.contains("API key", ignoreCase = true) ||
                            ttsState.message.contains("not configured", ignoreCase = true) ->
                                "Voice synthesis isn't configured. Check your Cartesia API key in Settings."
                            ttsState.message.contains("401", ignoreCase = true) ||
                            ttsState.message.contains("403", ignoreCase = true) ->
                                "Cartesia API key is invalid. Please update it in Settings."
                            else -> null
                        }
                        if (msg != null) _uiState.update { it.copy(errorMessage = msg) }
                    }
                }
            }
        }
    }

    /**
     * Stop TTS + LLM and immediately restart listening.
     *
     * @param fromVad True when called from the VAD callback (automatic barge-in).
     *   When [fromVad] is true and we are still in the backchannel filler phase,
     *   the trigger is a false positive caused by ambient noise during the silent
     *   gap between "Got it." and the first Cartesia LLM audio chunk.
     *   In that case we suppress the interrupt and immediately re-arm the VAD so
     *   it is ready when real content begins playing.
     *
     *   Manual taps (fromVad = false) always proceed regardless of phase.
     */
    private fun triggerBargeIn(fromVad: Boolean = false) {
        if (fromVad && isBackchannelPhase) {
            Log.d(TAG, "VAD: false-positive barge-in suppressed during backchannel phase")
            // VAD stopped itself before this callback. Re-arm it immediately so it
            // is ready for the LLM content that is about to start playing.
            if (settings.value.vadEnabled) {
                vadDetector.start(threshold = settings.value.vadSensitivity) {
                    Log.d(TAG, "VAD: auto barge-in triggered (re-armed)")
                    viewModelScope.launch { triggerBargeIn(fromVad = true) }
                }
            }
            return
        }
        backchannelJob?.cancel()
        backchannelJob = null
        streamingJob?.cancel()
        ttsEngine.stop()
        vadDetector.stop()
        isStreamingComplete = false
        isBackchannelPhase = false
        // Signal to UI that we are in the interrupt transition — prevents the
        // briefly-IDLE flash that previously appeared while echo decayed.
        _uiState.update { it.copy(voiceState = VoiceState.INTERRUPTING) }
        // Pre-connect during the echo-decay delay so the socket is ready when we listen.
        sttEngine.preConnect()
        viewModelScope.launch {
            kotlinx.coroutines.delay(300) // 300 ms for echo to clear from mic
            startListening()
        }
    }

    // ── No-speech timeout ─────────────────────────────────────────────────────

    /**
     * Start (or restart) the no-speech watchdog.
     *
     * Called on every [SttState.Listening] and [SttState.PartialResult] event.
     * If the user goes silent (or a false barge-in left the mic open on an empty
     * room), this fires after [NO_SPEECH_TIMEOUT_MS] and force-stops STT.
     *
     * [sttEngine.stopListening] will emit [SttState.FinalResult] if there is
     * pending partial text, or just transition to [SttState.Idle] if the session
     * was silent — so the state machine always progresses cleanly.
     */
    private fun armNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(NO_SPEECH_TIMEOUT_MS)
            if (_uiState.value.voiceState == VoiceState.LISTENING) {
                Log.d(TAG, "No-speech timeout — closing mic")
                sttEngine.stopListening()
                // Update UI immediately — do not wait for SttState.Idle which depends
                // on the WebSocket closing cleanly. If the socket is already gone the
                // callback never fires and the UI stays stuck on LISTENING.
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }

    private fun cancelNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = null
    }

    /**
     * Stop TTS and cancel LLM streaming because the STT engine confirmed the user is
     * already speaking (Listening or PartialResult received while TTS was still playing).
     * Unlike [triggerBargeIn] we do NOT restart listening — the STT mic is already open.
     */
    private fun interruptTtsIfSpeaking() {
        if (ttsEngine.state.value != TtsState.Speaking) return
        Log.d(TAG, "interruptTtsIfSpeaking: stopping TTS — user is speaking")
        backchannelJob?.cancel()
        backchannelJob = null
        streamingJob?.cancel()
        ttsEngine.stop()
        vadDetector.stop()
        isStreamingComplete = false
        isBackchannelPhase = false
        // STT is already running; the pipeline continues naturally via FinalResult.
    }

    // ── ElevenLabs session observation ────────────────────────────────────────

    private fun observeElevenLabsSession() {
        viewModelScope.launch {
            elevenLabsSession.status.collect { status ->
                if (settings.value.voiceMode != VoiceMode.ELEVENLABS) return@collect
                when (status) {
                    "connecting"   -> _uiState.update { it.copy(voiceState = VoiceState.THINKING) }
                    "connected"    -> _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                    "disconnected" -> _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
                }
            }
        }
        viewModelScope.launch {
            elevenLabsSession.conversationMode.collect { mode ->
                if (settings.value.voiceMode != VoiceMode.ELEVENLABS) return@collect
                if (elevenLabsSession.status.value != "connected") return@collect
                when (mode) {
                    "speaking"  -> _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
                    "listening" -> _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                }
            }
        }
        viewModelScope.launch {
            elevenLabsSession.userTranscript.collect { text ->
                if (settings.value.voiceMode != VoiceMode.ELEVENLABS) return@collect
                if (text.isNotBlank()) _uiState.update { it.copy(userTranscript = text) }
            }
        }
        viewModelScope.launch {
            elevenLabsSession.aiResponse.collect { text ->
                if (settings.value.voiceMode != VoiceMode.ELEVENLABS) return@collect
                if (text.isNotBlank()) _uiState.update { it.copy(aiResponse = text) }
            }
        }
        viewModelScope.launch {
            elevenLabsSession.error.collect { error ->
                if (settings.value.voiceMode != VoiceMode.ELEVENLABS) return@collect
                if (!error.isNullOrBlank()) {
                    _uiState.update { it.copy(errorMessage = error) }
                    elevenLabsSession.clearError()
                }
            }
        }
    }

    // ── Settings sync ─────────────────────────────────────────────────────────

    private fun observeSettingsChanges() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                ttsEngine.setSpeechRate(s.ttsSpeechRate)
                ttsEngine.setPitch(s.ttsPitch)
                _uiState.update { it.copy(voiceMode = s.voiceMode, appMode = s.appMode) }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry point from the UI mic button.
     *
     * STANDARD mode:
     *   IDLE → start listening | LISTENING → stop early | THINKING → cancel | SPEAKING → barge-in
     *
     * ELEVENLABS mode:
     *   IDLE → start session | any active state → end session
     */
    fun updateAppMode(mode: AppMode) = viewModelScope.launch {
        backchannelJob?.cancel()
        backchannelJob = null
        streamingJob?.cancel()
        cancelNoSpeechTimeout()
        ttsEngine.stop()
        sttEngine.stopListening()
        vadDetector.stop()
        elevenLabsSession.stop()
        releaseCaptureSession()
        isStreamingComplete = false
        isBackchannelPhase = false
        _uiState.update { it.copy(voiceState = VoiceState.IDLE, partialTranscript = "") }
        settingsRepository.updateAppMode(mode.name)
        when (mode) {
            AppMode.FAST     -> settingsRepository.applyFastPreset()
            AppMode.THINKING -> settingsRepository.applyThinkingPreset()
            AppMode.CUSTOM   -> settingsRepository.applyCustomPreset()
        }
    }

    fun onMicTap() {
        when (settings.value.voiceMode) {
            VoiceMode.ELEVENLABS -> handleElevenLabsTap()
            VoiceMode.STANDARD   -> handleStandardTap()
        }
    }

    private fun handleElevenLabsTap() {
        when (_uiState.value.voiceState) {
            VoiceState.IDLE -> {
                val agentId = settings.value.elevenLabsAgentId
                if (agentId.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = "ElevenLabs Agent ID not configured — set it in Settings")
                    }
                    return
                }
                _uiState.update {
                    it.copy(voiceState = VoiceState.THINKING, aiResponse = "", userTranscript = "")
                }
                viewModelScope.launch(dispatchers.io) {
                    elevenLabsSession.start(agentId)
                }
            }
            else -> elevenLabsSession.stop()
        }
    }

    private fun handleStandardTap() {
        when (_uiState.value.voiceState) {
            VoiceState.IDLE         -> startListening()
            VoiceState.LISTENING    -> {
                sttEngine.stopListening()
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
            VoiceState.THINKING     -> {
                backchannelJob?.cancel()
                backchannelJob = null
                streamingJob?.cancel()
                ttsEngine.stop()
                vadDetector.stop()
                isStreamingComplete = false
                isBackchannelPhase = false
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
            VoiceState.SPEAKING      -> triggerBargeIn()
            VoiceState.INTERRUPTING  -> { /* already transitioning — ignore tap */ }
        }
    }

    fun onPermissionGranted() {
        _uiState.update { it.copy(hasAudioPermission = true) }
        startListening()
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(errorMessage = "Microphone permission is required for voice conversations.")
        }
    }

    fun clearConversation() {
        backchannelJob?.cancel()
        backchannelJob = null
        streamingJob?.cancel()
        cancelNoSpeechTimeout()
        ttsEngine.stop()
        sttEngine.stopListening()
        vadDetector.stop()
        elevenLabsSession.stop()
        conversationRepository.clearHistory()
        releaseCaptureSession()
        isStreamingComplete = false
        isBackchannelPhase = false
        _uiState.update {
            it.copy(
                voiceState        = VoiceState.IDLE,
                partialTranscript = "",
                userTranscript    = "",
                aiResponse        = "",
                errorMessage      = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Internal pipeline ─────────────────────────────────────────────────────

    private fun startListening() {
        // Hard guard: never open the mic while TTS is playing.
        if (ttsEngine.state.value == TtsState.Speaking) {
            Log.w(TAG, "startListening: skipped — TTS still speaking")
            return
        }
        // Ensure the shared AudioRecord stays alive for the whole session so there
        // is no stop/restart gap between turns (which causes the broken-pipe race).
        acquireCaptureSession()
        _uiState.update { it.copy(partialTranscript = "", voiceState = VoiceState.LISTENING) }
        sttEngine.startListening()
    }

    /**
     * Starts the AI response pipeline with:
     *  1. Immediate backchannel acknowledgment (fills LLM latency gap)
     *  2. Adaptive system prompt (short queries → concise replies)
     *  3. LLM streaming with sentence-level TTS
     *  4. Retry on transient errors (up to [MAX_LLM_RETRIES])
     */
    private fun startAiResponse(userInput: String, retryAttempt: Int = 1) {
        Log.d(TAG, "startAiResponse: '$userInput' (attempt $retryAttempt)")
        isStreamingComplete = false

        // ── 1. Deferred backchannel ───────────────────────────────────────────
        // Only speak a filler phrase if the LLM hasn't returned its first token
        // within BACKCHANNEL_DELAY_MS. Fast providers (Groq) typically respond in
        // < 500 ms — firing immediately would create jarring double-audio. The job
        // is cancelled in StreamEvent.Token so it never fires on quick replies.
        isBackchannelPhase = false
        backchannelJob?.cancel()
        if (settings.value.ttsEnabled && settings.value.backchannelEnabled) {
            backchannelJob = viewModelScope.launch {
                kotlinx.coroutines.delay(BACKCHANNEL_DELAY_MS)
                if (_uiState.value.voiceState == VoiceState.THINKING &&
                    settings.value.ttsEnabled && settings.value.backchannelEnabled
                ) {
                    isBackchannelPhase = true
                    ttsEngine.speak(BACKCHANNELS.random(), UUID.randomUUID().toString())
                }
            }
        }

        streamingJob = viewModelScope.launch(dispatchers.io) {
            val tokenBuffer  = StringBuilder()
            val fullResponse = StringBuilder()

            // ── 2. Adaptive system-prompt suffix ─────────────────────────────
            // Detect query complexity by word count. Short queries get a concise-
            // reply instruction appended so the model matches the conversational pace.
            val wordCount = userInput.trim().split("\\s+".toRegex()).size
            val lengthHint = when {
                wordCount <= 4  -> "\nRespond in 1 sentence."
                wordCount <= 10 -> "\nRespond in 1–2 sentences."
                else            -> "" // let the model decide for complex queries
            }
            val effectivePrompt = settings.value.systemPrompt + lengthHint

            try {
                conversationRepository.streamResponse(userInput, effectivePrompt)
                    .collect { event ->
                        when (event) {
                            StreamEvent.Start -> {
                                Log.d(TAG, "StreamEvent.Start")
                                _uiState.update { it.copy(voiceState = VoiceState.THINKING) }
                            }
                            is StreamEvent.Token -> {
                                // First token arrived — cancel the pending backchannel so it
                                // never fires. If it already fired (slow model), this is a no-op.
                                backchannelJob?.cancel()
                                backchannelJob = null
                                Log.v(TAG, "StreamEvent.Token: '${event.text}'")
                                tokenBuffer.append(event.text)
                                fullResponse.append(event.text)
                                _uiState.update { it.copy(aiResponse = fullResponse.toString()) }

                                if (settings.value.ttsEnabled) {
                                    val wasBackchannel = isBackchannelPhase
                                    extractSentences(tokenBuffer).forEach { sentence ->
                                        // First LLM sentence spoken → backchannel phase ends.
                                        isBackchannelPhase = false
                                        ttsEngine.speak(sentence, UUID.randomUUID().toString())
                                    }
                                    // If backchannel flowed straight into LLM audio with no Idle
                                    // gap, observeTtsState won't fire a new Speaking event and VAD
                                    // will never re-arm itself.  Kick it here explicitly so barge-in
                                    // works during the actual AI response.
                                    if (wasBackchannel && !isBackchannelPhase &&
                                        settings.value.vadEnabled && !vadDetector.isRunning &&
                                        ttsEngine.state.value == TtsState.Speaking
                                    ) {
                                        Log.d(TAG, "VAD: re-arming after backchannel phase ended")
                                        vadDetector.start(threshold = settings.value.vadSensitivity) {
                                            Log.d(TAG, "VAD: auto barge-in triggered (post-backchannel)")
                                            viewModelScope.launch { triggerBargeIn(fromVad = true) }
                                        }
                                    }
                                }
                                if (_uiState.value.voiceState == VoiceState.THINKING) {
                                    _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
                                }
                            }
                            StreamEvent.Done -> {
                                Log.d(TAG, "StreamEvent.Done, ttsEnabled=${settings.value.ttsEnabled}")
                                val remaining = tokenBuffer.toString().trim()
                                if (remaining.isNotEmpty() && settings.value.ttsEnabled) {
                                    isBackchannelPhase = false   // final chunk = real LLM content
                                    ttsEngine.speak(remaining, UUID.randomUUID().toString())
                                } else {
                                    isBackchannelPhase = false
                                }
                                tokenBuffer.clear()
                                isStreamingComplete = true
                                // When TTS is disabled there is no TtsState.Idle coming — finish now.
                                // When TTS is enabled, let observeTtsState(TtsState.Idle) call
                                // finishTurnAndRestart() so we never race-reset isStreamingComplete
                                // before the actual audio finishes playing.
                                if (!settings.value.ttsEnabled) {
                                    finishTurnAndRestart()
                                }
                            }
                            is StreamEvent.Error -> {
                                Log.e(TAG, "StreamEvent.Error: ${event.cause.message}", event.cause)
                                // ── 3. Retry on transient errors ─────────────
                                if (retryAttempt < MAX_LLM_RETRIES) {
                                    val delay = 600L * retryAttempt
                                    Log.w(TAG, "Retrying in ${delay}ms (attempt $retryAttempt/$MAX_LLM_RETRIES)")
                                    isStreamingComplete = false
                                    tokenBuffer.clear()
                                    fullResponse.clear()
                                    kotlinx.coroutines.delay(delay)
                                    startAiResponse(userInput, retryAttempt + 1)
                                } else {
                                    isStreamingComplete = false
                                    _uiState.update {
                                        it.copy(
                                            voiceState   = VoiceState.IDLE,
                                            errorMessage = userFacingError(event.cause.message ?: "")
                                        )
                                    }
                                }
                            }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (retryAttempt < MAX_LLM_RETRIES) {
                    kotlinx.coroutines.delay(600L * retryAttempt)
                    startAiResponse(userInput, retryAttempt + 1)
                } else {
                    isStreamingComplete = false
                    _uiState.update {
                        it.copy(
                            voiceState   = VoiceState.IDLE,
                            errorMessage = userFacingError(e.message ?: "")
                        )
                    }
                }
            }
        }
    }

    // ── Finish turn & restart ─────────────────────────────────────────────────

    private fun finishTurnAndRestart() {
        isStreamingComplete = false
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        // Pre-connect now — socket will be ready in ~300 ms, well before the delay expires.
        sttEngine.preConnect()
        viewModelScope.launch {
            // 600 ms gives echo time to decay; combined with the pre-warmed socket the
            // total dead-air from TTS-idle to mic-open is ≈ 600 ms (was ≈ 1800 ms).
            kotlinx.coroutines.delay(600)
            if (_uiState.value.voiceState == VoiceState.IDLE &&
                settings.value.voiceMode == VoiceMode.STANDARD
            ) {
                startListening()
            }
        }
    }

    // ── Error message mapping ─────────────────────────────────────────────────

    /**
     * Converts raw exception messages into calm, actionable user-facing copy.
     * Rules: never show stack traces, API jargon, or HTTP status codes.
     */
    private fun userFacingError(raw: String): String = when {
        raw.contains("not configured", ignoreCase = true) ||
        raw.contains("API key", ignoreCase = true)        -> raw
        raw.contains("Connection lost", ignoreCase = true) ||
        raw.contains("Couldn't connect", ignoreCase = true) -> raw
        raw.contains("permission", ignoreCase = true)     ->
            "Microphone access is needed. Please grant the permission in your device settings."
        raw.contains("401", ignoreCase = true)            ->
            "API key is invalid or expired. Please update it in Settings."
        raw.contains("429", ignoreCase = true)            ->
            "Service is busy right now. Please wait a moment and try again."
        raw.contains("timeout", ignoreCase = true) ||
        raw.contains("timed out", ignoreCase = true)      ->
            "The request timed out. Check your internet connection and try again."
        raw.contains("broken pipe", ignoreCase = true) ||
        raw.contains("Socket closed", ignoreCase = true) ||
        raw.contains("EOF", ignoreCase = true)            ->
            "Connection interrupted. Tap the mic to try again."
        raw.isEmpty()                                     ->
            "Something went wrong. Tap the mic to try again."
        else -> "Something went wrong. Tap the mic to try again."
    }

    // ── Sentence extraction ───────────────────────────────────────────────────

    private fun extractSentences(buffer: StringBuilder): List<String> {
        val text = buffer.toString()
        val sentences = mutableListOf<String>()
        var lastIndex = 0
        // Negative lookbehind for digits prevents splitting on numbered-list markers
        // like "1. " or "2. ".  Lookahead requires the next word to start with a
        // capital letter or opening quote, avoiding mid-sentence abbreviation splits.
        val regex = Regex("""(?<![0-9])[.!?]+\s+(?=[A-Z"'])""")
        for (match in regex.findAll(text)) {
            val end = match.range.last + 1
            val sentence = text.substring(lastIndex, end).trim()
            // contains(' ') rejects lone fragments like "1." that slipped past the
            // lookbehind (e.g. when preceded by a word ending in a digit).
            if (sentence.isNotBlank() && sentence.contains(' ')) {
                sentences.add(sentence)
                lastIndex = end
                // When skipped, lastIndex does NOT advance — the fragment merges into
                // the next match, so it is never dispatched as a standalone utterance.
            }
        }
        buffer.clear()
        buffer.append(text.substring(lastIndex))
        return sentences
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        cancelNoSpeechTimeout()
        vadDetector.stop()
        sttEngine.destroy()
        ttsEngine.shutdown()
        elevenLabsSession.stop()
        releaseCaptureSession()
    }
}


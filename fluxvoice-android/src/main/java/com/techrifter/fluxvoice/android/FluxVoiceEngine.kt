package com.techrifter.fluxvoice.android

import android.os.SystemClock
import android.util.Log
import com.techrifter.fluxvoice.android.audio.FluxVoiceAudioCapture
import com.techrifter.fluxvoice.android.audio.FluxVoiceVad
import com.techrifter.fluxvoice.android.internal.ConversationManager
import com.techrifter.fluxvoice.android.internal.SentenceExtractor
import com.techrifter.fluxvoice.core.FluxVoiceConfig
import com.techrifter.fluxvoice.core.FluxVoiceController
import com.techrifter.fluxvoice.core.FluxVoiceState
import com.techrifter.fluxvoice.core.model.GenerationConfig
import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.core.state.SttState
import com.techrifter.fluxvoice.core.state.TtsState
import com.techrifter.fluxvoice.core.state.VoiceState
import com.techrifter.fluxvoice.core.streaming.StreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
        import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "FluxVoiceEngine"

private val BACKCHANNELS = listOf("Got it.", "Sure.", "Mm-hmm.", "Okay.", "Let me think.")

/**
 * Core FluxVoice pipeline engine — implements [FluxVoiceController].
 *
 * Orchestrates the full STT → LLM → TTS loop with auto barge-in (VAD), thinking
 * backchannels, no-speech timeout, and LLM retry. Lifecycle is managed by [scope].
 *
 * **Usage**
 * ```kotlin
 * // In your ViewModel:
 * val engine = FluxVoiceEngine(
 *     config = FluxVoiceConfig {
 *         sttProvider = DeepgramSttProvider(apiKey)
 *         llmProvider = OpenAiCompatibleLlmProvider(apiKey)
 *         ttsProvider = CartesiaTtsProvider(apiKey, voiceId)
 *     },
 *     scope = viewModelScope
 * )
 *
 * // Or from Compose (automatic lifecycle):
 * val controller = rememberFluxVoice { ... }  // from fluxvoice-compose
 * ```
 *
 * Always call [destroy] when the host component is torn down (ViewModel.onCleared).
 *
 * @param config  Providers and options built via [FluxVoiceConfig] DSL.
 * @param scope   Coroutine scope that owns all async work — typically `viewModelScope`.
 */
public class FluxVoiceEngine(
    private val config: FluxVoiceConfig,
    private val scope: CoroutineScope
) : FluxVoiceController {

    private companion object {
        const val MAX_LLM_RETRIES      = 2
        const val NO_SPEECH_TIMEOUT_MS  = 7_000L
        const val ECHO_DECAY_DELAY_MS   = 300L
        const val TURN_RESTART_DELAY_MS = 600L
    }

    private val _state = MutableStateFlow(FluxVoiceState())
    override val state: StateFlow<FluxVoiceState> = _state.asStateFlow()

    private val stt = config.sttProvider
    private val tts = config.ttsProvider
    private val llm = config.llmProvider

    private val conversation  = ConversationManager(config.maxContextTurns)
    private val audioCapture  = FluxVoiceAudioCapture()
    private val vad           = FluxVoiceVad(audioCapture)

    private var streamingJob:   Job? = null
    private var backchannelJob: Job? = null
    private var noSpeechJob:    Job? = null
    // Job to handle VAD re-arming delay
    private var vadArmJob:      Job? = null

    @Volatile private var isStreamingComplete  = false
    @Volatile private var isBackchannelPhase   = false

    // ── Telemetry & Stopwatch ────────────────────────────────────────────────
    @Volatile private var speechStartRealtime: Long = 0L
    @Volatile private var turnStartRealtime: Long = 0L
    @Volatile private var firstTokenRealtime: Long = 0L
    @Volatile private var firstAudioRealtime: Long = 0L
    @Volatile private var bargeInRealtime: Long? = null
    @Volatile private var promptTokensCount: Int = 0
    @Volatile private var completionTokensCount: Int = 0

    init {
        tts?.setSpeechRate(config.speechRate)
        tts?.setPitch(config.pitch)
        observeStt()
        observeTts()
    }

    // ── FluxVoiceController ───────────────────────────────────────────────────

    /**
     * Context-sensitive primary action:
     * - IDLE        → start listening (RECORD_AUDIO permission must be held)
     * - LISTENING   → stop listening early
     * - THINKING    → cancel the AI request
     * - SPEAKING    → barge in (stop TTS, restart mic)
     * - INTERRUPTING → no-op
     */
    override fun tap() {
        when (_state.value.voiceState) {
            VoiceState.IDLE -> startListening()
            VoiceState.LISTENING -> {
                stt?.stopListening()
                transitionTo(VoiceState.IDLE)
            }
            VoiceState.THINKING -> {
                backchannelJob?.cancel(); backchannelJob = null
                streamingJob?.cancel()
                tts?.stop()
                vad.stop()
                isStreamingComplete = false
                isBackchannelPhase  = false
                transitionTo(VoiceState.IDLE)
            }
            VoiceState.SPEAKING     -> triggerBargeIn(fromVad = false)
            VoiceState.INTERRUPTING -> Unit
        }
    }

    override fun clear() {
        backchannelJob?.cancel(); backchannelJob = null
        streamingJob?.cancel()
        cancelNoSpeechTimeout()
        tts?.stop()
        stt?.stopListening()
        vad.stop()
        conversation.clear()
        isStreamingComplete = false
        isBackchannelPhase  = false
        _state.update {
            it.copy(
                voiceState        = VoiceState.IDLE,
                partialTranscript = "",
                transcript        = "",
                aiResponse        = "",
                errorMessage      = null
            )
        }
    }

    override fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    override fun destroy() {
        clear()
        stt?.destroy()
        tts?.shutdown()
        audioCapture.shutdown()
    }

    // ── STT observation ───────────────────────────────────────────────────────

    private fun observeStt() {
        val provider = stt ?: return
        scope.launch {
            provider.state.collect { sttState ->
                when (sttState) {
                    SttState.Listening -> {
                        transitionTo(VoiceState.LISTENING)
                        speechStartRealtime = SystemClock.elapsedRealtime()
                        // Hard guard: kill TTS if somehow still playing when mic opens.
                        interruptTtsIfSpeaking()
                        armNoSpeechTimeout()
                    }
                    is SttState.PartialResult -> {
                        if (speechStartRealtime == 0L) {
                            speechStartRealtime = SystemClock.elapsedRealtime()
                        }
                        _state.update { it.copy(partialTranscript = sttState.text) }
                        armNoSpeechTimeout()
                    }
                    is SttState.FinalResult -> {
                        cancelNoSpeechTimeout()
                        val text = sttState.text.trim()
                        if (text.isBlank()) {
                            Log.d(TAG, "STT FinalResult was blank, returning to IDLE")
                            transitionTo(VoiceState.IDLE)
                            return@collect
                        }
                        Log.d(TAG, "STT FinalResult: '$text'")
                        turnStartRealtime = SystemClock.elapsedRealtime()
                        firstTokenRealtime = 0L
                        firstAudioRealtime = 0L
                        bargeInRealtime = null
                        promptTokensCount = 0
                        completionTokensCount = 0

                        stt.stopListening()
                        // Pre-warm the STT socket while AI thinks + TTS plays so the
                        // next startListening() connects with near-zero latency.
                        stt.preConnect()
                        _state.update {
                            it.copy(
                                voiceState        = VoiceState.THINKING,
                                transcript        = text,
                                partialTranscript = "",
                                aiResponse        = ""
                            )
                        }
                        config.onTranscript?.invoke(text)
                        startAiResponse(text)
                    }
                    is SttState.Error -> {
                        cancelNoSpeechTimeout()
                        config.onError?.invoke(RuntimeException(sttState.message))
                        _state.update {
                            it.copy(
                                voiceState   = VoiceState.IDLE,
                                errorMessage = userFacingError(sttState.message)
                            )
                        }
                    }
                    SttState.Idle -> {
                        cancelNoSpeechTimeout()
                        if (_state.value.voiceState == VoiceState.LISTENING) {
                            transitionTo(VoiceState.IDLE)
                        }
                    }
                }
            }
        }
    }

    // ── TTS observation + auto barge-in ───────────────────────────────────────

    private fun observeTts() {
        val provider = tts ?: return
        scope.launch {
            provider.state.collect { ttsState ->
                when (ttsState) {
                    TtsState.Speaking -> {
                        transitionTo(VoiceState.SPEAKING)
                        if (!isBackchannelPhase && firstAudioRealtime == 0L && turnStartRealtime > 0L) {
                            firstAudioRealtime = SystemClock.elapsedRealtime()
                        }
                        // Hard mutual-exclusion: mic must be OFF while TTS plays.
                        stt?.stopListening()
                        // Arm VAD so the user can interrupt without tapping.
                        if (config.vadEnabled && !vad.isRunning) {
                            vadArmJob?.cancel()
                            vadArmJob = scope.launch {
                                // AEC grace period: allow AEC to converge and trailing noises to dissipate
                                delay(600)
                                if (_state.value.voiceState == VoiceState.SPEAKING) {
                                    vad.start(threshold = config.vadSensitivity) {
                                        Log.d(TAG, "VAD: auto barge-in triggered")
                                        scope.launch { triggerBargeIn(fromVad = true) }
                                    }
                                }
                            }
                        }
                    }
                    TtsState.Idle -> {
                        vadArmJob?.cancel()
                        vad.stop()
                        if (_state.value.voiceState == VoiceState.SPEAKING) {
                            val jobDone = streamingJob?.isCompleted == true
                            if (isStreamingComplete || jobDone) {
                                Log.d(TAG, "TTS idle → finishing turn " +
                                    "(complete=$isStreamingComplete, jobDone=$jobDone)")
                                finishTurnAndRestart()
                            }
                        }
                    }
                    is TtsState.Error -> {
                        vadArmJob?.cancel()
                        vad.stop()
                        config.onError?.invoke(RuntimeException(ttsState.message))
                        val jobDone = streamingJob?.isCompleted == true
                        if (isStreamingComplete || jobDone) {
                            Log.d(TAG, "TTS error with streaming complete → finishing turn")
                            finishTurnAndRestart()
                        } else if (_state.value.voiceState == VoiceState.SPEAKING) {
                            _state.update { it.copy(voiceState = VoiceState.IDLE, transcript = "", aiResponse = "") }
                        }
                    }
                }
            }
        }
    }

    // ── Barge-in ──────────────────────────────────────────────────────────────

    /**
     * Interrupt TTS and restart listening.
     *
     * When [fromVad] is true and [isBackchannelPhase] is active, the trigger is a
     * false-positive caused by ambient noise during the silent gap between the filler
     * phrase and the first Cartesia/TTS audio chunk. Suppress it and re-arm the VAD.
     */
    private fun triggerBargeIn(fromVad: Boolean) {
        if (fromVad && isBackchannelPhase) {
            Log.d(TAG, "VAD: false-positive suppressed during backchannel phase")
            if (config.vadEnabled) {
                vadArmJob?.cancel()
                vadArmJob = scope.launch {
                    delay(600)
                    if (_state.value.voiceState == VoiceState.SPEAKING) {
                        vad.start(threshold = config.vadSensitivity) {
                            scope.launch { triggerBargeIn(fromVad = true) }
                        }
                    }
                }
            }
            return
        }
        backchannelJob?.cancel(); backchannelJob = null
        streamingJob?.cancel()
        vadArmJob?.cancel()
        tts?.stop()
        vad.stop()
        bargeInRealtime = SystemClock.elapsedRealtime()
        isStreamingComplete = false
        isBackchannelPhase  = false
        transitionTo(VoiceState.INTERRUPTING)
        stt?.preConnect()
        scope.launch {
            kotlinx.coroutines.delay(ECHO_DECAY_DELAY_MS)
            startListening()
        }
    }

    /**
     * Stop TTS immediately because STT detected the user is already speaking.
     * Unlike [triggerBargeIn] we don't restart listening — the STT mic is already open.
     */
    private fun interruptTtsIfSpeaking() {
        if (tts?.state?.value != TtsState.Speaking) return
        Log.d(TAG, "interruptTtsIfSpeaking: stopping TTS")
        backchannelJob?.cancel(); backchannelJob = null
        streamingJob?.cancel()
        tts.stop()
        vad.stop()
        isStreamingComplete = false
        isBackchannelPhase  = false
    }

    // ── No-speech timeout ─────────────────────────────────────────────────────

    private fun armNoSpeechTimeout() {
        noSpeechJob?.cancel()
        noSpeechJob = scope.launch {
            kotlinx.coroutines.delay(NO_SPEECH_TIMEOUT_MS)
            if (_state.value.voiceState == VoiceState.LISTENING) {
                Log.d(TAG, "No-speech timeout — closing mic")
                stt?.stopListening()
                _state.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }

    private fun cancelNoSpeechTimeout() {
        noSpeechJob?.cancel()
        noSpeechJob = null
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    private fun startListening() {
        if (tts?.state?.value == TtsState.Speaking) {
            Log.w(TAG, "startListening: skipped — TTS still speaking")
            return
        }
        _state.update { it.copy(partialTranscript = "") }
        transitionTo(VoiceState.LISTENING)
        stt?.startListening()
    }

    private fun startAiResponse(userInput: String, retryAttempt: Int = 1) {
        val llmProvider = llm ?: run {
            Log.w(TAG, "No LlmProvider configured — skipping AI response")
            finishTurnAndRestart()
            return
        }
        Log.d(TAG, "startAiResponse (attempt $retryAttempt): '$userInput'")
        isStreamingComplete = false
        isBackchannelPhase  = false
        backchannelJob?.cancel()

        // ── Deferred backchannel ──────────────────────────────────────────────
        // Only speak a filler if the first LLM token hasn't arrived within
        // backchannelDelayMs. Fast providers (Groq < 500 ms) cancel this job in
        // StreamEvent.Token, so they never trigger an unnecessary filler.
        if (config.backchannelEnabled && tts != null) {
            backchannelJob = scope.launch {
                kotlinx.coroutines.delay(config.backchannelDelayMs)
                if (_state.value.voiceState == VoiceState.THINKING) {
                    isBackchannelPhase = true
                    tts.speak(BACKCHANNELS.random(), UUID.randomUUID().toString())
                }
            }
        }

        conversation.addUserMessage(userInput)

        // Adaptive length hint: short queries → short answers, matching conversational pace.
        val wordCount = userInput.trim().split("\\s+".toRegex()).size
        val lengthHint = when {
            wordCount <= 4  -> "\nRespond in 1 sentence."
            wordCount <= 10 -> "\nRespond in 1–2 sentences."
            else            -> ""
        }
        val voiceDirective = "\nSpeak directly in natural conversational prose suitable for real-time voice output. Never use pleasantry preamble (like 'Sure!' or 'Certainly!'), markdown symbols, bullet points, tables, code blocks, or emojis. Use punctuation to guide natural spoken pacing."
        val genConfig = GenerationConfig(
            modelId         = config.modelId,
            systemPrompt    = config.systemPrompt.trim() + " " + voiceDirective + lengthHint,
            temperature     = config.temperature,
            maxOutputTokens = config.maxOutputTokens
        )
        val messages  = conversation.getMessages()

        streamingJob = scope.launch {
            val tokenBuffer  = StringBuilder()
            val fullResponse = StringBuilder()

            try {
                llmProvider.streamChat(messages, genConfig).collect { event ->
                    when (event) {
                        StreamEvent.Start -> {
                            _state.update { it.copy(voiceState = VoiceState.THINKING) }
                        }

                        is StreamEvent.Usage -> {
                            promptTokensCount = event.promptTokens
                            completionTokensCount = event.completionTokens
                        }

                        is StreamEvent.Token -> {
                            // First token cancels the pending backchannel and marks TTFT.
                            if (firstTokenRealtime == 0L && turnStartRealtime > 0L) {
                                firstTokenRealtime = SystemClock.elapsedRealtime()
                            }
                            backchannelJob?.cancel(); backchannelJob = null

                            tokenBuffer.append(event.text)
                            fullResponse.append(event.text)
                            _state.update { it.copy(aiResponse = fullResponse.toString()) }

                            if (tts != null) {
                                val wasBackchannel = isBackchannelPhase
                                SentenceExtractor.extract(tokenBuffer).forEach { sentence ->
                                    isBackchannelPhase = false
                                    tts.speak(sentence, UUID.randomUUID().toString())
                                }
                                // If backchannel ran straight into LLM audio with no Idle gap,
                                // observeTts won't fire a new Speaking event — re-arm VAD here.
                                if (wasBackchannel && !isBackchannelPhase &&
                                    config.vadEnabled && !vad.isRunning &&
                                    tts.state.value == TtsState.Speaking
                                ) {
                                    Log.d(TAG, "VAD: re-arming after backchannel ended")
                                    vadArmJob?.cancel()
                                    vadArmJob = scope.launch {
                                        delay(600)
                                        if (_state.value.voiceState == VoiceState.SPEAKING) {
                                            vad.start(threshold = config.vadSensitivity) {
                                                scope.launch { triggerBargeIn(fromVad = true) }
                                            }
                                        }
                                    }
                                }
                            }

                            if (_state.value.voiceState == VoiceState.THINKING) {
                                transitionTo(VoiceState.SPEAKING)
                            }
                        }

                        StreamEvent.Done -> {
                            val remaining = tokenBuffer.toString().trim()
                            if (remaining.isNotEmpty() && tts != null) {
                                isBackchannelPhase = false
                                tts.speak(remaining, UUID.randomUUID().toString())
                            } else {
                                isBackchannelPhase = false
                            }
                            tokenBuffer.clear()
                            isStreamingComplete = true

                            val fullText = fullResponse.toString()
                            conversation.addAssistantMessage(fullText)
                            config.onResponse?.invoke(fullText)

                            // When TTS is disabled or TTS already failed with error, finish now.
                            if (tts == null || tts.state.value is TtsState.Error) finishTurnAndRestart()
                        }

                        is StreamEvent.Error -> {
                            config.onError?.invoke(event.cause)
                            if (retryAttempt < MAX_LLM_RETRIES) {
                                Log.w(TAG, "LLM error, retrying (${retryAttempt}/$MAX_LLM_RETRIES)")
                                isStreamingComplete = false
                                tokenBuffer.clear()
                                fullResponse.clear()
                                kotlinx.coroutines.delay(600L * retryAttempt)
                                startAiResponse(userInput, retryAttempt + 1)
                            } else {
                                isStreamingComplete = false
                                _state.update {
                                    it.copy(
                                        voiceState   = VoiceState.IDLE,
                                        transcript   = "",
                                        aiResponse   = "",
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
                config.onError?.invoke(e)
                if (retryAttempt < MAX_LLM_RETRIES) {
                    kotlinx.coroutines.delay(600L * retryAttempt)
                    startAiResponse(userInput, retryAttempt + 1)
                } else {
                    isStreamingComplete = false
                    _state.update {
                        it.copy(
                            voiceState   = VoiceState.IDLE,
                            transcript   = "",
                            aiResponse   = "",
                            errorMessage = userFacingError(e.message ?: "")
                        )
                    }
                }
            }
        }
    }

    private fun finishTurnAndRestart() {
        val now = SystemClock.elapsedRealtime()
        val sttLatency = if (speechStartRealtime > 0L && turnStartRealtime >= speechStartRealtime) turnStartRealtime - speechStartRealtime else 0L
        val ttft = if (firstTokenRealtime > turnStartRealtime) firstTokenRealtime - turnStartRealtime else 0L
        val ttfa = if (firstAudioRealtime > turnStartRealtime) firstAudioRealtime - turnStartRealtime else 0L
        val total = if (now > turnStartRealtime && turnStartRealtime > 0L) now - turnStartRealtime else 0L
        val bargeInDelay = bargeInRealtime?.let { if (it > turnStartRealtime) it - turnStartRealtime else null }

        val metrics = TurnMetrics(
            sttLatencyMs        = sttLatency,
            timeToFirstTokenMs  = ttft,
            timeToFirstAudioMs  = ttfa,
            totalTurnDurationMs = total,
            bargeInLatencyMs    = bargeInDelay,
            bargeInOccurred     = bargeInDelay != null,
            inputTokens         = promptTokensCount,
            outputTokens        = completionTokensCount
        )

        isStreamingComplete = false
        transitionTo(VoiceState.IDLE)
        _state.update { it.copy(metrics = metrics) }
        config.onTurnMetrics?.invoke(metrics)

        stt?.preConnect()
        scope.launch {
            kotlinx.coroutines.delay(TURN_RESTART_DELAY_MS)
            if (_state.value.voiceState == VoiceState.IDLE) {
                startListening()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun transitionTo(next: VoiceState) {
        val prev = _state.value.voiceState
        if (prev == next) return
        Log.d(TAG, "State: $prev → $next")
        config.onStateChange?.invoke(prev, next)
        _state.update { it.copy(voiceState = next) }
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains("not configured", ignoreCase = true) ||
        raw.contains("API key", ignoreCase = true)          -> raw
        raw.contains("permission", ignoreCase = true)       ->
            "Microphone access is needed. Please grant the permission in your device settings."
        raw.contains("401", ignoreCase = true)              ->
            "API key is invalid or expired."
        raw.contains("429", ignoreCase = true)              ->
            "Service is busy. Please wait a moment and try again."
        raw.contains("timeout", ignoreCase = true) ||
        raw.contains("timed out", ignoreCase = true)        ->
            "The request timed out. Check your internet connection."
        raw.contains("broken pipe", ignoreCase = true) ||
        raw.contains("Socket closed", ignoreCase = true) ||
        raw.contains("EOF", ignoreCase = true)              ->
            "Connection interrupted. Tap to try again."
        raw.isNotBlank() && !raw.contains("Exception") && raw.length <= 150 -> raw
        else -> "Something went wrong. Tap to try again."
    }
}

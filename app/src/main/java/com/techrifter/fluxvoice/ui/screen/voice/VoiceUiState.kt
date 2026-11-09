package com.techrifter.fluxvoice.ui.screen.voice

import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.model.VoiceMode
import com.techrifter.fluxvoice.domain.model.VoiceScenario
import com.techrifter.fluxvoice.domain.pipeline.PipelineReadiness

/**
 * Represents the current phase of the voice conversation pipeline.
 *
 * State machine:
 *   IDLE ──(tap mic)──► LISTENING ──(speech detected)──► THINKING ──(first token)──► SPEAKING
 *     ▲                     │                                                              │
 *     └────────(done)───────┴──────────────────────────────────────────────────────────────┘
 *
 *   SPEAKING ──(VAD / tap)──► INTERRUPTING ──(300 ms echo decay)──► LISTENING
 *
 * The dedicated [INTERRUPTING] state eliminates the race window that previously
 * existed between stopping TTS and opening the microphone.  UI can use it to
 * show a distinct visual cue ("Stopping…") instead of briefly flashing IDLE or
 * LISTENING while audio stops and the WebSocket is being set up.
 */
enum class VoiceState {
    /** Ready — tap the mic to start a conversation. */
    IDLE,

    /** Microphone is active — showing live partial transcript. */
    LISTENING,

    /** STT finished → waiting for the first LLM token. */
    THINKING,

    /** LLM is streaming tokens → TTS is playing sentences in parallel. */
    SPEAKING,

    /**
     * User interrupted the AI (via VAD barge-in or mic tap).
     * TTS has been stopped; waiting ~300 ms for speaker echo to decay
     * before the microphone opens.  Transitions to [LISTENING] automatically.
     */
    INTERRUPTING
}

/**
 * Immutable UI state for [VoiceScreen].
 */
data class VoiceUiState(
    val voiceState: VoiceState = VoiceState.IDLE,

    /** Which pipeline is active — drives UI label text and FAB behaviour. */
    val voiceMode: VoiceMode = VoiceMode.STANDARD,

    /** Current experience mode — drives the mode badge in the header. */
    val appMode: AppMode = AppMode.FAST,

    /** Live partial STT result shown while the user is still speaking. */
    val partialTranscript: String = "",

    /** Confirmed final transcript (what the user said). */
    val userTranscript: String = "",

    /** AI response text, accumulated from the token stream. */
    val aiResponse: String = "",

    /** Full conversation history turns (user and assistant messages). */
    val messages: List<Message> = emptyList(),

    /** Non-null while an error snackbar should be shown. */
    val errorMessage: String? = null,

    /** Active pipeline configuration selected in playground */
    val activePipeline: PipelineConfig? = null,

    /** Readiness status for active pipeline (credentials check) */
    val pipelineReadiness: PipelineReadiness = PipelineReadiness.Ready,

    /** Benchmark metrics for the latest completed voice turn */
    val lastMetrics: TurnMetrics? = null,

    /** Whether the benchmark HUD card is expanded */
    val isBenchmarkHudExpanded: Boolean = true,

    /** Whether the app currently holds the RECORD_AUDIO permission. */
    val hasAudioPermission: Boolean = false,

    /** Dynamically generated voice scenarios (time-adaptive or real-time LLM curated) */
    val scenarios: List<VoiceScenario> = emptyList(),

    /** Whether an active AI shuffle / scenario refresh is in-flight */
    val isShufflingScenarios: Boolean = false,

    /** Active operator username */
    val anonymousUsername: String = ""
)


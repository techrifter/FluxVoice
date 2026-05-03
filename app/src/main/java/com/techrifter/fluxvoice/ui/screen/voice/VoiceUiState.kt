package com.techrifter.fluxvoice.ui.screen.voice

import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.VoiceMode

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

    /** Non-null while an error snackbar should be shown. */
    val errorMessage: String? = null,

    /** Whether the app currently holds the RECORD_AUDIO permission. */
    val hasAudioPermission: Boolean = false
)


package com.techrifter.fluxvoice.core.state

/**
 * Represents every phase of the FluxVoice conversation pipeline.
 *
 * State machine:
 *   IDLE ──(tap)──► LISTENING ──(speech done)──► THINKING ──(first token)──► SPEAKING
 *     ▲                  │                                                         │
 *     └──────(done)──────┴─────────────────────────────────────────────────────────┘
 *
 *   SPEAKING ──(barge-in / tap)──► INTERRUPTING ──(echo decay)──► LISTENING
 */
public enum class VoiceState {
    /** Idle — waiting for user to initiate a conversation. */
    IDLE,

    /** Microphone is open; live partial transcript being received. */
    LISTENING,

    /** STT complete; waiting for the first LLM token. */
    THINKING,

    /** LLM is streaming tokens; TTS is playing sentences in parallel. */
    SPEAKING,

    /**
     * User interrupted; TTS stopped. Waiting for speaker echo to decay
     * before reopening the microphone.
     */
    INTERRUPTING
}

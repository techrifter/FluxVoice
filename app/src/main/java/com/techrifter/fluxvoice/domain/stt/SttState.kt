package com.techrifter.fluxvoice.domain.stt

/**
 * State emitted by [SttEngine] as speech recognition progresses.
 *
 * Lifecycle (happy path):
 *   Idle → Listening → PartialResult* → FinalResult → Idle
 */
sealed class SttState {
    /** Waiting for [SttEngine.startListening] to be called. */
    data object Idle : SttState()

    /** Microphone is active; the engine is listening for speech. */
    data object Listening : SttState()

    /**
     * Mid-utterance partial transcript (updates frequently while the user speaks).
     * Use to show live transcription preview — do NOT trigger the AI pipeline on this.
     */
    data class PartialResult(val text: String) : SttState()

    /**
     * Final transcription after the user has finished speaking.
     * Trigger the AI pipeline (STT → LLM → TTS) when this is received.
     */
    data class FinalResult(val text: String) : SttState()

    /** Recognition error with a human-readable description. */
    data class Error(val message: String) : SttState()
}


package com.techrifter.fluxvoice.domain.tts

/**
 * State emitted by [TtsEngine] during speech synthesis.
 * Transitions: Idle ↔ Speaking, any state → Error
 */
sealed class TtsState {
    /** No audio playing; engine is ready for the next [TtsEngine.speak] call. */
    data object Idle : TtsState()

    /** Audio is currently being synthesised and played. */
    data object Speaking : TtsState()

    /** Engine encountered an unrecoverable error. */
    data class Error(val message: String) : TtsState()
}


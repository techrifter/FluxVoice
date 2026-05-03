package com.techrifter.fluxvoice.core.state

/**
 * State emitted by a [com.techrifter.fluxvoice.core.tts.TtsProvider] during speech synthesis.
 */
public sealed class TtsState {
    public data object Idle    : TtsState()
    public data object Speaking : TtsState()
    public data class Error(val message: String) : TtsState()
}

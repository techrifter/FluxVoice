package com.techrifter.fluxvoice.core.state

/**
 * State emitted by an [com.techrifter.fluxvoice.core.stt.SttProvider] as recognition progresses.
 *
 * Lifecycle (happy path):
 *   Idle → Listening → PartialResult* → FinalResult → Idle
 */
public sealed class SttState {
    public data object Idle           : SttState()
    public data object Listening      : SttState()
    public data class PartialResult(val text: String) : SttState()
    public data class FinalResult(val text: String)   : SttState()
    public data class Error(val message: String)      : SttState()
}

package com.techrifter.fluxvoice.core.streaming

/**
 * Events emitted by a streaming LLM response.
 *
 * Flow lifecycle:
 *   Start → Token* → Done   (happy path)
 *   Start → Token* → Error  (network / API failure)
 *
 * Cancelling the collecting Job closes the connection; no special event is emitted.
 */
public sealed class StreamEvent {
    /** Stream connection established; tokens are incoming. */
    public data object Start : StreamEvent()

    /** A single text token/delta from the model. */
    public data class Token(val text: String) : StreamEvent()

    /** Stream completed successfully. */
    public data object Done : StreamEvent()

    /** Transient or terminal error — includes the underlying cause. */
    public data class Error(val cause: Throwable) : StreamEvent()
}

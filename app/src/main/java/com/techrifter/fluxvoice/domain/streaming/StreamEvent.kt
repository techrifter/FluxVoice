package com.techrifter.fluxvoice.domain.streaming

/**
 * Sealed hierarchy representing every possible event emitted by a streaming AI response.
 *
 * Flow lifecycle: Start → Token* → Done  (happy path)
 *                 Start → Token* → Error (network/API failure)
 *
 * Cancellation: the collector's Job is simply cancelled; no special event is emitted.
 * The repository's finally{} block handles any cleanup (saving partial response).
 */
sealed class StreamEvent {
    /** First event — stream connection established, tokens incoming */
    data object Start : StreamEvent()

    /** A single text chunk / token delta from the model */
    data class Token(val text: String) : StreamEvent()

    /** Stream completed cleanly */
    data object Done : StreamEvent()

    /** Token usage telemetry for cost and throughput calculation */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent()

    /** Transient or terminal error — includes the underlying cause */
    data class Error(val cause: Throwable) : StreamEvent()
}


package com.techrifter.fluxvoice.core.llm

import com.techrifter.fluxvoice.core.model.GenerationConfig
import com.techrifter.fluxvoice.core.model.Message
import com.techrifter.fluxvoice.core.streaming.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for a streaming LLM provider.
 *
 * Every provider — Groq, Gemini, OpenAI, Anthropic, local Ollama, or your own
 * backend — implements this single interface. Swap providers with one line change.
 *
 * The returned [Flow] must emit: [StreamEvent.Start] → [StreamEvent.Token]* →
 * [StreamEvent.Done] or [StreamEvent.Error].
 *
 * Cancellation: cancelling the collecting Job must close the underlying HTTP
 * connection. Use `flow { }` with `try/finally` or Ktor's response body API.
 *
 * Example:
 * ```kotlin
 * class MyBackendProvider(private val baseUrl: String) : LlmProvider {
 *     override fun streamChat(
 *         messages: List<Message>,
 *         config: GenerationConfig
 *     ): Flow<StreamEvent> = flow {
 *         emit(StreamEvent.Start)
 *         // ... call your API and emit tokens
 *         emit(StreamEvent.Done)
 *     }
 * }
 * ```
 */
public interface LlmProvider {

    /**
     * Stream a response for [messages] given [config].
     * This is a cold flow — execution starts only when collected.
     */
    public fun streamChat(
        messages: List<Message>,
        config: GenerationConfig
    ): Flow<StreamEvent>
}

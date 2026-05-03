package com.techrifter.fluxvoice.domain.streaming

import com.techrifter.fluxvoice.domain.model.AuthConfig
import com.techrifter.fluxvoice.domain.model.GenerationConfig
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow

/**
 * Describes what a provider can and cannot do.
 *
 * Used to guard feature usage at runtime (e.g. don't send image attachments to
 * a text-only model) and to drive dynamic UI (e.g. hide voice-mode selector for
 * providers that don't stream).
 */
data class ProviderCapabilities(
    /** Provider returns tokens incrementally as a stream (vs. one big response). */
    val supportsStreaming: Boolean = true,
    /** Provider can call external tools / functions defined by the caller. */
    val supportsFunctionCalling: Boolean = false,
    /** Practical max context length in tokens (0 = unknown / unlimited). */
    val maxContextTokens: Int = 0
)

/**
 * Contract every AI provider must implement.
 *
 * Providers are stateless singletons — auth credentials are passed per-call via [AuthConfig].
 * To add a new provider:
 *   1. Implement this interface
 *   2. Register in ProviderModule with @IntoMap/@ProviderTypeKey
 *   3. Add the corresponding enum in [ProviderType]
 */
interface AiStreamingProvider {

    /** Identifies this provider for multi-binding lookup. */
    val providerType: ProviderType

    /**
     * What this provider/model combination supports.
     * Override in each provider implementation; defaults are conservative.
     */
    val capabilities: ProviderCapabilities get() = ProviderCapabilities()

    /**
     * Streams an AI response as a cold [Flow] of [StreamEvent].
     *
     * The flow emits [StreamEvent.Start], then zero or more [StreamEvent.Token] deltas,
     * and finally [StreamEvent.Done] or [StreamEvent.Error].
     *
     * Cancellation: cancelling the collecting Job closes the HTTP connection and
     * terminates the flow — no additional cleanup needed from the caller.
     */
    fun streamChat(
        messages: List<Message>,
        config: GenerationConfig,
        authConfig: AuthConfig
    ): Flow<StreamEvent>
}


package com.techrifter.fluxvoice.domain.repository

import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for an ongoing voice conversation.
 *
 * Maintains an **in-memory** context window bounded by [AppSettings.maxContextTurns].
 * Delegates token generation to the configured [AiStreamingProvider].
 *
 * Voice conversations are ephemeral by design — no database persistence.
 * If you need persistence, provide an alternative @Binds implementation.
 *
 * **Adding a new backend:** implement [AiStreamingProvider], register it in
 * [ProviderModule] with @IntoMap/@ProviderTypeKey — this repository picks it up
 * automatically based on the active [AppSettings.providerType].
 */
interface ConversationRepository {

    /** Current conversation history (user + assistant turns), newest last. */
    val history: List<Message>

    /** Observable flow of conversation history (user + assistant turns). */
    val historyFlow: StateFlow<List<Message>>

    /**
     * Stream an AI response for [userInput].
     *
     * @param userInput       The user's transcribed speech.
     * @param systemPromptOverride When non-null, replaces the saved system prompt for this
     *        turn only — used for adaptive length hints without modifying stored settings.
     *
     * Flow lifecycle: Start → Token* → Done  (happy path)
     *                 Start → Token* → Error (network/API failure)
     */
    fun streamResponse(userInput: String, systemPromptOverride: String? = null): Flow<StreamEvent>

    /** Append a single message directly to history (e.g. from ElevenLabs or external turns). */
    fun addMessage(message: Message)

    /** Wipe conversation history to start a fresh session. */
    fun clearHistory()
}


package com.techrifter.fluxvoice.data.conversation

import android.util.Log
import com.techrifter.fluxvoice.domain.model.AuthConfig
import com.techrifter.fluxvoice.domain.model.GenerationConfig
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.MessageRole
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.repository.ConversationRepository
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import com.techrifter.fluxvoice.domain.streaming.AiStreamingProvider
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FluxVoice"

/**
 * In-memory [ConversationRepository] implementation.
 *
 * Maintains a bounded context window of recent exchanges and delegates
 * token streaming to the currently configured [AiStreamingProvider].
 * No database persistence — history lives in RAM and is cleared on process restart
 * or when [clearHistory] is called explicitly.
 *
 * **Adding a new AI backend:**
 *  1. Implement [AiStreamingProvider] in `data/remote/provider/`
 *  2. Add its [ProviderType] enum entry
 *  3. Register it in [ProviderModule] with @IntoMap/@ProviderTypeKey
 *  This repository picks it up automatically at runtime.
 */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    /** Hilt multi-binding: one entry per registered AI provider. */
    private val providers: Map<ProviderType, @JvmSuppressWildcards AiStreamingProvider>
) : ConversationRepository {

    private val _history = ArrayDeque<Message>()
    override val history: List<Message> get() = _history.toList()

    override fun streamResponse(userInput: String, systemPromptOverride: String?): Flow<StreamEvent> = channelFlow {
        // 1. Load current settings (provider selection, API keys, model config)
        val settings = settingsRepository.getSettings().first()
        Log.d(TAG, "streamResponse: provider=${settings.providerType} model=${settings.modelId}")

        val provider = providers[settings.providerType]
            ?: providers[ProviderType.GEMINI]
            ?: error("No AI provider registered — check ProviderModule bindings")

        // ── Pre-flight: validate credentials before hitting the network ──────
        val missingKeyError: String? = when (settings.providerType) {
            ProviderType.GEMINI        -> if (settings.geminiApiKey.isBlank())
                "Gemini API key is not set. Go to Settings → AI Provider and add your key." else null
            ProviderType.GROQ          -> if (settings.groqApiKey.isBlank())
                "Groq API key is not set. Go to Settings → Credentials and add your key." else null
            ProviderType.OPENAI_COMPAT -> if (settings.openAiApiKey.isBlank())
                "OpenAI API key is not set. Go to Settings → AI Provider and add your key." else null
            ProviderType.LOCAL         -> null // no key needed for local
        }
        if (missingKeyError != null) {
            Log.e(TAG, "streamResponse: missing key — $missingKeyError")
            send(StreamEvent.Error(Exception(missingKeyError)))
            return@channelFlow
        }

        val authConfig = AuthConfig(
            apiKey = when (settings.providerType) {
                ProviderType.GEMINI        -> settings.geminiApiKey
                ProviderType.GROQ          -> settings.groqApiKey
                ProviderType.OPENAI_COMPAT -> settings.openAiApiKey
                ProviderType.LOCAL         -> ""
            },
            baseUrl = settings.localLlmUrl
        )
        val genConfig = GenerationConfig(
            modelId         = settings.modelId,
            temperature     = settings.temperature,
            maxOutputTokens = 512,
            // Use per-turn override (adaptive length hint) if provided, else stored prompt
            systemPrompt    = (systemPromptOverride ?: settings.systemPrompt).takeIf { it.isNotBlank() }
        )

        // 2. Append user turn and trim context window
        _history += Message(role = MessageRole.USER, content = userInput)
        trimHistory(settings.maxContextTurns)

        val buffer = StringBuilder()

        try {
            provider.streamChat(_history.toList(), genConfig, authConfig).collect { event ->
                send(event)
                if (event is StreamEvent.Token) buffer.append(event.text)
            }
        } finally {
            // Save assistant response even if the stream was cancelled mid-way
            if (buffer.isNotEmpty()) {
                _history += Message(role = MessageRole.ASSISTANT, content = buffer.toString())
                trimHistory(settings.maxContextTurns)
            }
        }
    }

    override fun clearHistory() {
        _history.clear()
    }

    /**
     * Keep at most [maxTurns] complete user+assistant exchange pairs.
     * Removes from the front of the deque (oldest messages first).
     */
    private fun trimHistory(maxTurns: Int) {
        val maxMessages = maxTurns * 2
        while (_history.size > maxMessages) {
            _history.removeFirst()
        }
    }
}

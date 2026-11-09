package com.techrifter.fluxvoice.domain.pipeline

import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.TtsProvider
import com.techrifter.fluxvoice.domain.repository.PipelineRepository
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class PipelineReadiness {
    data object Ready : PipelineReadiness()
    data class MissingCredentials(val providerNames: List<String>) : PipelineReadiness() {
        fun formatErrorMessage(destination: String = "Settings"): String {
            val keyWord = if (providerNames.size > 1) "keys" else "key"
            val formattedList = when (providerNames.size) {
                0 -> ""
                1 -> providerNames[0]
                2 -> "${providerNames[0]} and ${providerNames[1]}"
                else -> "${providerNames.dropLast(1).joinToString(", ")}, and ${providerNames.last()}"
            }
            return "Add $formattedList $keyWord in $destination to run this pipeline."
        }
    }
}

@Singleton
class PipelineSessionManager @Inject constructor(
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
    private val keyVault: KeyVault
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cachedSettings: AppSettings = AppSettings()

    init {
        scope.launch {
            settingsRepository.getSettings().collect { s ->
                cachedSettings = s
                // Bi-directional key sync: populate KeyVault from Settings if not present
                if (s.groqApiKey.isNotBlank() && keyVault.getGroqKey().isBlank()) keyVault.setGroqKey(s.groqApiKey)
                if (s.geminiApiKey.isNotBlank() && keyVault.getGeminiKey().isBlank()) keyVault.setGeminiKey(s.geminiApiKey)
                if (s.openAiApiKey.isNotBlank() && keyVault.getOpenAiKey().isBlank()) keyVault.setOpenAiKey(s.openAiApiKey)
                if (s.deepgramApiKey.isNotBlank() && keyVault.getDeepgramKey().isBlank()) keyVault.setDeepgramKey(s.deepgramApiKey)
                if (s.cartesiaApiKey.isNotBlank() && keyVault.getCartesiaKey().isBlank()) keyVault.setCartesiaKey(s.cartesiaApiKey)
                if (s.elevenLabsApiKey.isNotBlank() && keyVault.getElevenLabsKey().isBlank()) keyVault.setElevenLabsKey(s.elevenLabsApiKey)
                if (s.openRouterApiKey.isNotBlank() && keyVault.getOpenRouterKey().isBlank()) keyVault.setOpenRouterKey(s.openRouterApiKey)
            }
        }
    }

    /** Current active pipeline as a Flow */
    fun observeActivePipeline(): Flow<PipelineConfig?> =
        pipelineRepository.getActivePipelineId().map { activeId ->
            pipelineRepository.getPipelineById(activeId)
        }

    suspend fun getActivePipeline(): PipelineConfig? {
        val activeId = pipelineRepository.getActivePipelineId().first()
        return pipelineRepository.getPipelineById(activeId)
    }

    /**
     * Checks if all necessary API keys for the given pipeline are stored in KeyVault or Settings.
     */
    fun checkReadiness(pipeline: PipelineConfig): PipelineReadiness {
        val missing = mutableListOf<String>()

        fun hasKey(provider: String): Boolean {
            return when (provider.lowercase()) {
                "deepgram" -> keyVault.getDeepgramKey().isNotBlank() || cachedSettings.deepgramApiKey.isNotBlank()
                "groq" -> keyVault.getGroqKey().isNotBlank() || cachedSettings.groqApiKey.isNotBlank()
                "openai" -> keyVault.getOpenAiKey().isNotBlank() || cachedSettings.openAiApiKey.isNotBlank()
                "gemini" -> keyVault.getGeminiKey().isNotBlank() || cachedSettings.geminiApiKey.isNotBlank()
                "cartesia" -> keyVault.getCartesiaKey().isNotBlank() || cachedSettings.cartesiaApiKey.isNotBlank()
                "elevenlabs" -> keyVault.getElevenLabsKey().isNotBlank() || cachedSettings.elevenLabsApiKey.isNotBlank()
                "openrouter" -> keyVault.getOpenRouterKey().isNotBlank() || cachedSettings.openRouterApiKey.isNotBlank()
                else -> keyVault.hasKey(provider)
            }
        }

        // Check STT
        when (pipeline.sttProviderId.lowercase()) {
            "deepgram" -> if (!hasKey("deepgram")) missing.add("Deepgram")
            "groq", "groq_whisper" -> if (!hasKey("groq")) missing.add("Groq")
            "whisper", "openai" -> if (!hasKey("openai")) missing.add("OpenAI")
            "custom" -> {
                if (pipeline.customEndpointUrl.isBlank() && keyVault.getKey("custom_stt_url").isBlank() && cachedSettings.localLlmUrl.isBlank()) {
                    missing.add("Custom STT Endpoint")
                }
            }
        }

        // Check LLM
        when (pipeline.llmProviderId.lowercase()) {
            "groq" -> if (!hasKey("groq")) missing.add("Groq")
            "openrouter" -> if (!hasKey("openrouter")) missing.add("OpenRouter")
            "openai" -> if (!hasKey("openai")) missing.add("OpenAI")
            "gemini" -> if (!hasKey("gemini")) missing.add("Gemini")
        }

        // Check TTS
        when (pipeline.ttsProviderId.lowercase()) {
            "cartesia" -> if (!hasKey("cartesia")) missing.add("Cartesia")
            "elevenlabs" -> if (!hasKey("elevenlabs")) missing.add("ElevenLabs")
            "openai" -> if (!hasKey("openai")) missing.add("OpenAI")
            "deepgram", "deepgram_aura" -> if (!hasKey("deepgram")) missing.add("Deepgram")
            "custom" -> {
                if (pipeline.customEndpointUrl.isBlank() && keyVault.getKey("custom_tts_url").isBlank() && cachedSettings.localLlmUrl.isBlank()) {
                    missing.add("Custom TTS Endpoint")
                }
            }
            "android" -> { /* Built-in on-device offline, always ready */ }
        }

        return if (missing.isEmpty()) {
            PipelineReadiness.Ready
        } else {
            PipelineReadiness.MissingCredentials(missing.distinct())
        }
    }

    /**
     * Activates a pipeline: applies its parameters to the active runtime settings
     * and records it as the active pipeline.
     */
    suspend fun activatePipeline(pipeline: PipelineConfig) {
        pipelineRepository.setActivePipelineId(pipeline.id)

        // 1. STT Provider
        val targetStt = when (pipeline.sttProviderId.lowercase()) {
            "groq", "groq_whisper" -> SttProvider.GROQ_WHISPER.name
            "whisper", "openai" -> SttProvider.WHISPER.name
            "custom" -> SttProvider.CUSTOM.name
            else -> SttProvider.DEEPGRAM.name
        }
        settingsRepository.updateSttProvider(targetStt)

        // 2. LLM Provider & Model
        val targetProvider = when (pipeline.llmProviderId.lowercase()) {
            "openrouter" -> ProviderType.OPENROUTER.name
            "openai" -> ProviderType.OPENAI_COMPAT.name
            "gemini" -> ProviderType.GEMINI.name
            "local" -> ProviderType.LOCAL.name
            "custom" -> ProviderType.OPENAI_COMPAT.name
            else -> if (pipeline.customEndpointUrl.isNotBlank()) ProviderType.OPENAI_COMPAT.name else ProviderType.GROQ.name
        }
        if (pipeline.customEndpointUrl.isNotBlank()) {
            settingsRepository.updateLocalLlmUrl(pipeline.customEndpointUrl)
        }
        settingsRepository.updateProviderType(targetProvider)
        settingsRepository.updateModelId(pipeline.llmModelId)
        settingsRepository.updateTemperature(pipeline.temperature)
        settingsRepository.updateSystemPrompt(pipeline.systemPrompt)

        // 3. TTS Settings
        val targetTts = when (pipeline.ttsProviderId.lowercase()) {
            "elevenlabs" -> TtsProvider.ELEVENLABS.name
            "openai" -> TtsProvider.OPENAI.name
            "deepgram", "deepgram_aura" -> TtsProvider.DEEPGRAM_AURA.name
            "android" -> TtsProvider.ANDROID.name
            "custom" -> TtsProvider.CUSTOM.name
            else -> TtsProvider.CARTESIA.name
        }
        settingsRepository.updateTtsProvider(targetTts)
        settingsRepository.updateCartesiaVoiceId(pipeline.ttsVoiceId)
        settingsRepository.updateTtsSpeechRate(pipeline.speechRate)
        settingsRepository.updateTtsPitch(pipeline.pitch)

        // 4. VAD & Conversational UX
        settingsRepository.updateVadEnabled(pipeline.vadEnabled)
        settingsRepository.updateVadSensitivity(pipeline.vadSensitivity)
        settingsRepository.updateBackchannelEnabled(pipeline.backchannelEnabled)
    }
}


package com.techrifter.fluxvoice.data.repository

import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
import com.techrifter.fluxvoice.data.security.KeyVault
import androidx.datastore.preferences.core.Preferences
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.VoiceMode
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val keyVault: KeyVault
) : SettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStore.settings

    // ── App Mode ──────────────────────────────────────────────────────────────
    override suspend fun updateAppMode(value: String) =
        dataStore.update { it[SettingsDataStore.APP_MODE] = value }

    override suspend fun applyFastPreset() = dataStore.update { p ->
        p[SettingsDataStore.PROVIDER_TYPE]       = ProviderType.GROQ.name
        p[SettingsDataStore.MODEL_ID]            = ProviderType.GROQ.defaultModel
        p[SettingsDataStore.TEMPERATURE]         = 0.7f
        p[SettingsDataStore.STT_PROVIDER]        = SttProvider.DEEPGRAM.name
        p[SettingsDataStore.MAX_CONTEXT_TURNS]   = 6
        p[SettingsDataStore.VAD_ENABLED]         = true
        p[SettingsDataStore.VAD_SENSITIVITY]     = 1_000
        p[SettingsDataStore.BACKCHANNEL_ENABLED] = true
        p[SettingsDataStore.VOICE_MODE]          = VoiceMode.STANDARD.name
        p[SettingsDataStore.TTS_ENABLED]         = true
        p[SettingsDataStore.TTS_PROVIDER]        = "cartesia"
        p[SettingsDataStore.CARTESIA_VOICE_ID]   = "638efaaa-4d0c-442e-b701-3fae16aad012"
        p[SettingsDataStore.SYSTEM_PROMPT]       = "You are Flux Fast, an ultra-low-latency voice assistant. Deliver immediate, high-signal answers in 1 to 2 punchy sentences. Never use preamble, pleasantries, or filler."
    }

    override suspend fun applyThinkingPreset() = dataStore.update { p ->
        p[SettingsDataStore.PROVIDER_TYPE]       = ProviderType.GEMINI.name
        p[SettingsDataStore.MODEL_ID]            = "gemini-2.5-flash"
        p[SettingsDataStore.TEMPERATURE]         = 0.3f
        p[SettingsDataStore.STT_PROVIDER]        = SttProvider.DEEPGRAM.name
        p[SettingsDataStore.MAX_CONTEXT_TURNS]   = 20
        p[SettingsDataStore.VAD_ENABLED]         = true
        p[SettingsDataStore.VAD_SENSITIVITY]     = 600
        p[SettingsDataStore.BACKCHANNEL_ENABLED] = false
        p[SettingsDataStore.VOICE_MODE]          = VoiceMode.STANDARD.name
        p[SettingsDataStore.TTS_ENABLED]         = true
        p[SettingsDataStore.TTS_PROVIDER]        = "cartesia"
        p[SettingsDataStore.CARTESIA_VOICE_ID]   = "638efaaa-4d0c-442e-b701-3fae16aad012"
        p[SettingsDataStore.SYSTEM_PROMPT]       = "You are Flux Reason, an expert voice advisor for deep reasoning and system architecture. Analyze questions with first-principles depth, then articulate your answer in clear, flowing spoken paragraphs without bullet points or outlines."
    }

    override suspend fun applyCustomPreset() = dataStore.update { p ->
        p[SettingsDataStore.PROVIDER_TYPE]       = ProviderType.GROQ.name
        p[SettingsDataStore.MODEL_ID]            = ""
        p[SettingsDataStore.CARTESIA_VOICE_ID]   = ""
        p[SettingsDataStore.LOCAL_LLM_URL]       = ""
        p[SettingsDataStore.TEMPERATURE]         = 0.7f
        p[SettingsDataStore.STT_PROVIDER]        = SttProvider.DEEPGRAM.name
        p[SettingsDataStore.MAX_CONTEXT_TURNS]   = 10
        p[SettingsDataStore.VAD_ENABLED]         = true
        p[SettingsDataStore.VAD_SENSITIVITY]     = 1_000
        p[SettingsDataStore.BACKCHANNEL_ENABLED] = false
        p[SettingsDataStore.VOICE_MODE]          = VoiceMode.STANDARD.name
        p[SettingsDataStore.TTS_ENABLED]         = true
        p[SettingsDataStore.TTS_PROVIDER]        = "cartesia"
        p[SettingsDataStore.SYSTEM_PROMPT]       = com.techrifter.fluxvoice.domain.model.PipelineConfig.DEFAULT_SYSTEM_PROMPT
    }

    // ── Voice Mode ────────────────────────────────────────────────────────────
    override suspend fun updateVoiceMode(value: String) =
        dataStore.update { it[SettingsDataStore.VOICE_MODE] = value }

    // ── STT Provider ──────────────────────────────────────────────────────────
    override suspend fun updateSttProvider(value: String) =
        dataStore.update { it[SettingsDataStore.STT_PROVIDER] = value }

    // ── AI Provider ───────────────────────────────────────────────────────────
    override suspend fun updateProviderType(value: String) =
        dataStore.update { it[SettingsDataStore.PROVIDER_TYPE] = value }

    override suspend fun updateGeminiApiKey(value: String) {
        saveApiKey("gemini", SettingsDataStore.GEMINI_API_KEY, value)
    }

    override suspend fun updateOpenAiApiKey(value: String) {
        saveApiKey("openai", SettingsDataStore.OPENAI_API_KEY, value)
    }

    override suspend fun updateGroqApiKey(value: String) {
        saveApiKey("groq", SettingsDataStore.GROQ_API_KEY, value)
    }

    override suspend fun updateOpenRouterApiKey(value: String) {
        saveApiKey("openrouter", SettingsDataStore.OPENROUTER_API_KEY, value)
    }

    override suspend fun updateLocalLlmUrl(value: String) =
        dataStore.update { it[SettingsDataStore.LOCAL_LLM_URL] = value }

    override suspend fun updateModelId(value: String) =
        dataStore.update { it[SettingsDataStore.MODEL_ID] = value }

    override suspend fun updateTemperature(value: Float) =
        dataStore.update { it[SettingsDataStore.TEMPERATURE] = value }

    override suspend fun updateSystemPrompt(value: String) =
        dataStore.update { it[SettingsDataStore.SYSTEM_PROMPT] = value }

    // ── ElevenLabs ────────────────────────────────────────────────────────────
    override suspend fun updateElevenLabsAgentId(value: String) =
        dataStore.update { it[SettingsDataStore.ELEVENLABS_AGENT_ID] = value }

    override suspend fun updateElevenLabsApiKey(value: String) {
        saveApiKey("elevenlabs", SettingsDataStore.ELEVENLABS_API_KEY, value)
    }

    // ── Deepgram ──────────────────────────────────────────────────────────────
    override suspend fun updateDeepgramApiKey(value: String) {
        saveApiKey("deepgram", SettingsDataStore.DEEPGRAM_API_KEY, value)
    }

    // ── Voice Conversation ────────────────────────────────────────────────────
    override suspend fun updateMaxContextTurns(value: Int) =
        dataStore.update { it[SettingsDataStore.MAX_CONTEXT_TURNS] = value }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    override suspend fun updateTtsEnabled(value: Boolean) =
        dataStore.update { it[SettingsDataStore.TTS_ENABLED] = value }

    override suspend fun updateTtsProvider(value: String) =
        dataStore.update { it[SettingsDataStore.TTS_PROVIDER] = value }

    override suspend fun updateTtsSpeechRate(value: Float) =
        dataStore.update { it[SettingsDataStore.TTS_SPEECH_RATE] = value }

    override suspend fun updateTtsPitch(value: Float) =
        dataStore.update { it[SettingsDataStore.TTS_PITCH] = value }

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    override suspend fun updateCartesiaApiKey(value: String) {
        saveApiKey("cartesia", SettingsDataStore.CARTESIA_API_KEY, value)
    }

    override suspend fun updateCartesiaVoiceId(value: String) =
        dataStore.update { it[SettingsDataStore.CARTESIA_VOICE_ID] = value }

    // ── Conversational UX ─────────────────────────────────────────────────────
    override suspend fun updateVadEnabled(value: Boolean) =
        dataStore.update { it[SettingsDataStore.VAD_ENABLED] = value }

    override suspend fun updateVadSensitivity(value: Int) =
        dataStore.update { it[SettingsDataStore.VAD_SENSITIVITY] = value }

    override suspend fun updateBackchannelEnabled(value: Boolean) =
        dataStore.update { it[SettingsDataStore.BACKCHANNEL_ENABLED] = value }

    override suspend fun updateSplashEnabled(value: Boolean) =
        dataStore.update { it[SettingsDataStore.SPLASH_ENABLED] = value }

    // ── Anonymous Identity & Telemetry ────────────────────────────────────────
    override suspend fun updateAnonymousIdentity(username: String) =
        dataStore.update {
            it[SettingsDataStore.ANONYMOUS_USERNAME] = username
        }

    override suspend fun updateTelemetryOptIn(value: Boolean) =
        dataStore.update { it[SettingsDataStore.TELEMETRY_OPT_IN] = value }

    private suspend fun saveApiKey(
        providerId: String,
        preferenceKey: Preferences.Key<String>,
        value: String
    ) {
        keyVault.setKey(providerId, value.trim())
        dataStore.update { it.remove(preferenceKey) }
    }
}

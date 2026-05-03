package com.techrifter.fluxvoice.data.repository

import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
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
    private val dataStore: SettingsDataStore
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
    }

    override suspend fun applyThinkingPreset() = dataStore.update { p ->
        p[SettingsDataStore.PROVIDER_TYPE]       = ProviderType.GEMINI.name
        p[SettingsDataStore.MODEL_ID]            = "gemini-2.5-pro"
        p[SettingsDataStore.TEMPERATURE]         = 0.3f
        p[SettingsDataStore.STT_PROVIDER]        = SttProvider.DEEPGRAM.name
        p[SettingsDataStore.MAX_CONTEXT_TURNS]   = 20
        p[SettingsDataStore.VAD_ENABLED]         = true
        p[SettingsDataStore.VAD_SENSITIVITY]     = 600
        p[SettingsDataStore.BACKCHANNEL_ENABLED] = false
        p[SettingsDataStore.VOICE_MODE]          = VoiceMode.STANDARD.name
        p[SettingsDataStore.TTS_ENABLED]         = true
        p[SettingsDataStore.SYSTEM_PROMPT]       = "You are a thoughtful voice assistant. Reason carefully before responding. Be accurate and thorough — explain your thinking when it helps, but stay conversational and avoid unnecessary filler."
    }

    override suspend fun applyCustomPreset() = dataStore.update { p ->
        p[SettingsDataStore.PROVIDER_TYPE]       = ProviderType.GROQ.name
        p[SettingsDataStore.MODEL_ID]            = ProviderType.GROQ.defaultModel
        p[SettingsDataStore.TEMPERATURE]         = 0.7f
        p[SettingsDataStore.STT_PROVIDER]        = SttProvider.DEEPGRAM.name
        p[SettingsDataStore.MAX_CONTEXT_TURNS]   = 10
        p[SettingsDataStore.VAD_ENABLED]         = true
        p[SettingsDataStore.VAD_SENSITIVITY]     = 1_000
        p[SettingsDataStore.BACKCHANNEL_ENABLED] = false
        p[SettingsDataStore.VOICE_MODE]          = VoiceMode.STANDARD.name
        p[SettingsDataStore.TTS_ENABLED]         = true
        p[SettingsDataStore.SYSTEM_PROMPT]       = "You are a helpful voice assistant. Keep responses concise. Be friendly and conversational."
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

    override suspend fun updateGeminiApiKey(value: String) =
        dataStore.update { it[SettingsDataStore.GEMINI_API_KEY] = value }

    override suspend fun updateOpenAiApiKey(value: String) =
        dataStore.update { it[SettingsDataStore.OPENAI_API_KEY] = value }

    override suspend fun updateGroqApiKey(value: String) =
        dataStore.update { it[SettingsDataStore.GROQ_API_KEY] = value }

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

    // ── Deepgram ──────────────────────────────────────────────────────────────
    override suspend fun updateDeepgramApiKey(value: String) =
        dataStore.update { it[SettingsDataStore.DEEPGRAM_API_KEY] = value }

    // ── Voice Conversation ────────────────────────────────────────────────────
    override suspend fun updateMaxContextTurns(value: Int) =
        dataStore.update { it[SettingsDataStore.MAX_CONTEXT_TURNS] = value }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    override suspend fun updateTtsEnabled(value: Boolean) =
        dataStore.update { it[SettingsDataStore.TTS_ENABLED] = value }

    override suspend fun updateTtsSpeechRate(value: Float) =
        dataStore.update { it[SettingsDataStore.TTS_SPEECH_RATE] = value }

    override suspend fun updateTtsPitch(value: Float) =
        dataStore.update { it[SettingsDataStore.TTS_PITCH] = value }

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    override suspend fun updateCartesiaApiKey(value: String) =
        dataStore.update { it[SettingsDataStore.CARTESIA_API_KEY] = value }

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
}

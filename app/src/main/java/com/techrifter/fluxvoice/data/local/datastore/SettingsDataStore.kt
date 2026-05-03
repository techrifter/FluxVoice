package com.techrifter.fluxvoice.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.VoiceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object Keys {
        // ── App Mode ──────────────────────────────────────────────────────────
        val APP_MODE              = stringPreferencesKey("app_mode")
        // ── Voice Mode ────────────────────────────────────────────────────────
        val VOICE_MODE            = stringPreferencesKey("voice_mode")
        // ── STT Provider ──────────────────────────────────────────────────────
        val STT_PROVIDER          = stringPreferencesKey("stt_provider")
        // ── AI Provider ───────────────────────────────────────────────────────
        val PROVIDER_TYPE         = stringPreferencesKey("provider_type")
        val GEMINI_API_KEY        = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY        = stringPreferencesKey("openai_api_key")
        val GROQ_API_KEY          = stringPreferencesKey("groq_api_key")
        val LOCAL_LLM_URL         = stringPreferencesKey("local_llm_url")
        val MODEL_ID              = stringPreferencesKey("model_id")
        val TEMPERATURE           = floatPreferencesKey("temperature")
        val SYSTEM_PROMPT         = stringPreferencesKey("system_prompt")
        // ── ElevenLabs ────────────────────────────────────────────────────────
        val ELEVENLABS_AGENT_ID   = stringPreferencesKey("elevenlabs_agent_id")
        // ── Deepgram ──────────────────────────────────────────────────────────
        val DEEPGRAM_API_KEY      = stringPreferencesKey("deepgram_api_key")
        // ── Voice Conversation ────────────────────────────────────────────────
        val MAX_CONTEXT_TURNS     = intPreferencesKey("max_context_turns")
        // ── Text-to-Speech ────────────────────────────────────────────────────
        val TTS_ENABLED           = booleanPreferencesKey("tts_enabled")
        val TTS_SPEECH_RATE       = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH             = floatPreferencesKey("tts_pitch")
        // ── Cartesia TTS ──────────────────────────────────────────────────────
        val CARTESIA_API_KEY      = stringPreferencesKey("cartesia_api_key")
        val CARTESIA_VOICE_ID     = stringPreferencesKey("cartesia_voice_id")
        // ── Conversational UX ─────────────────────────────────────────────────
        val VAD_ENABLED           = booleanPreferencesKey("vad_enabled")
        val VAD_SENSITIVITY       = intPreferencesKey("vad_sensitivity")
        val BACKCHANNEL_ENABLED   = booleanPreferencesKey("backchannel_enabled")
        val SPLASH_ENABLED        = booleanPreferencesKey("splash_enabled")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val appModeName = prefs[APP_MODE] ?: AppMode.FAST.name
        val appMode = AppMode.entries.firstOrNull { it.name == appModeName } ?: AppMode.FAST
        val providerName  = prefs[PROVIDER_TYPE] ?: ProviderType.GEMINI.name
        val providerType  = ProviderType.entries.firstOrNull { it.name == providerName }
            ?: ProviderType.GEMINI
        val voiceModeName = prefs[VOICE_MODE] ?: VoiceMode.STANDARD.name
        val voiceModeRaw  = VoiceMode.entries.firstOrNull { it.name == voiceModeName }
            ?: VoiceMode.STANDARD
        val elevenLabsAgentId = prefs[ELEVENLABS_AGENT_ID] ?: ""
        val voiceMode = voiceModeRaw
        val sttProviderName = prefs[STT_PROVIDER] ?: SttProvider.DEEPGRAM.name
        val sttProvider   = SttProvider.entries.firstOrNull { it.name == sttProviderName }
            ?: SttProvider.DEEPGRAM
        AppSettings(
            appMode           = appMode,
            voiceMode         = voiceMode,
            sttProvider       = sttProvider,
            providerType      = providerType,
            geminiApiKey      = prefs[GEMINI_API_KEY]      ?: "",
            openAiApiKey      = prefs[OPENAI_API_KEY]      ?: "",
            groqApiKey        = prefs[GROQ_API_KEY]        ?: "",
            localLlmUrl       = prefs[LOCAL_LLM_URL]       ?: "http://localhost:11434",
            modelId           = prefs[MODEL_ID]            ?: providerType.defaultModel,
            temperature       = (prefs[TEMPERATURE] ?: 0.7f).coerceIn(0f, 1f),
            systemPrompt      = prefs[SYSTEM_PROMPT]       ?: "You are a helpful voice assistant. Keep responses concise and conversational.",
            elevenLabsAgentId = elevenLabsAgentId,
            deepgramApiKey    = prefs[DEEPGRAM_API_KEY]    ?: "",
            maxContextTurns   = prefs[MAX_CONTEXT_TURNS]   ?: 10,
            ttsEnabled        = prefs[TTS_ENABLED]         ?: true,
            ttsSpeechRate     = prefs[TTS_SPEECH_RATE]     ?: 1.0f,
            ttsPitch          = prefs[TTS_PITCH]           ?: 1.0f,
            cartesiaApiKey    = prefs[CARTESIA_API_KEY]    ?: "",
            cartesiaVoiceId   = prefs[CARTESIA_VOICE_ID]   ?: "638efaaa-4d0c-442e-b701-3fae16aad012",
            vadEnabled        = prefs[VAD_ENABLED]         ?: true,
            vadSensitivity    = prefs[VAD_SENSITIVITY]     ?: 1_000,
            backchannelEnabled = prefs[BACKCHANNEL_ENABLED] ?: false
        )
    }

    suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}

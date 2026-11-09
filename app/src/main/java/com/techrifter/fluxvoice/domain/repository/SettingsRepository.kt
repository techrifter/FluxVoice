package com.techrifter.fluxvoice.domain.repository

import com.techrifter.fluxvoice.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>

    // ── App Mode ──────────────────────────────────────────────────────────────
    suspend fun updateAppMode(value: String)
    suspend fun applyFastPreset()
    suspend fun applyThinkingPreset()
    suspend fun applyCustomPreset()

    // ── Voice Mode ────────────────────────────────────────────────────────────
    suspend fun updateVoiceMode(value: String)

    // ── STT Provider ──────────────────────────────────────────────────────────
    suspend fun updateSttProvider(value: String)

    // ── AI Provider ───────────────────────────────────────────────────────────
    suspend fun updateProviderType(value: String)
    suspend fun updateGeminiApiKey(value: String)
    suspend fun updateOpenAiApiKey(value: String)
    suspend fun updateGroqApiKey(value: String)
    suspend fun updateOpenRouterApiKey(value: String)
    suspend fun updateLocalLlmUrl(value: String)
    suspend fun updateModelId(value: String)
    suspend fun updateTemperature(value: Float)
    suspend fun updateSystemPrompt(value: String)

    // ── ElevenLabs ────────────────────────────────────────────────────────────
    suspend fun updateElevenLabsAgentId(value: String)
    suspend fun updateElevenLabsApiKey(value: String)

    // ── Deepgram ──────────────────────────────────────────────────────────────
    suspend fun updateDeepgramApiKey(value: String)

    // ── Voice Conversation ────────────────────────────────────────────────────
    suspend fun updateMaxContextTurns(value: Int)

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    suspend fun updateTtsEnabled(value: Boolean)
    suspend fun updateTtsProvider(value: String)
    suspend fun updateTtsSpeechRate(value: Float)
    suspend fun updateTtsPitch(value: Float)

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    suspend fun updateCartesiaApiKey(value: String)
    suspend fun updateCartesiaVoiceId(value: String)

    // ── Conversational UX ─────────────────────────────────────────────────────
    suspend fun updateVadEnabled(value: Boolean)
    suspend fun updateVadSensitivity(value: Int)
    suspend fun updateBackchannelEnabled(value: Boolean)

    // ── App preferences ───────────────────────────────────────────────────────
    suspend fun updateSplashEnabled(value: Boolean)

    // ── Anonymous Identity & Telemetry ────────────────────────────────────────
    suspend fun updateAnonymousIdentity(username: String)
    suspend fun updateTelemetryOptIn(value: Boolean)
}

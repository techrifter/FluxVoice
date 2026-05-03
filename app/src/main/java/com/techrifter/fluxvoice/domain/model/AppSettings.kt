package com.techrifter.fluxvoice.domain.model

/**
 * User-facing settings for the application.
 * Persisted via DataStore; read and written through SettingsRepository.
 */
data class AppSettings(
    // ── App Mode (Fast / Thinking / Custom) ───────────────────────────────────
    val appMode: AppMode = AppMode.FAST,

    // ── Voice Pipeline Mode ───────────────────────────────────────────────────
    val voiceMode: VoiceMode = VoiceMode.STANDARD,

    // ── STT Provider (STANDARD mode) ─────────────────────────────────────────
    /** Selects the real-time STT engine used in Standard pipeline mode. */
    val sttProvider: SttProvider = SttProvider.DEEPGRAM,

    // ── AI Provider ──────────────────────────────────────────────────────────
    val providerType: ProviderType = ProviderType.GEMINI,
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val groqApiKey: String = "",
    val localLlmUrl: String = "http://localhost:11434",
    val modelId: String = ProviderType.GEMINI.defaultModel,
    val temperature: Float = 0.7f,
    val systemPrompt: String = "You are a helpful voice assistant. Keep responses concise and conversational.",

    // ── ElevenLabs ConvAI ─────────────────────────────────────────────────────
    val elevenLabsAgentId: String = "",

    // ── Deepgram STT ──────────────────────────────────────────────────────────
    val deepgramApiKey: String = "",

    // ── Voice Conversation ────────────────────────────────────────────────────
    val maxContextTurns: Int = 10,

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    val ttsEnabled: Boolean = true,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    /** Leave blank to use the built-in Android TTS as fallback. */
    val cartesiaApiKey: String = "",
    val cartesiaVoiceId: String = "638efaaa-4d0c-442e-b701-3fae16aad012",

    // ── Conversational UX ─────────────────────────────────────────────────────
    /** Auto barge-in: stop AI speech when mic detects user voice while TTS plays. */
    val vadEnabled: Boolean = true,
    /**
     * Barge-in sensitivity (200–3000). Lower = more sensitive.
     * Mapped to the WebRTC VAD aggressiveness mode:
     *   ≤ 600  → NORMAL (High)   — permissive, quick to trigger
     *   ≤ 1500 → AGGRESSIVE (Medium) — default
     *   > 1500 → VERY_AGGRESSIVE (Low) — strict, best for noisy environments
     */
    val vadSensitivity: Int = 1_000,
    /** Speak a short acknowledgment ("Got it.", "Sure."…) immediately after STT. */
    val backchannelEnabled: Boolean = false,
    val splashEnabled: Boolean = true
)

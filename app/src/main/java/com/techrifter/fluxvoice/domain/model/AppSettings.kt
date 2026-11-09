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
    val openRouterApiKey: String = "",
    val localLlmUrl: String = "",
    val modelId: String = "",
    val temperature: Float = 0.7f,
    val systemPrompt: String = PipelineConfig.DEFAULT_SYSTEM_PROMPT,

    // ── ElevenLabs ConvAI ─────────────────────────────────────────────────────
    val elevenLabsAgentId: String = "",
    val elevenLabsApiKey: String = "",

    // ── Deepgram STT ──────────────────────────────────────────────────────────
    val deepgramApiKey: String = "",

    // ── Voice Conversation ────────────────────────────────────────────────────
    val maxContextTurns: Int = 10,

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    val ttsEnabled: Boolean = true,
    val ttsProvider: TtsProvider = TtsProvider.CARTESIA,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    /** Leave blank to use the built-in Android TTS as fallback. */
    val cartesiaApiKey: String = "",
    val cartesiaVoiceId: String = "",

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
    val splashEnabled: Boolean = true,

    // ── Anonymous Identity & Telemetry ────────────────────────────────────────
    val anonymousUsername: String = "",
    val telemetryOptIn: Boolean = true
)

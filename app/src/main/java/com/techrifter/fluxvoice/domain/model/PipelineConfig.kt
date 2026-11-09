package com.techrifter.fluxvoice.domain.model

import java.util.UUID

/**
 * First-class domain model representing a voice conversation pipeline.
 * Users can configure, test, compare, and export combinations of STT, LLM, and TTS.
 */
data class PipelineConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isPreset: Boolean = false,

    // ── STT Configuration ─────────────────────────────────────────────────────
    val sttProviderId: String = "deepgram",
    val sttModelId: String = "nova-3",
    val sttEndpointingMs: Int = 500,

    // ── LLM Configuration ─────────────────────────────────────────────────────
    val llmProviderId: String = "groq",
    val llmModelId: String = "openai/gpt-oss-120b",
    val customEndpointUrl: String = "",
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,

    // ── TTS Configuration ─────────────────────────────────────────────────────
    val ttsProviderId: String = "cartesia",
    val ttsVoiceId: String = "638efaaa-4d0c-442e-b701-3fae16aad012",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,

    // ── Conversational Dynamics ───────────────────────────────────────────────
    val vadEnabled: Boolean = true,
    val vadSensitivity: Int = 1000,
    val backchannelEnabled: Boolean = false,
    val backchannelDelayMs: Long = 1500L,

    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Flux, an ultra-fast, intelligent voice AI assistant. Speak with clarity, confidence, and natural rhythm. Deliver high-signal, direct answers tailored for spoken audio without pleasantry filler, markdown, or lists."
    }
}


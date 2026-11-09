package com.techrifter.fluxvoice.domain.model

/**
 * Selects which Text-to-Speech engine backs the voice pipeline.
 * FluxVoice provides unified runtime support for all industry-standard TTS providers.
 */
enum class TtsProvider(
    val id: String,
    val displayName: String,
    val defaultVoiceId: String
) {
    CARTESIA(
        id = "cartesia",
        displayName = "Cartesia Sonic",
        defaultVoiceId = "638efaaa-4d0c-442e-b701-3fae16aad012"
    ),
    ELEVENLABS(
        id = "elevenlabs",
        displayName = "ElevenLabs Neural",
        defaultVoiceId = "JBFqnCBsd6RMkjVDRZzb"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI Speech (tts-1)",
        defaultVoiceId = "alloy"
    ),
    DEEPGRAM_AURA(
        id = "deepgram",
        displayName = "Deepgram Aura",
        defaultVoiceId = "aura-asteria-en"
    ),
    ANDROID(
        id = "android",
        displayName = "Android System (Offline)",
        defaultVoiceId = "default"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom BYOK Endpoint",
        defaultVoiceId = "default"
    );

    companion object {
        fun fromId(id: String): TtsProvider = entries.firstOrNull {
            it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
        } ?: CARTESIA
    }
}


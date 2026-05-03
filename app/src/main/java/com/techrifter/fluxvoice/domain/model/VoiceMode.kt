package com.techrifter.fluxvoice.domain.model

/**
 * Selects which voice pipeline backs the conversation:
 *
 * - [STANDARD]    — STT → LLM streaming → TTS
 * - [ELEVENLABS]  — ElevenLabs ConvAI SDK (WebRTC, all-in-one STT + LLM + TTS managed by ElevenLabs)
 */
enum class VoiceMode(val displayName: String) {
    STANDARD("Standard (STT + AI)"),
    ELEVENLABS("ElevenLabs ConvAI")
}


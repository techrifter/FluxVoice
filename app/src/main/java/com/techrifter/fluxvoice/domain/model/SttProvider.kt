package com.techrifter.fluxvoice.domain.model

/**
 * Selects which Speech-to-Text engine backs the STANDARD voice pipeline.
 *
 * - [WHISPER]   — OpenAI Whisper API: records full utterance then POSTs WAV (batch / push-to-talk)
 * - [DEEPGRAM]  — Deepgram Streaming: streams raw PCM over WebSocket with live interim results
 */
enum class SttProvider(val displayName: String, val id: String) {
    DEEPGRAM("Deepgram Nova-3 (Real-Time WebSocket)", "deepgram"),
    GROQ_WHISPER("Groq Whisper Turbo (Fast Cloud REST)", "groq_whisper"),
    WHISPER("OpenAI Whisper (Cloud REST)", "whisper"),
    CUSTOM("Custom BYOK Endpoint", "custom");
    
    companion object {
        fun fromId(id: String): SttProvider = entries.find { it.id.equals(id, ignoreCase = true) } ?: DEEPGRAM
    }
}


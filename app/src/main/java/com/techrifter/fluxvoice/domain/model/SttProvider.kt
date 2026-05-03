package com.techrifter.fluxvoice.domain.model

/**
 * Selects which Speech-to-Text engine backs the STANDARD voice pipeline.
 *
 * - [WHISPER]   — OpenAI Whisper API: records full utterance then POSTs WAV (batch / push-to-talk)
 * - [DEEPGRAM]  — Deepgram Streaming: streams raw PCM over WebSocket with live interim results
 */
enum class SttProvider(val displayName: String) {
    WHISPER("Whisper (OpenAI — batch)"),
    DEEPGRAM("Deepgram (real-time streaming)")
}


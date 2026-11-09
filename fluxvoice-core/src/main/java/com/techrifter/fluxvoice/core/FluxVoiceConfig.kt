package com.techrifter.fluxvoice.core

import com.techrifter.fluxvoice.core.llm.LlmProvider
import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.core.state.VoiceState
import com.techrifter.fluxvoice.core.stt.SttProvider
import com.techrifter.fluxvoice.core.tts.TtsProvider

/**
 * Immutable configuration for a FluxVoice session.
 * Build via [FluxVoiceConfig] DSL function or [FluxVoiceConfig.Builder].
 */
public class FluxVoiceConfig private constructor(builder: Builder) {

    public val sttProvider: SttProvider?          = builder.sttProvider
    public val ttsProvider: TtsProvider?          = builder.ttsProvider
    public val llmProvider: LlmProvider?          = builder.llmProvider

    public val modelId: String                    = builder.modelId
    public val systemPrompt: String               = builder.systemPrompt
    public val maxContextTurns: Int               = builder.maxContextTurns

    public val vadEnabled: Boolean                = builder.vadEnabled
    public val vadSensitivity: Int                = builder.vadSensitivity

    /** Speak a short filler phrase when LLM latency exceeds [backchannelDelayMs]. */
    public val backchannelEnabled: Boolean        = builder.backchannelEnabled
    public val backchannelDelayMs: Long           = builder.backchannelDelayMs

    /** LLM sampling temperature (0.0–2.0). Lower = more deterministic, higher = more creative. Default 0.7. */
    public val temperature: Float                 = builder.temperature

    /** Maximum tokens the LLM may generate per turn. Default 2048. */
    public val maxOutputTokens: Int               = builder.maxOutputTokens

    /** TTS playback speech rate (0.5–2.0). Default 1.0. */
    public val speechRate: Float                  = builder.speechRate

    /** TTS playback pitch (0.5–2.0). Default 1.0. */
    public val pitch: Float                       = builder.pitch

    // ── Lifecycle hooks — called on the main thread ───────────────────────────

    /** Called with the final user transcript after each STT turn. */
    public val onTranscript: ((String) -> Unit)?  = builder.onTranscript

    /** Called with the complete AI response after each turn. */
    public val onResponse: ((String) -> Unit)?    = builder.onResponse

    /** Called whenever an error occurs. Return true to suppress the default error UI. */
    public val onError: ((Throwable) -> Unit)?    = builder.onError

    /** Called on every [VoiceState] transition. */
    public val onStateChange: ((from: VoiceState, to: VoiceState) -> Unit)? = builder.onStateChange

    /** Called when turn latency and resource metrics are finalized. */
    public val onTurnMetrics: ((TurnMetrics) -> Unit)? = builder.onTurnMetrics

    public class Builder {
        public var sttProvider: SttProvider?    = null
        public var ttsProvider: TtsProvider?    = null
        public var llmProvider: LlmProvider?    = null

        /** Convenience alias for [sttProvider]. */
        public var stt: SttProvider?
            get() = sttProvider
            set(value) { sttProvider = value }

        /** Convenience alias for [ttsProvider]. */
        public var tts: TtsProvider?
            get() = ttsProvider
            set(value) { ttsProvider = value }

        /** Convenience alias for [llmProvider]. */
        public var llm: LlmProvider?
            get() = llmProvider
            set(value) { llmProvider = value }
        public var modelId: String              = ""
        public var systemPrompt: String         = DEFAULT_SYSTEM_PROMPT
        public var maxContextTurns: Int         = 10
        public var vadEnabled: Boolean          = true
        public var vadSensitivity: Int          = 1_000
        public var backchannelEnabled: Boolean  = false
        public var backchannelDelayMs: Long     = 1_500L
        public var temperature: Float           = 0.7f
        public var maxOutputTokens: Int         = 2_048
        public var speechRate: Float            = 1.0f
        public var pitch: Float                 = 1.0f
        public var onTranscript: ((String) -> Unit)?  = null
        public var onResponse: ((String) -> Unit)?    = null
        public var onError: ((Throwable) -> Unit)?    = null
        public var onStateChange: ((VoiceState, VoiceState) -> Unit)? = null
        public var onTurnMetrics: ((TurnMetrics) -> Unit)? = null

        /** Hook: called with the final user transcript each turn. */
        public fun onTranscript(block: (String) -> Unit): Builder =
            apply { onTranscript = block }

        /** Hook: called with the full AI response each turn. */
        public fun onResponse(block: (String) -> Unit): Builder =
            apply { onResponse = block }

        /** Hook: called on any error. */
        public fun onError(block: (Throwable) -> Unit): Builder =
            apply { onError = block }

        /** Hook: called on every voice state transition. */
        public fun onStateChange(block: (from: VoiceState, to: VoiceState) -> Unit): Builder =
            apply { onStateChange = block }

        /** Hook: called when turn performance and telemetry metrics are recorded. */
        public fun onTurnMetrics(block: (TurnMetrics) -> Unit): Builder =
            apply { onTurnMetrics = block }

        public fun build(): FluxVoiceConfig = FluxVoiceConfig(this)
    }

    public companion object {
        public const val DEFAULT_SYSTEM_PROMPT: String =
            "You are Flux, an ultra-fast, intelligent voice AI assistant. Speak with clarity, confidence, and natural rhythm. Deliver high-signal, direct answers tailored for spoken audio without pleasantry filler, markdown, or lists."

        /** Kotlin DSL entry point. */
        public operator fun invoke(block: Builder.() -> Unit): FluxVoiceConfig =
            Builder().apply(block).build()
    }
}

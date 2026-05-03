package com.techrifter.fluxvoice.core

import com.techrifter.fluxvoice.core.llm.LlmProvider
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

    // ── Lifecycle hooks — called on the main thread ───────────────────────────

    /** Called with the final user transcript after each STT turn. */
    public val onTranscript: ((String) -> Unit)?  = builder.onTranscript

    /** Called with the complete AI response after each turn. */
    public val onResponse: ((String) -> Unit)?    = builder.onResponse

    /** Called whenever an error occurs. Return true to suppress the default error UI. */
    public val onError: ((Throwable) -> Unit)?    = builder.onError

    /** Called on every [VoiceState] transition. */
    public val onStateChange: ((from: VoiceState, to: VoiceState) -> Unit)? = builder.onStateChange

    public class Builder {
        public var sttProvider: SttProvider?    = null
        public var ttsProvider: TtsProvider?    = null
        public var llmProvider: LlmProvider?    = null
        public var systemPrompt: String         = DEFAULT_SYSTEM_PROMPT
        public var maxContextTurns: Int         = 10
        public var vadEnabled: Boolean          = true
        public var vadSensitivity: Int          = 1_000
        public var backchannelEnabled: Boolean  = false
        public var backchannelDelayMs: Long     = 1_500L
        public var temperature: Float           = 0.7f
        public var maxOutputTokens: Int         = 2_048
        public var onTranscript: ((String) -> Unit)?  = null
        public var onResponse: ((String) -> Unit)?    = null
        public var onError: ((Throwable) -> Unit)?    = null
        public var onStateChange: ((VoiceState, VoiceState) -> Unit)? = null

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

        public fun build(): FluxVoiceConfig = FluxVoiceConfig(this)
    }

    public companion object {
        public const val DEFAULT_SYSTEM_PROMPT: String =
            "You are a helpful voice assistant. Keep responses concise and conversational."

        /** Kotlin DSL entry point. */
        public operator fun invoke(block: Builder.() -> Unit): FluxVoiceConfig =
            Builder().apply(block).build()
    }
}

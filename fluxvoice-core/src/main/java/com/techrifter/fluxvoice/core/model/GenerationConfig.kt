package com.techrifter.fluxvoice.core.model

/**
 * Per-request generation parameters forwarded to [com.techrifter.fluxvoice.core.llm.LlmProvider].
 */
public data class GenerationConfig(
    public val modelId: String = "",
    public val temperature: Float = 0.7f,
    public val maxOutputTokens: Int = 2048,
    public val systemPrompt: String = ""
)

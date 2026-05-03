package com.techrifter.fluxvoice.core.model

/**
 * Per-request generation parameters forwarded to [com.techrifter.fluxvoice.core.llm.LlmProvider].
 */
public data class GenerationConfig(
    val modelId: String = "",
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val systemPrompt: String = ""
)

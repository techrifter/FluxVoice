package com.techrifter.fluxvoice.domain.model

/**
 * Configuration passed to a provider for each generation request.
 */
data class GenerationConfig(
    val modelId: String = "gemini-2.5-flash",
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val systemPrompt: String? = null
)


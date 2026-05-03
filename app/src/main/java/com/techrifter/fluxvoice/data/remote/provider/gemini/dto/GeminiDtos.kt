package com.techrifter.fluxvoice.data.remote.provider.gemini.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request ─────────────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction? = null
)

@Serializable
data class GeminiContent(
    val role: String, // "user" | "model"
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
    @SerialName("topP") val topP: Float? = null
)

@Serializable
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

// ─── Streaming Response ───────────────────────────────────────────────────────

@Serializable
data class GeminiStreamResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)


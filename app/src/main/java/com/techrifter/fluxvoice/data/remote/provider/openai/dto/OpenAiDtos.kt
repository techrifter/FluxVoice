package com.techrifter.fluxvoice.data.remote.provider.openai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request ─────────────────────────────────────────────────────────────────

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    @SerialName("stream_options") val streamOptions: OpenAiStreamOptions? = OpenAiStreamOptions(includeUsage = true),
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
data class OpenAiMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

// ─── Streaming Response ───────────────────────────────────────────────────────

@Serializable
data class OpenAiStreamChunk(
    val id: String? = null,
    val choices: List<OpenAiStreamChoice>? = null,
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)

// ─── Non-streaming Response (fallback for models that ignore stream=true) ────

@Serializable
data class OpenAiCompletionResponse(
    val id: String? = null,
    val choices: List<OpenAiCompletionChoice>? = null
)

@Serializable
data class OpenAiCompletionChoice(
    val message: OpenAiCompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class OpenAiCompletionMessage(
    val role: String? = null,
    val content: String? = null
)


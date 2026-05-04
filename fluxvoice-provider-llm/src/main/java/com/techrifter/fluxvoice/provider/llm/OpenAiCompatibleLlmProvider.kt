package com.techrifter.fluxvoice.provider.llm

import android.util.Log
import com.techrifter.fluxvoice.core.llm.LlmProvider
import com.techrifter.fluxvoice.core.model.GenerationConfig
import com.techrifter.fluxvoice.core.model.Message
import com.techrifter.fluxvoice.core.model.MessageRole
import com.techrifter.fluxvoice.core.streaming.StreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "OpenAiCompatibleLlmProvider"
private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

/**
 * LLM provider using SSE streaming over the OpenAI chat completions protocol.
 * Defaults to Groq, but works with any compatible endpoint — OpenAI, Gemini, Ollama, etc.
 *
 * ```kotlin
 * // Groq (default)
 * llmProvider = OpenAiCompatibleLlmProvider(apiKey = BuildConfig.GROQ_KEY)
 *
 * // OpenAI
 * llmProvider = OpenAiCompatibleLlmProvider(
 *     apiKey  = BuildConfig.OPENAI_KEY,
 *     modelId = "gpt-4o-mini",
 *     baseUrl = "https://api.openai.com/v1/chat/completions"
 * )
 *
 * // Ollama (local, no key needed)
 * llmProvider = OpenAiCompatibleLlmProvider(
 *     apiKey  = "",
 *     modelId = "llama3",
 *     baseUrl = "http://localhost:11434/v1/chat/completions"
 * )
 * ```
 *
 * @param apiKey   API key for the provider. Pass an empty string for keyless endpoints (e.g. Ollama).
 * @param modelId  Model ID to request. Defaults to `llama-3.3-70b-versatile` (Groq).
 * @param baseUrl  Chat completions endpoint. Defaults to Groq's URL. Pass your own for other providers or local endpoints.
 */
public class OpenAiCompatibleLlmProvider(
    private val apiKey: String,
    private val modelId: String = "llama-3.3-70b-versatile",
    private val baseUrl: String = GROQ_BASE_URL,
) : LlmProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig
    ): Flow<StreamEvent> = channelFlow {
        send(StreamEvent.Start)
        try {
            val request = buildRequest(messages, config)
            val response = httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(request)
            }
            Log.d(TAG, "HTTP ${response.status.value}")

            if (!response.status.isSuccess()) {
                val body = buildString {
                    val ch = response.bodyAsChannel()
                    while (!ch.isClosedForRead) append(ch.readUTF8Line() ?: break)
                }
                val msg = when (response.status.value) {
                    400      -> "Bad request — check your model ID."
                    401, 403 -> "Invalid LLM API key. Update it in your config."
                    404      -> "Model '$modelId' not found."
                    429      -> "LLM Rate limit reached. Wait a moment and try again."
                    500, 503 -> "LLM Server error (${response.status.value}). Try again shortly."
                    else     -> "HTTP ${response.status.value}: $body"
                }
                send(StreamEvent.Error(Exception(msg)))
                return@channelFlow
            }

            val byteChannel = response.bodyAsChannel()
            while (!byteChannel.isClosedForRead) {
                val line = byteChannel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    parseToken(data)?.let { send(StreamEvent.Token(it)) }
                } else if (line.isNotBlank()) {
                    parseNonStreamingResponse(line)?.let { send(StreamEvent.Token(it)) }
                }
            }
            send(StreamEvent.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream error: ${e.message}", e)
            send(StreamEvent.Error(e))
        }
    }

    private fun buildRequest(messages: List<Message>, config: GenerationConfig): ChatRequest {
        val msgs = mutableListOf<ChatMessage>()
        val system = config.systemPrompt.ifBlank { null }
        system?.let { msgs += ChatMessage("system", it) }
        messages.forEach { m ->
            val role = when (m.role) {
                MessageRole.USER      -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM    -> "system"
            }
            msgs += ChatMessage(role, m.content)
        }
        return ChatRequest(
            model       = config.modelId.ifBlank { modelId },
            messages    = msgs,
            temperature = config.temperature,
            maxTokens   = config.maxOutputTokens.takeIf { it > 0 }
        )
    }

    private fun parseToken(data: String): String? = try {
        val chunk = json.decodeFromString<StreamChunk>(data)
        chunk.choices?.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "parseToken failed: ${e.message}")
        null
    }

    private fun parseNonStreamingResponse(data: String): String? = try {
        Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class StreamChunk(
    val choices: List<StreamChoice>? = null
)

@Serializable
private data class StreamChoice(
    val delta: StreamDelta? = null
)

@Serializable
private data class StreamDelta(
    val content: String? = null
)

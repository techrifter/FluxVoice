package com.techrifter.fluxvoice.data.remote.provider.groq

import com.techrifter.fluxvoice.data.remote.provider.openai.dto.OpenAiMessage
import com.techrifter.fluxvoice.data.remote.provider.openai.dto.OpenAiRequest
import com.techrifter.fluxvoice.data.remote.provider.openai.dto.OpenAiStreamChunk
import com.techrifter.fluxvoice.domain.model.AuthConfig
import com.techrifter.fluxvoice.domain.model.GenerationConfig
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.MessageRole
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.streaming.AiStreamingProvider
import com.techrifter.fluxvoice.domain.streaming.ProviderCapabilities
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GroqProvider"

private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"

/**
 * AI provider backed by the Groq inference platform (OpenAI-compatible API).
 *
 * Uses SSE streaming (`stream = true`) so tokens arrive with very low latency.
 * Default model: llama-3.3-70b-versatile — override via [GenerationConfig.modelId].
 */
@Singleton
class GroqProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiStreamingProvider {

    override val providerType: ProviderType = ProviderType.GROQ
    override val capabilities = ProviderCapabilities(
        supportsStreaming       = true,
        supportsFunctionCalling = true,
        maxContextTokens        = 131_072
    )

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig,
        authConfig: AuthConfig
    ): Flow<StreamEvent> = channelFlow {
        val channel = this
        channel.send(StreamEvent.Start)

        try {
            val request = buildRequest(messages, config)
            Log.d(TAG, "REQUEST url=$GROQ_CHAT_URL model=${config.modelId} apiKey=${authConfig.apiKey.take(8)}…")

            val response = httpClient.post(GROQ_CHAT_URL) {
                contentType(ContentType.Application.Json)
                bearerAuth(authConfig.apiKey)
                setBody(request)
            }

            Log.d(TAG, "RESPONSE status=${response.status.value} ${response.status.description}")

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsChannel().let {
                    buildString { while (!it.isClosedForRead) append(it.readUTF8Line() ?: break) }
                }
                Log.e(TAG, "ERROR body=$errorBody")
                val friendlyMessage = when (response.status.value) {
                    400        -> "Bad request — check your model ID in Settings."
                    401, 403   -> "Invalid Groq API key. Go to Settings → Credentials and verify it."
                    404        -> "Model '${config.modelId}' not found on Groq. Check the model ID in Settings."
                    429        -> "Groq rate limit reached. Wait a moment and try again."
                    500, 503   -> "Groq server error (${response.status.value}). Try again shortly."
                    else       -> "HTTP ${response.status.value}: ${response.status.description}"
                }
                channel.send(StreamEvent.Error(Exception(friendlyMessage)))
                return@channelFlow
            }

            // ── Stream SSE lines ──────────────────────────────────────────────
            val byteChannel = response.bodyAsChannel()
            var tokenCount = 0
            while (!byteChannel.isClosedForRead) {
                val line = byteChannel.readUTF8Line() ?: break
                Log.v(TAG, "SSE line: $line")
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty() || data == "[DONE]") {
                        if (data == "[DONE]") Log.d(TAG, "SSE [DONE] received, tokens=$tokenCount")
                        continue
                    }
                    parseToken(data)?.let { token ->
                        tokenCount++
                        Log.v(TAG, "TOKEN #$tokenCount: $token")
                        channel.send(StreamEvent.Token(token))
                    }
                } else if (line.isNotBlank()) {
                    // Some models ignore stream=true and return a single complete JSON object.
                    // Try to parse it as a non-streaming completion response and emit its content.
                    parseNonStreamingResponse(line)?.let { fullText ->
                        Log.d(TAG, "Non-streaming response detected, length=${fullText.length}")
                        tokenCount++
                        channel.send(StreamEvent.Token(fullText))
                    }
                }
            }
            Log.d(TAG, "Stream complete, total tokens=$tokenCount")
            channel.send(StreamEvent.Done)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "EXCEPTION: ${e.message}", e)
            channel.send(StreamEvent.Error(e))
        }
    }

    private fun buildRequest(messages: List<Message>, config: GenerationConfig): OpenAiRequest {
        val openAiMessages = mutableListOf<OpenAiMessage>()

        config.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            openAiMessages += OpenAiMessage(role = "system", content = it)
        }

        messages.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.USER      -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM    -> "system"
            }
            openAiMessages += OpenAiMessage(role = role, content = msg.content)
        }

        return OpenAiRequest(
            model       = config.modelId,
            messages    = openAiMessages,
            temperature = config.temperature,
            maxTokens   = config.maxOutputTokens
        )
    }

    private fun parseToken(data: String): String? = try {
        val chunk = json.decodeFromString<OpenAiStreamChunk>(data)
        chunk.choices?.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "parseToken failed for data=$data  err=${e.message}")
        null
    }

    /** Fallback: extract text from a complete (non-streaming) chat completion JSON. */
    private fun parseNonStreamingResponse(data: String): String? = try {
        Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "parseNonStreamingResponse failed: ${e.message}")
        null
    }
}


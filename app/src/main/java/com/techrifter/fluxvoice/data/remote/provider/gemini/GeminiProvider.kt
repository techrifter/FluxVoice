package com.techrifter.fluxvoice.data.remote.provider.gemini

import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiContent
import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiGenerationConfig
import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiPart
import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiRequest
import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiStreamResponse
import com.techrifter.fluxvoice.data.remote.provider.gemini.dto.GeminiSystemInstruction
import com.techrifter.fluxvoice.domain.model.AuthConfig
import com.techrifter.fluxvoice.domain.model.GenerationConfig
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.MessageRole
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.streaming.AiStreamingProvider
import com.techrifter.fluxvoice.domain.streaming.ProviderCapabilities
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models"

/** Max number of automatic retries on HTTP 429 (rate-limit) responses. */
private const val MAX_RETRIES = 3

@Singleton
class GeminiProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiStreamingProvider {

    override val providerType: ProviderType = ProviderType.GEMINI
    override val capabilities = ProviderCapabilities(
        supportsStreaming       = true,
        supportsFunctionCalling = true,
        maxContextTokens        = 1_000_000
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
            val url = "$BASE_URL/${config.modelId}:streamGenerateContent"

            var attempt = 0
            while (true) {
                val response = httpClient.post(url) {
                    parameter("key", authConfig.apiKey)
                    parameter("alt", "sse")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // ── Rate-limit: retry with exponential back-off ───────────────
                if (response.status.value == 429) {
                    if (attempt < MAX_RETRIES) {
                        val backoffMs = (2_000L * (1 shl attempt)) // 2s, 4s, 8s
                        attempt++
                        delay(backoffMs)
                        continue
                    } else {
                        channel.send(StreamEvent.Error(Exception(
                            "Rate limit reached (HTTP 429). You've exceeded the free-tier quota. " +
                            "Wait a minute or upgrade to a paid Gemini API plan."
                        )))
                        return@channelFlow
                    }
                }

                if (!response.status.isSuccess()) {
                    val friendlyMessage = when (response.status.value) {
                        400 -> "Bad request — check your API key and model ID in Settings."
                        401, 403 -> "Invalid or unauthorised API key. Go to Settings and verify it."
                        404 -> "Model '${config.modelId}' not found. Check the model ID in Settings."
                        500, 503 -> "Gemini server error (${response.status.value}). Try again shortly."
                        else -> "HTTP ${response.status.value}: ${response.status.description}"
                    }
                    channel.send(StreamEvent.Error(Exception(friendlyMessage)))
                    return@channelFlow
                }

                // ── Success: stream SSE lines ─────────────────────────────────
                val byteChannel = response.bodyAsChannel()
                while (!byteChannel.isClosedForRead) {
                    val line = byteChannel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue
                        parseToken(data)?.let { token -> channel.send(StreamEvent.Token(token)) }
                    }
                }
                channel.send(StreamEvent.Done)
                break // stream complete — exit retry loop
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            channel.send(StreamEvent.Error(e))
        }
    }

    private fun buildRequest(messages: List<Message>, config: GenerationConfig): GeminiRequest {
        val contents = messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { msg ->
                GeminiContent(
                    role = if (msg.role == MessageRole.USER) "user" else "model",
                    parts = listOf(GeminiPart(msg.content))
                )
            }

        val systemInstruction = config.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            GeminiSystemInstruction(listOf(GeminiPart(it)))
        }

        return GeminiRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = config.maxOutputTokens
            ),
            systemInstruction = systemInstruction
        )
    }

    private fun parseToken(data: String): String? = try {
        val response = json.decodeFromString<GeminiStreamResponse>(data)
        response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}


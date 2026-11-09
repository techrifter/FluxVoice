package com.techrifter.fluxvoice.data.remote.provider.openrouter

import android.util.Log
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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenRouterAiProvider"
private const val OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"

@Singleton
class OpenRouterAiProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiStreamingProvider {

    override val providerType: ProviderType = ProviderType.OPENROUTER
    override val capabilities = ProviderCapabilities(
        supportsStreaming       = true,
        supportsFunctionCalling = true,
        maxContextTokens        = 200_000
    )

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig,
        authConfig: AuthConfig
    ): Flow<StreamEvent> = channelFlow {
        val channel = this
        channel.send(StreamEvent.Start)

        if (authConfig.apiKey.isBlank()) {
            channel.send(
                StreamEvent.Error(
                    IllegalStateException("OpenRouter API key is missing. Set it in Key Vault or Settings.")
                )
            )
            return@channelFlow
        }

        try {
            val endpointUrl = if (authConfig.baseUrl.isNotBlank()) {
                val base = authConfig.baseUrl.trimEnd('/')
                if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            } else {
                OPENROUTER_CHAT_URL
            }

            val request = buildRequest(messages, config)

            val response = httpClient.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(authConfig.apiKey.trim())
                header("HTTP-Referer", "https://fluxvoice.ai")
                header("X-Title", "FluxVoice Playground")
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsChannel().let {
                    buildString { while (!it.isClosedForRead) append(it.readUTF8Line() ?: break) }
                }
                val detail = try {
                    json.parseToJsonElement(errorBody).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                } catch (e: Exception) { null }
                val friendlyMsg = when (response.status.value) {
                    402 -> detail ?: "OpenRouter requires credits for this model. Switch to a ':free' model like Gemini Flash (Free) in Pipeline Studio."
                    401 -> "Invalid OpenRouter API key. Check your key in Key Vault."
                    404 -> detail ?: "Model '${config.modelId}' not found on OpenRouter."
                    else -> detail ?: "HTTP ${response.status.value}: ${response.status.description}"
                }
                Log.e(TAG, "OpenRouter error: $friendlyMsg")
                channel.send(StreamEvent.Error(Exception(friendlyMsg)))
                return@channelFlow
            }

            val byteChannel = response.bodyAsChannel()
            var tokenCount = 0

            while (!byteChannel.isClosedForRead) {
                val line = byteChannel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty() || data == "[DONE]") continue

                    parseChunk(data)?.let { chunk ->
                        chunk.usage?.let { u ->
                            channel.send(StreamEvent.Usage(u.promptTokens, u.completionTokens))
                        }
                        chunk.choices?.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }?.let { token ->
                            tokenCount++
                            channel.send(StreamEvent.Token(token))
                        }
                    }
                } else if (line.isNotBlank()) {
                    parseNonStreamingResponse(line)?.let { fullText ->
                        tokenCount++
                        channel.send(StreamEvent.Token(fullText))
                    }
                }
            }

            Log.d(TAG, "OpenRouter stream complete, tokens=$tokenCount")
            channel.send(StreamEvent.Done)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter exception: ${e.message}", e)
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
            model       = config.modelId.ifBlank { "anthropic/claude-3.5-sonnet" },
            messages    = openAiMessages,
            temperature = config.temperature,
            maxTokens   = config.maxOutputTokens
        )
    }

    private fun parseChunk(data: String): OpenAiStreamChunk? = try {
        json.decodeFromString<OpenAiStreamChunk>(data)
    } catch (e: Exception) {
        null
    }

    private fun parseNonStreamingResponse(data: String): String? = try {
        Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}


package com.techrifter.fluxvoice.data.remote.provider.openai

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

@Singleton
class OpenAiProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiStreamingProvider {

    override val providerType: ProviderType = ProviderType.OPENAI_COMPAT
    override val capabilities = ProviderCapabilities(
        supportsStreaming       = true,
        supportsFunctionCalling = true,
        maxContextTokens        = 128_000
    )

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig,
        authConfig: AuthConfig
    ): Flow<StreamEvent> = channelFlow {
        val channel = this
        channel.send(StreamEvent.Start)

        try {
            val baseUrl = authConfig.baseUrl.trimEnd('/').takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            val request = buildRequest(messages, config)

            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                if (authConfig.apiKey.isNotBlank()) bearerAuth(authConfig.apiKey)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsChannel().let {
                    buildString { while (!it.isClosedForRead) append(it.readUTF8Line() ?: break) }
                }
                val detail = try {
                    json.parseToJsonElement(errorBody).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                } catch (e: Exception) { null }
                val msg = detail ?: "HTTP ${response.status.value}: ${response.status.description}"
                channel.send(StreamEvent.Error(Exception(msg)))
                return@channelFlow
            }

            val byteChannel = response.bodyAsChannel()
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
                            channel.send(StreamEvent.Token(token))
                        }
                    }
                    parseUsage(data)?.let { (p, c) ->
                        channel.send(StreamEvent.Usage(p, c))
                    }
                } else if (line.isNotBlank()) {
                    parseNonStreamingResponse(line)?.let { fullText ->
                        channel.send(StreamEvent.Token(fullText))
                    }
                }
            }
            channel.send(StreamEvent.Done)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            openAiMessages += OpenAiMessage(role = role, content = msg.content)
        }

        return OpenAiRequest(
            model = config.modelId.ifBlank { "gpt-4o-mini" },
            messages = openAiMessages,
            temperature = config.temperature,
            maxTokens = config.maxOutputTokens
        )
    }

    private fun parseChunk(data: String): OpenAiStreamChunk? = try {
        json.decodeFromString<OpenAiStreamChunk>(data)
    } catch (_: Exception) {
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

    private fun parseUsage(data: String): Pair<Int, Int>? = try {
        val usage = json.parseToJsonElement(data).jsonObject["usage"]?.jsonObject
        val prompt = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull()
        val completion = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull()
        if (prompt != null && completion != null) Pair(prompt, completion) else null
    } catch (_: Exception) {
        null
    }
}


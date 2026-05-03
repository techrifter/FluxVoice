package com.techrifter.fluxvoice.data.remote.provider.local

import com.techrifter.fluxvoice.domain.model.AuthConfig
import com.techrifter.fluxvoice.domain.model.GenerationConfig
import com.techrifter.fluxvoice.domain.model.Message
import com.techrifter.fluxvoice.domain.model.MessageRole
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.streaming.AiStreamingProvider
import com.techrifter.fluxvoice.domain.streaming.ProviderCapabilities
import com.techrifter.fluxvoice.domain.streaming.StreamEvent
import io.ktor.client.HttpClient
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ollama-compatible local LLM provider.
 * Targets the /api/chat endpoint which streams NDJSON (one JSON object per line).
 * Compatible with Ollama and any server implementing the same API.
 */
@Singleton
class LocalLlmProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) : AiStreamingProvider {

    override val providerType: ProviderType = ProviderType.LOCAL
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsFunctionCalling = false,
        maxContextTokens = 0
    )

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig,
        authConfig: AuthConfig
    ): Flow<StreamEvent> = channelFlow {
        val channel = this
        channel.send(StreamEvent.Start)

        try {
            val baseUrl = authConfig.baseUrl.takeIf { it.isNotBlank() }
                ?: "http://localhost:11434"
            val request = buildRequest(messages, config)

            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                channel.send(StreamEvent.Error(Exception("HTTP ${response.status.value}")))
                return@channelFlow
            }

            val byteChannel = response.bodyAsChannel()
            while (!byteChannel.isClosedForRead) {
                val line = byteChannel.readUTF8Line()?.trim() ?: break
                if (line.isEmpty()) continue
                parseToken(line)?.let { token -> channel.send(StreamEvent.Token(token)) }
            }
            channel.send(StreamEvent.Done)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            channel.send(StreamEvent.Error(e))
        }
    }

    private fun buildRequest(messages: List<Message>, config: GenerationConfig): OllamaRequest {
        val ollamaMessages = mutableListOf<OllamaMessage>()
        config.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            ollamaMessages += OllamaMessage("system", it)
        }
        messages.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            ollamaMessages += OllamaMessage(role, msg.content)
        }
        return OllamaRequest(model = config.modelId, messages = ollamaMessages)
    }

    private fun parseToken(line: String): String? = try {
        val chunk = json.decodeFromString<OllamaChunk>(line)
        chunk.message?.content?.takeIf { it.isNotEmpty() && chunk.done == false }
    } catch (_: Exception) {
        null
    }

    // ─── Ollama DTOs (local to this provider) ────────────────────────────────

    @Serializable
    private data class OllamaRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = true
    )

    @Serializable
    private data class OllamaMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class OllamaChunk(
        val model: String? = null,
        val message: OllamaMessage? = null,
        val done: Boolean? = null,
        @SerialName("done_reason") val doneReason: String? = null
    )
}


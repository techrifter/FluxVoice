package com.techrifter.fluxvoice.core

import com.techrifter.fluxvoice.core.model.GenerationConfig
import com.techrifter.fluxvoice.core.model.Message
import com.techrifter.fluxvoice.core.model.MessageRole
import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.core.state.VoiceState
import com.techrifter.fluxvoice.core.streaming.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxVoiceCoreTest {

    @Test
    fun `FluxVoiceConfig builder sets sensible defaults`() {
        val config = FluxVoiceConfig { }

        assertNull(config.sttProvider)
        assertNull(config.ttsProvider)
        assertNull(config.llmProvider)
        assertEquals("", config.modelId)
        assertEquals(FluxVoiceConfig.DEFAULT_SYSTEM_PROMPT, config.systemPrompt)
        assertEquals(10, config.maxContextTurns)
        assertTrue(config.vadEnabled)
        assertEquals(1_000, config.vadSensitivity)
        assertFalse(config.backchannelEnabled)
        assertEquals(1_500L, config.backchannelDelayMs)
        assertEquals(0.7f, config.temperature, 0.001f)
        assertEquals(2_048, config.maxOutputTokens)
        assertEquals(1.0f, config.speechRate, 0.001f)
        assertEquals(1.0f, config.pitch, 0.001f)
    }

    @Test
    fun `FluxVoiceConfig builder overrides customization parameters including speechRate and pitch`() {
        var transcriptReceived = ""
        var responseReceived = ""
        var metricsReceived: TurnMetrics? = null

        val config = FluxVoiceConfig {
            modelId = "custom-model-id"
            systemPrompt = "Custom voice persona"
            maxContextTurns = 12
            vadEnabled = false
            vadSensitivity = 600
            backchannelEnabled = true
            backchannelDelayMs = 2000L
            temperature = 0.4f
            maxOutputTokens = 1024
            speechRate = 1.25f
            pitch = 1.1f

            onTranscript { transcriptReceived = it }
            onResponse { responseReceived = it }
            onTurnMetrics { metricsReceived = it }
        }

        assertEquals("custom-model-id", config.modelId)
        assertEquals("Custom voice persona", config.systemPrompt)
        assertEquals(12, config.maxContextTurns)
        assertFalse(config.vadEnabled)
        assertEquals(600, config.vadSensitivity)
        assertTrue(config.backchannelEnabled)
        assertEquals(2000L, config.backchannelDelayMs)
        assertEquals(0.4f, config.temperature, 0.001f)
        assertEquals(1024, config.maxOutputTokens)
        assertEquals(1.25f, config.speechRate, 0.001f)
        assertEquals(1.1f, config.pitch, 0.001f)

        config.onTranscript?.invoke("Hello test")
        config.onResponse?.invoke("Response test")
        val sampleMetrics = TurnMetrics(sttLatencyMs = 120L)
        config.onTurnMetrics?.invoke(sampleMetrics)

        assertEquals("Hello test", transcriptReceived)
        assertEquals("Response test", responseReceived)
        assertEquals(120L, metricsReceived?.sttLatencyMs)
    }

    @Test
    fun `FluxVoiceConfig builder stt llm tts property aliases assign providers correctly`() {
        val dummyStt = object : com.techrifter.fluxvoice.core.stt.SttProvider {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<com.techrifter.fluxvoice.core.state.SttState>(com.techrifter.fluxvoice.core.state.SttState.Idle)
            override fun startListening() {}
            override fun stopListening() {}
            override fun destroy() {}
        }
        val dummyLlm = object : com.techrifter.fluxvoice.core.llm.LlmProvider {
            override fun streamChat(messages: List<Message>, config: GenerationConfig) = kotlinx.coroutines.flow.emptyFlow<StreamEvent>()
        }
        val dummyTts = object : com.techrifter.fluxvoice.core.tts.TtsProvider {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<com.techrifter.fluxvoice.core.state.TtsState>(com.techrifter.fluxvoice.core.state.TtsState.Idle)
            override fun speak(text: String, utteranceId: String) {}
            override fun stop() {}
            override fun shutdown() {}
        }

        val config = FluxVoiceConfig {
            stt = dummyStt
            llm = dummyLlm
            tts = dummyTts
        }

        assertEquals(dummyStt, config.sttProvider)
        assertEquals(dummyLlm, config.llmProvider)
        assertEquals(dummyTts, config.ttsProvider)
    }

    @Test
    fun `TurnMetrics calculates latencies and tokens accurately`() {
        val metrics = TurnMetrics(
            sttLatencyMs = 250L,
            timeToFirstTokenMs = 180L,
            timeToFirstAudioMs = 320L,
            totalTurnDurationMs = 1450L,
            bargeInLatencyMs = null,
            bargeInOccurred = false,
            inputTokens = 42,
            outputTokens = 18,
            isTokensEstimated = false,
            estimatedCostUsd = 0.00045
        )

        assertEquals(60, metrics.totalTokens)
        assertEquals(42, metrics.promptTokens)
        assertEquals(18, metrics.completionTokens)
        assertEquals(180L, metrics.ttftMs)
        assertEquals(320L, metrics.ttfaMs)
        assertEquals(1450L, metrics.totalTurnLatencyMs)
        assertFalse(metrics.bargeInOccurred)
        assertNull(metrics.bargeInLatencyMs)
    }

    @Test
    fun `TurnMetrics records bargeIn events correctly`() {
        val metrics = TurnMetrics(
            sttLatencyMs = 150L,
            timeToFirstTokenMs = 120L,
            bargeInLatencyMs = 95L,
            bargeInOccurred = true
        )

        assertTrue(metrics.bargeInOccurred)
        assertEquals(95L, metrics.bargeInLatencyMs)
    }

    @Test
    fun `Message model encapsulates roles correctly`() {
        val userMsg = Message(MessageRole.USER, "What is the weather?")
        val assistantMsg = Message(MessageRole.ASSISTANT, "It is sunny.")
        val systemMsg = Message(MessageRole.SYSTEM, "System prompt")

        assertEquals(MessageRole.USER, userMsg.role)
        assertEquals("What is the weather?", userMsg.content)
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
        assertEquals("It is sunny.", assistantMsg.content)
        assertEquals(MessageRole.SYSTEM, systemMsg.role)
    }

    @Test
    fun `StreamEvent types convey streaming tokens, usage and lifecycle`() {
        val start: StreamEvent = StreamEvent.Start
        val token: StreamEvent = StreamEvent.Token("hello")
        val usage: StreamEvent = StreamEvent.Usage(promptTokens = 10, completionTokens = 5)
        val done: StreamEvent = StreamEvent.Done
        val error: StreamEvent = StreamEvent.Error(IllegalStateException("Network failure"))

        assertTrue(start is StreamEvent.Start)
        assertEquals("hello", (token as StreamEvent.Token).text)
        assertEquals(10, (usage as StreamEvent.Usage).promptTokens)
        assertEquals(5, usage.completionTokens)
        assertTrue(done is StreamEvent.Done)
        assertEquals("Network failure", (error as StreamEvent.Error).cause.message)
    }

    @Test
    fun `GenerationConfig defaults maintain expected standard voice values`() {
        val defaultGen = GenerationConfig()
        assertEquals(0.7f, defaultGen.temperature, 0.001f)
        assertEquals(2048, defaultGen.maxOutputTokens)
        assertEquals("", defaultGen.systemPrompt)
        assertEquals("", defaultGen.modelId)

        val customGen = GenerationConfig(
            temperature = 0.2f,
            maxOutputTokens = 512,
            systemPrompt = "Direct instructions",
            modelId = "custom-llm-1"
        )
        assertEquals(0.2f, customGen.temperature, 0.001f)
        assertEquals(512, customGen.maxOutputTokens)
        assertEquals("Direct instructions", customGen.systemPrompt)
        assertEquals("custom-llm-1", customGen.modelId)
    }
}


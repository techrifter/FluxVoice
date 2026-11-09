package com.techrifter.fluxvoice

import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkEntity
import com.techrifter.fluxvoice.data.telemetry.CostCalculator
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.pipeline.PipelineExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTelemetryAndExporterTest {

    private val costCalculator = CostCalculator()

    @Test
    fun `calculateTurnCostUsd returns accurate cost for groq turn`() {
        // Groq GPT OSS 120B: $0.15 / 1M in, $0.60 / 1M out
        val cost = costCalculator.calculateTurnCostUsd(
            modelId = "openai/gpt-oss-120b",
            promptTokens = 1000,
            completionTokens = 500,
            responseCharCount = 200,
            audioDurationMs = 2000L
        )

        // input: 0.00015, output: 0.00030, tts: 200 * 0.000075 = 0.015, stt: 2 * 0.000072 = 0.000144
        assertTrue(cost > 0.0)
        assertTrue(cost < 0.10)
    }

    @Test
    fun `calculateTurnCostUsd handles zero tokens safely`() {
        val cost = costCalculator.calculateTurnCostUsd(
            modelId = "openai/gpt-oss-120b",
            promptTokens = 0,
            completionTokens = 0,
            responseCharCount = 0,
            audioDurationMs = 0L
        )
        assertEquals(0.0, cost, 0.000001)
    }

    @Test
    fun `pipelineExporter generates complete kotlin compose sdk block`() {
        val config = PipelineConfig(
            id = "test_pipe",
            name = "Test Ultra Pipeline",
            description = "High speed voice",
            isPreset = false,
            sttProviderId = "deepgram",
            sttModelId = "nova-3",
            sttEndpointingMs = 350,
            llmProviderId = "groq",
            llmModelId = "openai/gpt-oss-120b",
            temperature = 0.6f,
            systemPrompt = "Answer concisely.",
            ttsProviderId = "cartesia",
            ttsVoiceId = "voice-123",
            speechRate = 1.0f,
            vadEnabled = true,
            vadSensitivity = 800,
            backchannelEnabled = true
        )

        val code = PipelineExporter.exportToKotlinSdk(config)

        assertTrue(code.contains("rememberFluxVoice {"))
        assertTrue(code.contains("DeepgramSttProvider("))
        assertTrue(code.contains("OpenAiCompatibleLlmProvider("))
        assertTrue(code.contains("CartesiaTtsProvider("))
        assertTrue(code.contains("systemPrompt = \"Answer concisely.\""))
        assertTrue(code.contains("temperature = 0.6f"))
        assertTrue(code.contains("speechRate = 1.0f"))
        assertTrue(code.contains("vadEnabled = true"))
        assertTrue(code.contains("onTurnMetrics = {"))
    }

    @Test
    fun `pipelineExporter generates valid Gemini configuration using OpenAiCompatibleLlmProvider`() {
        val geminiConfig = PipelineConfig(
            id = "gemini_pipe",
            name = "Deep Reasoning",
            llmProviderId = "gemini",
            llmModelId = "gemini-2.5-flash"
        )

        val code = PipelineExporter.exportToKotlinSdk(geminiConfig)

        assertTrue(code.contains("OpenAiCompatibleLlmProvider("))
        assertTrue(code.contains("YOUR_GEMINI_KEY"))
        assertTrue(code.contains("gemini-2.5-flash"))
        assertTrue(code.contains("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
        // Confirmed phantom class is not emitted
        assertFalse(code.contains("GeminiLlmProvider"))
    }

    @Test
    fun `pipelineExporter generates valid Custom STT, LLM, and TTS configuration`() {
        val customConfig = PipelineConfig(
            id = "custom_pipe",
            name = "BYOK Pipeline",
            sttProviderId = "custom",
            sttModelId = "whisper-large-v3",
            llmProviderId = "custom",
            llmModelId = "meta-llama/llama-3.3-70b-instruct",
            customEndpointUrl = "https://api.together.xyz/v1",
            ttsProviderId = "custom",
            ttsVoiceId = "custom-voice-1"
        )

        val code = PipelineExporter.exportToKotlinSdk(customConfig)

        assertTrue(code.contains("CustomWhisperSttProvider("))
        assertTrue(code.contains("YOUR_CUSTOM_STT_KEY"))
        assertTrue(code.contains("whisper-large-v3"))
        assertTrue(code.contains("OpenAiCompatibleLlmProvider("))
        assertTrue(code.contains("https://api.together.xyz/v1/chat/completions"))
        assertTrue(code.contains("CustomAudioSpeechTtsProvider("))
        assertTrue(code.contains("custom-voice-1"))
    }

    @Test
    fun `pipelineExporter exports Groq Whisper Turbo STT`() {
        val groqSttConfig = PipelineConfig(
            id = "groq_stt_pipe",
            name = "Groq Turbo Stack",
            sttProviderId = "groq_whisper"
        )

        val code = PipelineExporter.exportToKotlinSdk(groqSttConfig)

        assertTrue(code.contains("GroqWhisperSttProvider("))
        assertTrue(code.contains("YOUR_GROQ_KEY"))
        assertTrue(code.contains("whisper-large-v3-turbo"))
    }

    @Test
    fun `pipelineExporter escapes user-provided Kotlin string values`() {
        val config = PipelineConfig(
            id = "escaped_pipe",
            name = "Pipeline\n// injected line",
            llmProviderId = "groq",
            llmModelId = "model\"\\\$value\nnext",
            systemPrompt = "Say \"hello\"\\path\nthen continue"
        )

        val code = PipelineExporter.exportToKotlinSdk(config)
        val expectedModelId = buildString {
            append("modelId = \"model")
            append('\\')
            append('"')
            append('\\')
            append('\\')
            append('\\')
            append('$')
            append("value\\nnext\"")
        }

        assertTrue(code.contains("// Pipeline: Pipeline // injected line"))
        assertTrue(code.contains(expectedModelId))
        assertTrue(code.contains("""systemPrompt = "Say \"hello\"\\path\nthen continue""""))
    }

    @Test
    fun `turnBenchmarkEntity retains architecture metadata for stt, llm, and tts`() {
        val entity = TurnBenchmarkEntity(
            pipelineId = "test_custom_id",
            pipelineName = "Personal Custom Stack",
            timestamp = System.currentTimeMillis(),
            sttLatencyMs = 120L,
            ttftMs = 210L,
            ttfaMs = 330L,
            totalTurnMs = 850L,
            promptTokens = 40,
            completionTokens = 25,
            totalTokens = 65,
            estimatedCostUsd = 0.0001,
            bargeInOccurred = true,
            userTranscriptionSnippet = "Hello world",
            assistantResponseSnippet = "Hi there, how can I help?",
            isTokensEstimated = false,
            sttProvider = "groq_whisper",
            llmModel = "openai/gpt-oss-120b",
            ttsProvider = "cartesia"
        )

        assertEquals("groq_whisper", entity.sttProvider)
        assertEquals("openai/gpt-oss-120b", entity.llmModel)
        assertEquals("cartesia", entity.ttsProvider)
        assertTrue(entity.bargeInOccurred)
        assertEquals(65, entity.totalTokens)
    }

    @Test
    fun `multiple custom stacks retain distinct ids and configuration`() {
        val stack1 = PipelineConfig(
            id = "custom_stack_1",
            name = "Personal Custom Stack 1",
            description = "First custom pipeline",
            isPreset = false,
            sttProviderId = "deepgram",
            llmProviderId = "groq",
            ttsProviderId = "cartesia"
        )

        val stack2 = PipelineConfig(
            id = "custom_stack_2",
            name = "Personal Custom Stack 2",
            description = "Second custom pipeline",
            isPreset = false,
            sttProviderId = "groq_whisper",
            llmProviderId = "gemini",
            ttsProviderId = "elevenlabs"
        )

        assertFalse(stack1.isPreset)
        assertFalse(stack2.isPreset)
        assertTrue(stack1.id != stack2.id)
        assertTrue(stack1.name != stack2.name)
        assertTrue(stack1.sttProviderId != stack2.sttProviderId)
        assertTrue(stack1.llmProviderId != stack2.llmProviderId)
        assertTrue(stack1.ttsProviderId != stack2.ttsProviderId)
    }
}

package com.techrifter.fluxvoice.provider.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiCompatibleLlmProviderTest {

    @Test
    fun `default constructor configures openai-gpt-oss-120b and groq base url`() {
        val provider = OpenAiCompatibleLlmProvider(apiKey = "gsk_test_key")

        assertEquals("openai/gpt-oss-120b", provider.getModelIdForTesting())
        assertEquals("https://api.groq.com/openai/v1/chat/completions", provider.getBaseUrlForTesting())
    }

    @Test
    fun `custom parameters override model and baseUrl`() {
        val provider = OpenAiCompatibleLlmProvider(
            apiKey = "sk-custom",
            modelId = "gpt-4o-mini",
            baseUrl = "https://api.openai.com/v1/chat/completions"
        )

        assertEquals("gpt-4o-mini", provider.getModelIdForTesting())
        assertEquals("https://api.openai.com/v1/chat/completions", provider.getBaseUrlForTesting())
    }

    @Test
    fun `normalizeEndpoint appends chat completions to bare v1 and bare host`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleLlmProvider.normalizeEndpoint("https://api.openai.com/v1")
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            OpenAiCompatibleLlmProvider.normalizeEndpoint("https://openrouter.ai/api/v1/")
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleLlmProvider.normalizeEndpoint("http://localhost:11434")
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleLlmProvider.normalizeEndpoint("http://localhost:11434/v1")
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            OpenAiCompatibleLlmProvider.normalizeEndpoint("")
        )
    }

    @Test
    fun `parseToken extracts streaming delta tokens accurately`() {
        val provider = OpenAiCompatibleLlmProvider(apiKey = "test")

        val sseChunk = """{"choices":[{"delta":{"content":"Hello world"},"index":0}]}"""
        val token = provider.parseToken(sseChunk)
        assertEquals("Hello world", token)

        val emptyDelta = """{"choices":[{"delta":{},"index":0}]}"""
        assertNull(provider.parseToken(emptyDelta))

        val doneChunk = "[DONE]"
        assertNull(provider.parseToken(doneChunk))

        val invalidJson = "invalid payload"
        assertNull(provider.parseToken(invalidJson))
    }

    @Test
    fun `parseUsage extracts token telemetry counts`() {
        val provider = OpenAiCompatibleLlmProvider(apiKey = "test")

        val usageChunk = """{"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":45,"total_tokens":165}}"""
        val usage = provider.parseUsage(usageChunk)

        assertNotNull(usage)
        assertEquals(120, usage?.first)
        assertEquals(45, usage?.second)

        val chunkWithoutUsage = """{"choices":[{"delta":{"content":"token"}}]}"""
        assertNull(provider.parseUsage(chunkWithoutUsage))
    }

    @Test
    fun `parseNonStreamingResponse extracts message content fallback`() {
        val provider = OpenAiCompatibleLlmProvider(apiKey = "test")

        val fullResponse = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "This is a non-streaming completed answer."
                        }
                    }
                ]
            }
        """.trimIndent()

        val parsed = provider.parseNonStreamingResponse(fullResponse)
        assertEquals("This is a non-streaming completed answer.", parsed)
    }
}


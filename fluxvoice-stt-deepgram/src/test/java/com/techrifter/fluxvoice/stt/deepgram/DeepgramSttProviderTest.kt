package com.techrifter.fluxvoice.stt.deepgram

import com.techrifter.fluxvoice.core.state.SttState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepgramSttProviderTest {

    private lateinit var provider: DeepgramSttProvider

    @Before
    fun setUp() {
        provider = DeepgramSttProvider(apiKey = "test_deepgram_key", model = "nova-3")
        provider.setSessionIdForTesting(100L)
        provider.resetForTesting()
    }

    @Test
    fun `handleMessage emits PartialResult on interim non-final chunk`() {
        val interimJson = """
            {
                "type": "Results",
                "is_final": false,
                "speech_final": false,
                "channel": {
                    "alternatives": [
                        { "transcript": "What is the weather" }
                    ]
                }
            }
        """.trimIndent()

        provider.handleMessage(interimJson, myId = 100L)

        val state = provider.state.value
        assertTrue("Expected PartialResult but got $state", state is SttState.PartialResult)
        assertEquals("What is the weather", (state as SttState.PartialResult).text)
    }

    @Test
    fun `handleMessage accumulates multi-clause is_final segments and emits full transcript on speech_final`() {
        val clause1 = """
            {
                "type": "Results",
                "is_final": true,
                "speech_final": false,
                "channel": {
                    "alternatives": [
                        { "transcript": "I would like to order a pizza" }
                    ]
                }
            }
        """.trimIndent()

        val clause2 = """
            {
                "type": "Results",
                "is_final": true,
                "speech_final": false,
                "channel": {
                    "alternatives": [
                        { "transcript": "with extra cheese and olives" }
                    ]
                }
            }
        """.trimIndent()

        val endpointing = """
            {
                "type": "Results",
                "is_final": true,
                "speech_final": true,
                "channel": {
                    "alternatives": [
                        { "transcript": "" }
                    ]
                }
            }
        """.trimIndent()

        provider.handleMessage(clause1, myId = 100L)
        provider.handleMessage(clause2, myId = 100L)
        provider.handleMessage(endpointing, myId = 100L)

        val state = provider.state.value
        assertTrue("Expected FinalResult but got $state", state is SttState.FinalResult)
        assertEquals(
            "I would like to order a pizza with extra cheese and olives",
            (state as SttState.FinalResult).text
        )
    }

    @Test
    fun `handleMessage emits accumulated transcript on UtteranceEnd fallback`() {
        val clause = """
            {
                "type": "Results",
                "is_final": true,
                "speech_final": false,
                "channel": {
                    "alternatives": [
                        { "transcript": "Turn on the living room lights" }
                    ]
                }
            }
        """.trimIndent()

        val utteranceEnd = """{ "type": "UtteranceEnd" }"""

        provider.handleMessage(clause, myId = 100L)
        provider.handleMessage(utteranceEnd, myId = 100L)

        val state = provider.state.value
        assertTrue("Expected FinalResult but got $state", state is SttState.FinalResult)
        assertEquals("Turn on the living room lights", (state as SttState.FinalResult).text)
    }

    @Test
    fun `handleMessage discards messages with mismatched sessionId`() {
        val oldSessionJson = """
            {
                "type": "Results",
                "is_final": true,
                "speech_final": true,
                "channel": {
                    "alternatives": [
                        { "transcript": "Stale message from aborted session" }
                    ]
                }
            }
        """.trimIndent()

        // Message has myId = 99L, but current sessionId = 100L
        provider.handleMessage(oldSessionJson, myId = 99L)

        val state = provider.state.value
        assertTrue("State should remain Idle on stale session message but got $state", state is SttState.Idle)
    }

    @Test
    fun `constructor accepts custom endpointingMs parameter`() {
        val customProvider = DeepgramSttProvider(
            apiKey = "test_key",
            model = "nova-3",
            endpointingMs = 350
        )
        // Verified constructor accepts endpointingMs without throwing
        assertTrue(customProvider.state.value is SttState.Idle)
    }
}


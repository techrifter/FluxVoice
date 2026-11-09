package com.techrifter.fluxvoice.tts.cartesia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CartesiaTtsProviderTest {

    private lateinit var provider: CartesiaTtsProvider

    @Before
    fun setUp() {
        provider = CartesiaTtsProvider(apiKey = "test_cartesia_key")
    }

    @Test
    fun `setSpeechRate clamps speed within 0_5 to 2_0`() {
        provider.setSpeechRate(1.5f)
        assertEquals(1.5f, provider.getSpeechRateForTesting(), 0.001f)

        // Below minimum
        provider.setSpeechRate(0.2f)
        assertEquals(0.5f, provider.getSpeechRateForTesting(), 0.001f)

        // Above maximum
        provider.setSpeechRate(3.5f)
        assertEquals(2.0f, provider.getSpeechRateForTesting(), 0.001f)
    }

    @Test
    fun `setPitch clamps pitch within 0_5 to 2_0`() {
        provider.setPitch(1.2f)
        assertEquals(1.2f, provider.getPitchForTesting(), 0.001f)

        // Below minimum
        provider.setPitch(0.1f)
        assertEquals(0.5f, provider.getPitchForTesting(), 0.001f)

        // Above maximum
        provider.setPitch(2.8f)
        assertEquals(2.0f, provider.getPitchForTesting(), 0.001f)
    }

    @Test
    fun `findDataOffset locates data chunk in RIFF header`() {
        // Construct a 44-byte WAV header followed by PCM payload
        val wavHeader = ByteArray(44 + 64)
        "RIFF".toByteArray().copyInto(wavHeader, 0)
        "WAVE".toByteArray().copyInto(wavHeader, 8)
        "fmt ".toByteArray().copyInto(wavHeader, 12)
        // audio format PCM = 1 at offset 20
        wavHeader[20] = 1
        // channels = 1 (mono) at offset 22
        wavHeader[22] = 1
        // "data" chunk at offset 36
        "data".toByteArray().copyInto(wavHeader, 36)
        // data size at 40..43

        val offset = provider.findDataOffset(wavHeader)
        assertEquals(44, offset)
    }

    @Test
    fun `findDataOffset returns -1 on corrupt or incomplete WAV`() {
        val corruptBytes = "RIFF....WAVEfmt ........".toByteArray()
        val offset = provider.findDataOffset(corruptBytes)
        assertEquals(-1, offset)
    }

    @Test
    fun `readShortLE parses channels little-endian format`() {
        val bufMono = byteArrayOf(0, 0, 1, 0)
        assertEquals(1, provider.readShortLE(bufMono, 2))

        val bufStereo = byteArrayOf(0, 0, 2, 0)
        assertEquals(2, provider.readShortLE(bufStereo, 2))

        // Out of bounds safety fallback
        assertEquals(1, provider.readShortLE(bufStereo, 10))
    }

    @Test
    fun `constructor initializes custom speechRate and pitch`() {
        val custom = CartesiaTtsProvider(
            apiKey = "test_key",
            voiceId = "test_voice",
            speechRate = 1.35f,
            pitch = 1.15f
        )
        assertEquals(1.35f, custom.getSpeechRateForTesting(), 0.001f)
        assertEquals(1.15f, custom.getPitchForTesting(), 0.001f)
    }
}


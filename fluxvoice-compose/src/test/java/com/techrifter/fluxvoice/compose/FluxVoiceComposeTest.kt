package com.techrifter.fluxvoice.compose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxVoiceComposeTest {

    @Test
    fun `FluxVoiceMode definitions contain non-empty prompts and distinct display metadata`() {
        val modes = FluxVoiceMode.values()
        assertEquals(3, modes.size)

        for (mode in modes) {
            assertTrue("Mode ${mode.name} display name should not be blank", mode.displayName.isNotBlank())
            assertTrue("Mode ${mode.name} emoji should not be blank", mode.emoji.isNotBlank())
            assertTrue("Mode ${mode.name} tagline should not be blank", mode.tagline.isNotBlank())
            assertTrue("Mode ${mode.name} defaultPrompt should not be blank", mode.defaultPrompt.isNotBlank())
        }

        assertEquals("Fast", FluxVoiceMode.FAST.displayName)
        assertEquals("⚡", FluxVoiceMode.FAST.emoji)
        assertTrue(FluxVoiceMode.FAST.defaultPrompt.contains("concise"))

        assertEquals("Thinking", FluxVoiceMode.THINKING.displayName)
        assertEquals("🧠", FluxVoiceMode.THINKING.emoji)
        assertTrue(FluxVoiceMode.THINKING.defaultPrompt.contains("Reason carefully"))

        assertEquals("Custom", FluxVoiceMode.CUSTOM.displayName)
        assertEquals("✨", FluxVoiceMode.CUSTOM.emoji)
    }

    @Test
    fun `FluxVoiceColors default constructor initializes color scheme properties`() {
        val colors = FluxVoiceColors()

        assertEquals(Color(0xFF64748B), colors.idle)
        assertEquals(Color(0xFF3B82F6), colors.listening)
        assertEquals(Color(0xFF8B5CF6), colors.thinking)
        assertEquals(Color(0xFF10B981), colors.speaking)
        assertEquals(Color(0xFFEF4444), colors.interrupting)

        val custom = colors.copy(
            listening = Color(0xFF00B4D8),
            speaking = Color(0xFF10B981)
        )
        assertEquals(Color(0xFF00B4D8), custom.listening)
        assertEquals(Color(0xFF10B981), custom.speaking)
        assertEquals(Color(0xFF64748B), custom.idle)
    }
}


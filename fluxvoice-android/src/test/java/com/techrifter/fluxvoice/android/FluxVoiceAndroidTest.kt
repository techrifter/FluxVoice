package com.techrifter.fluxvoice.android

import com.techrifter.fluxvoice.android.internal.ConversationManager
import com.techrifter.fluxvoice.android.internal.SentenceExtractor
import com.techrifter.fluxvoice.core.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxVoiceAndroidTest {

    @Test
    fun `SentenceExtractor splits complete sentences and keeps unconsumed text`() {
        val buffer = StringBuilder()
        buffer.append("Hello world! How are you doing today? I am fine")

        val extracted = SentenceExtractor.extract(buffer)
        assertEquals(2, extracted.size)
        assertEquals("Hello world!", extracted[0])
        assertEquals("How are you doing today?", extracted[1])
        assertEquals("I am fine", buffer.toString().trim())
    }

    @Test
    fun `SentenceExtractor handles streaming token append batches`() {
        val buffer = StringBuilder()

        // Batch 1: Incomplete sentence
        buffer.append("The quick brown ")
        var sentences = SentenceExtractor.extract(buffer)
        assertTrue(sentences.isEmpty())
        assertEquals("The quick brown ", buffer.toString())

        // Batch 2: Finishes sentence and starts next
        buffer.append("fox jumps over the lazy dog. Next sentence is here.")
        sentences = SentenceExtractor.extract(buffer)
        assertEquals(1, sentences.size)
        assertEquals("The quick brown fox jumps over the lazy dog.", sentences[0])
        assertEquals("Next sentence is here.", buffer.toString())

        // Batch 3: Trailing token makes it a boundary
        buffer.append(" And another.")
        sentences = SentenceExtractor.extract(buffer)
        assertEquals(1, sentences.size)
        assertEquals("Next sentence is here.", sentences[0])
        assertEquals("And another.", buffer.toString().trim())
    }

    @Test
    fun `SentenceExtractor does not split on numbered lists`() {
        val buffer = StringBuilder()
        buffer.append("1. First item is apples. Then second item is oranges.")

        val extracted = SentenceExtractor.extract(buffer)
        assertEquals(1, extracted.size)
        assertEquals("1. First item is apples.", extracted[0])
        assertEquals("Then second item is oranges.", buffer.toString().trim())
    }

    @Test
    fun `ConversationManager bounds history to maxTurns`() {
        val manager = ConversationManager(maxTurns = 3) // limit = 3 * 2 = 6 messages

        // Add 4 user-assistant pairs (8 messages)
        for (i in 1..4) {
            manager.addUserMessage("User question $i")
            manager.addAssistantMessage("Assistant answer $i")
        }

        val messages = manager.getMessages()
        assertEquals(6, messages.size)

        // Turn 1 should have been pruned out (User 1 and Assistant 1)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("User question 2", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("Assistant answer 2", messages[1].content)

        assertEquals("User question 4", messages[4].content)
        assertEquals("Assistant answer 4", messages[5].content)
    }

    @Test
    fun `ConversationManager deduplicates repeated user message on LLM retry`() {
        val manager = ConversationManager(maxTurns = 5)
        manager.addUserMessage("What is the weather today?")
        // Simulating LLM retry attempt 2 calling addUserMessage again with identical input
        manager.addUserMessage("What is the weather today?")

        val messages = manager.getMessages()
        assertEquals(1, messages.size)
        assertEquals("What is the weather today?", messages[0].content)
        assertEquals(MessageRole.USER, messages[0].role)
    }

    @Test
    fun `ConversationManager clear empties all conversation history`() {
        val manager = ConversationManager(maxTurns = 5)
        manager.addUserMessage("Hello")
        manager.addAssistantMessage("Hi there")
        assertEquals(2, manager.getMessages().size)

        manager.clear()
        assertTrue(manager.getMessages().isEmpty())
    }
}


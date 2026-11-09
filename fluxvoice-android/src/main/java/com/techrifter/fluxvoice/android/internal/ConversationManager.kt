package com.techrifter.fluxvoice.android.internal

import com.techrifter.fluxvoice.core.model.Message
import com.techrifter.fluxvoice.core.model.MessageRole

/**
 * In-memory bounded conversation history.
 *
 * Maintains a sliding window of [maxTurns] user+assistant turn pairs.
 * Old turns are dropped from the front when the limit is exceeded so the
 * LLM always receives the most recent context.
 */
internal class ConversationManager(private val maxTurns: Int = 10) {

    private val history = ArrayDeque<Message>()

    @Synchronized
    fun addUserMessage(text: String) {
        val last = history.lastOrNull()
        if (last == null || last.role != MessageRole.USER || last.content != text) {
            history.addLast(Message(role = MessageRole.USER, content = text))
            trimToLimit()
        }
    }

    @Synchronized
    fun addAssistantMessage(text: String) {
        history.addLast(Message(role = MessageRole.ASSISTANT, content = text))
        trimToLimit()
    }

    @Synchronized
    fun getMessages(): List<Message> = history.toList()

    @Synchronized
    fun clear() = history.clear()

    private fun trimToLimit() {
        val limit = maxTurns * 2  // each turn = 1 user + 1 assistant message
        while (history.size > limit) history.removeFirst()
    }
}

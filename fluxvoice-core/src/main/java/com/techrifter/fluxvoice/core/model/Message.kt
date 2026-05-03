package com.techrifter.fluxvoice.core.model

import java.util.UUID

/**
 * A single conversation turn passed to LLM providers as context.
 * Roles: USER = human speech, ASSISTANT = AI reply, SYSTEM = injected instruction.
 */
public data class Message(
    val role: MessageRole,
    val content: String,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

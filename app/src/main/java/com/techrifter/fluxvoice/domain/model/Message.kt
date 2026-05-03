package com.techrifter.fluxvoice.domain.model

/**
 * Immutable domain model for a single conversation turn.
 * Passed to AI providers as context via [AiStreamingProvider.streamChat].
 * Roles: USER = human speech, ASSISTANT = AI response, SYSTEM = injected instruction.
 */
data class Message(
    val role: MessageRole,
    val content: String,
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

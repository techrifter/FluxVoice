package com.techrifter.fluxvoice.domain.model

enum class AppMode(
    val displayName: String,
    val emoji: String,
    val tagline: String
) {
    FAST("Fast", "⚡", "Speed-first"),
    THINKING("Thinking", "🧠", "Quality-first"),
    CUSTOM("Custom", "🔌", "Full control")
}

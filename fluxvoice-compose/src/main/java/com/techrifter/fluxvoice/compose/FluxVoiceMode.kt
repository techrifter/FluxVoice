package com.techrifter.fluxvoice.compose

/**
 * Conversation mode — controls the mode badge appearance and exposes a [defaultPrompt]
 * you can pass straight to [rememberFluxVoice] as the `systemPrompt`.
 *
 * ```kotlin
 * var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }
 *
 * val controller = rememberFluxVoice(key1 = mode) {
 *     systemPrompt = mode.defaultPrompt
 *     sttProvider  = DeepgramSttProvider(apiKey = "…")
 *     llmProvider  = GroqLlmProvider(apiKey = "…")
 *     ttsProvider  = CartesiaTtsProvider(apiKey = "…")
 * }
 *
 * FluxVoiceScreen(
 *     controller    = controller,
 *     initialMode   = mode,
 *     onModeChange  = { mode = it },
 *     onSettingsClick = { navController.navigate("settings") }
 * )
 * ```
 */
public enum class FluxVoiceMode(
    public val displayName: String,
    public val emoji: String,
    public val tagline: String,
    public val defaultPrompt: String,
) {
    FAST(
        displayName   = "Fast",
        emoji         = "⚡",
        tagline       = "Instant one-shot answers",
        defaultPrompt = "You are a helpful voice assistant. Keep responses concise and conversational."
    ),
    THINKING(
        displayName   = "Thinking",
        emoji         = "🧠",
        tagline       = "Careful, reasoned responses",
        defaultPrompt = "You are a thoughtful voice assistant. Reason carefully before responding. Structure your answer clearly but keep it conversational and concise."
    ),
    CUSTOM(
        displayName   = "Custom",
        emoji         = "✨",
        tagline       = "Your own configuration",
        defaultPrompt = "You are a helpful voice assistant. Keep responses concise. Be friendly and conversational."
    ),
}

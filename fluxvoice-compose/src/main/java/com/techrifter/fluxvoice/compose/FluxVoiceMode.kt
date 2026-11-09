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
 *     llmProvider  = OpenAiCompatibleLlmProvider(apiKey = "…")
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
        defaultPrompt = "You are Flux Fast, an ultra-low-latency voice assistant. Deliver immediate, concise, high-signal answers in 1 to 2 punchy sentences. Never use preamble, pleasantries, or filler."
    ),
    THINKING(
        displayName   = "Thinking",
        emoji         = "🧠",
        tagline       = "Careful, reasoned responses",
        defaultPrompt = "You are Flux Reason, an expert voice advisor for deep reasoning and system architecture. Reason carefully with first-principles depth, then articulate your answer in clear, flowing spoken paragraphs without bullet points or outlines."
    ),
    CUSTOM(
        displayName   = "Custom",
        emoji         = "✨",
        tagline       = "Your own configuration",
        defaultPrompt = "You are Flux, an intelligent voice AI assistant for real-time conversation. Deliver high-signal, direct answers optimized for spoken audio without markdown, lists, or filler."
    ),
}

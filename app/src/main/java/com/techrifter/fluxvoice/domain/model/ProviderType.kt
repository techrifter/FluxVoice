package com.techrifter.fluxvoice.domain.model

/**
 * Supported AI provider backends.
 * Add new entries here to extend with a new provider: then wire in ProviderModule.
 */
enum class ProviderType(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-2.5-flash"),
    GROQ("Groq", "openai/gpt-oss-120b"),
    OPENAI_COMPAT("OpenAI / Compatible", "gpt-4o-mini"),
    OPENROUTER("OpenRouter", "meta-llama/llama-3.3-70b-instruct"),
    LOCAL("Local LLM (Ollama)", "llama3.2")
}


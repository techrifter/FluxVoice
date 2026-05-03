package com.techrifter.fluxvoice.domain.model

/**
 * Supported AI provider backends.
 * Add new entries here to extend with a new provider — then wire in ProviderModule.
 */
enum class ProviderType(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-2.0-flash"),
    GROQ("Groq", "llama-3.3-70b-versatile"),
    OPENAI_COMPAT("OpenAI / Compatible", "gpt-4o"),
    LOCAL("Local LLM (Ollama)", "llama3.2")
}


package com.techrifter.fluxvoice.data.telemetry

data class LlmPricing(
    val inputCostPerMillion: Double,
    val outputCostPerMillion: Double
)

object ModelPricingCatalog {

    /**
     * Map of modelId lowercase substrings to pricing per million tokens in USD.
     */
    private val PRICING_MAP = mapOf(
        // Groq Active Models
        "gpt-oss-120b" to LlmPricing(0.15, 0.60),
        "gpt-oss-20b" to LlmPricing(0.075, 0.30),
        "qwen3.8-27b" to LlmPricing(0.80, 4.00),
        "allam-2-7b" to LlmPricing(0.10, 0.15),

        // OpenRouter / Meta / DeepSeek / Anthropic
        "llama-3.3-70b" to LlmPricing(0.59, 0.79),
        "deepseek-r1" to LlmPricing(0.55, 2.19),
        "deepseek-chat" to LlmPricing(0.14, 0.28),
        "claude-3.5-sonnet" to LlmPricing(3.00, 15.00),
        "claude-3.5-haiku" to LlmPricing(0.80, 4.00),

        // OpenAI (Direct or via OpenRouter)
        "gpt-4o" to LlmPricing(2.50, 10.00),
        "gpt-4o-mini" to LlmPricing(0.15, 0.60),

        // Google Gemini
        "gemini-2.5-flash" to LlmPricing(0.10, 0.40),
        "gemini-2.0-flash" to LlmPricing(0.10, 0.40),
        "gemini-1.5-pro" to LlmPricing(1.25, 5.00),
        "gemini-1.5-flash" to LlmPricing(0.075, 0.30)
    )

    fun getLlmPricing(modelId: String): LlmPricing {
        val normalized = modelId.lowercase()
        for ((key, pricing) in PRICING_MAP) {
            if (normalized.contains(key)) return pricing
        }
        // Default conservative fallback
        return LlmPricing(inputCostPerMillion = 0.50, outputCostPerMillion = 1.00)
    }

    /** Cartesia TTS cost: ~$0.075 per 1,000 characters */
    const val CARTESIA_COST_PER_CHAR = 0.000075

    /** ElevenLabs Flash cost: ~$0.15 per 1,000 characters */
    const val ELEVENLABS_COST_PER_CHAR = 0.00015

    /** OpenAI TTS-1 cost: ~$0.015 per 1,000 characters */
    const val OPENAI_TTS_COST_PER_CHAR = 0.000015

    /** Deepgram Aura cost: ~$0.015 per 1,000 characters */
    const val DEEPGRAM_AURA_COST_PER_CHAR = 0.000015

    /** Deepgram Nova-3 cost: ~$0.0043 per minute (~$0.000072 per sec) */
    const val DEEPGRAM_COST_PER_SECOND = 0.000072

    /** Groq Whisper cost: ~$0.00005 per second */
    const val GROQ_WHISPER_COST_PER_SECOND = 0.00005
}


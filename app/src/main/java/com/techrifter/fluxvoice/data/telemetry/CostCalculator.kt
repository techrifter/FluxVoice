package com.techrifter.fluxvoice.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CostCalculator @Inject constructor() {

    fun calculateTurnCostUsd(
        modelId: String,
        promptTokens: Int,
        completionTokens: Int,
        responseCharCount: Int = 0,
        audioDurationMs: Long = 0L,
        ttsProviderId: String = "cartesia"
    ): Double {
        val pricing = ModelPricingCatalog.getLlmPricing(modelId)

        val inputCost = (promptTokens / 1_000_000.0) * pricing.inputCostPerMillion
        val outputCost = (completionTokens / 1_000_000.0) * pricing.outputCostPerMillion

        val ttsRate = when (ttsProviderId.lowercase()) {
            "elevenlabs" -> ModelPricingCatalog.ELEVENLABS_COST_PER_CHAR
            "openai" -> ModelPricingCatalog.OPENAI_TTS_COST_PER_CHAR
            "deepgram", "deepgram_aura" -> ModelPricingCatalog.DEEPGRAM_AURA_COST_PER_CHAR
            "android" -> 0.0
            "custom" -> 0.0
            else -> ModelPricingCatalog.CARTESIA_COST_PER_CHAR
        }
        val ttsCost = responseCharCount * ttsRate
        val sttCost = (audioDurationMs / 1000.0) * ModelPricingCatalog.DEEPGRAM_COST_PER_SECOND

        return inputCost + outputCost + ttsCost + sttCost
    }
}


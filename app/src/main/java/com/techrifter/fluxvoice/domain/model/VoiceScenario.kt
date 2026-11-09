package com.techrifter.fluxvoice.domain.model

import kotlinx.serialization.Serializable

/**
 * Dynamic conversational starter prompt for the playground.
 * Dynamically generated via time-of-day context or real-time LLM curation.
 */
@Serializable
data class VoiceScenario(
    val icon: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val category: String = "Trending"
)


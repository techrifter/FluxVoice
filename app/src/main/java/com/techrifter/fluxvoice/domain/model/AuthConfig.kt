package com.techrifter.fluxvoice.domain.model

/**
 * Auth credentials passed to a provider at call time.
 * Keys are never stored in provider instances — they come from SettingsRepository per-request.
 */
data class AuthConfig(
    val apiKey: String = "",
    val baseUrl: String = ""
)


package com.techrifter.fluxvoice.ui.screen.settings

import com.techrifter.fluxvoice.domain.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaving: Boolean = false,
    val savedMessage: String? = null
)


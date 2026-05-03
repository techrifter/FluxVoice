package com.techrifter.fluxvoice.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _savedMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.getSettings(),
        _isSaving,
        _savedMessage
    ) { settings, isSaving, savedMessage ->
        SettingsUiState(settings = settings, isSaving = isSaving, savedMessage = savedMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    // ── App Mode ──────────────────────────────────────────────────────────────
    fun updateAppMode(mode: AppMode) = viewModelScope.launch {
        settingsRepository.updateAppMode(mode.name)
        when (mode) {
            AppMode.FAST     -> settingsRepository.applyFastPreset()
            AppMode.THINKING -> settingsRepository.applyThinkingPreset()
            AppMode.CUSTOM   -> settingsRepository.applyCustomPreset()
        }
    }

    // ── Voice Mode ────────────────────────────────────────────────────────────
    fun updateVoiceMode(value: String)       = viewModelScope.launch { settingsRepository.updateVoiceMode(value) }

    // ── STT Provider ──────────────────────────────────────────────────────────
    fun updateSttProvider(value: String)     = viewModelScope.launch { settingsRepository.updateSttProvider(value) }

    // ── AI Provider ───────────────────────────────────────────────────────────
    /**
     * Updates the active AI provider and automatically resets the model ID to the
     * provider's default so a stale model from a previous provider never causes a 404.
     */
    fun updateProvider(value: String) = viewModelScope.launch {
        settingsRepository.updateProviderType(value)
        // Reset model to the new provider's default to avoid cross-provider model mismatches
        val defaultModel = ProviderType.entries.firstOrNull { it.name == value }?.defaultModel
        if (defaultModel != null) settingsRepository.updateModelId(defaultModel)
    }
    fun updateGeminiApiKey(value: String) = viewModelScope.launch { settingsRepository.updateGeminiApiKey(value) }
    fun updateOpenAiApiKey(value: String) = viewModelScope.launch { settingsRepository.updateOpenAiApiKey(value) }
    fun updateGroqApiKey(value: String)   = viewModelScope.launch { settingsRepository.updateGroqApiKey(value) }
    fun updateLocalLlmUrl(value: String)  = viewModelScope.launch { settingsRepository.updateLocalLlmUrl(value) }
    fun updateModelId(value: String)      = viewModelScope.launch { settingsRepository.updateModelId(value) }
    fun updateTemperature(value: Float)   = viewModelScope.launch { settingsRepository.updateTemperature(value) }
    fun updateSystemPrompt(value: String) = viewModelScope.launch { settingsRepository.updateSystemPrompt(value) }

    // ── ElevenLabs ────────────────────────────────────────────────────────────
    fun updateElevenLabsAgentId(value: String) = viewModelScope.launch { settingsRepository.updateElevenLabsAgentId(value) }

    // ── Deepgram ──────────────────────────────────────────────────────────────
    fun updateDeepgramApiKey(value: String)  = viewModelScope.launch { settingsRepository.updateDeepgramApiKey(value) }

    // ── Voice Conversation ────────────────────────────────────────────────────
    fun updateMaxContextTurns(value: Int) = viewModelScope.launch { settingsRepository.updateMaxContextTurns(value) }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    fun updateTtsEnabled(value: Boolean)    = viewModelScope.launch { settingsRepository.updateTtsEnabled(value) }
    fun updateTtsSpeechRate(value: Float)   = viewModelScope.launch { settingsRepository.updateTtsSpeechRate(value) }
    fun updateTtsPitch(value: Float)        = viewModelScope.launch { settingsRepository.updateTtsPitch(value) }

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    fun updateCartesiaApiKey(value: String)  = viewModelScope.launch { settingsRepository.updateCartesiaApiKey(value) }
    fun updateCartesiaVoiceId(value: String) = viewModelScope.launch { settingsRepository.updateCartesiaVoiceId(value) }

    // ── Conversational UX ─────────────────────────────────────────────────────
    fun updateVadEnabled(value: Boolean)         = viewModelScope.launch { settingsRepository.updateVadEnabled(value) }
    fun updateVadSensitivity(value: Int)         = viewModelScope.launch { settingsRepository.updateVadSensitivity(value) }
    fun updateBackchannelEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.updateBackchannelEnabled(value) }

    fun dismissSavedMessage() { _savedMessage.value = null }
}

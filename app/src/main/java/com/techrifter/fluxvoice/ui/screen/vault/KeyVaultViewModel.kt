package com.techrifter.fluxvoice.ui.screen.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.security.KeyValidationStatus
import com.techrifter.fluxvoice.data.security.KeyValidator
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyVaultUiState(
    val cartesiaKey: String = "",
    val deepgramKey: String = "",
    val elevenLabsKey: String = "",
    val geminiKey: String = "",
    val groqKey: String = "",
    val openAiKey: String = "",
    val openRouterKey: String = "",
    val validationStatus: Map<String, KeyValidationStatus> = emptyMap()
)

@HiltViewModel
class KeyVaultViewModel @Inject constructor(
    private val keyVault: KeyVault,
    private val keyValidator: KeyValidator,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyVaultUiState())
    val uiState: StateFlow<KeyVaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                _uiState.update { current ->
                    current.copy(
                        cartesiaKey   = keyVault.getCartesiaKey().ifBlank { s.cartesiaApiKey },
                        deepgramKey   = keyVault.getDeepgramKey().ifBlank { s.deepgramApiKey },
                        elevenLabsKey = keyVault.getElevenLabsKey().ifBlank { s.elevenLabsApiKey },
                        geminiKey     = keyVault.getGeminiKey().ifBlank { s.geminiApiKey },
                        groqKey       = keyVault.getGroqKey().ifBlank { s.groqApiKey },
                        openAiKey     = keyVault.getOpenAiKey().ifBlank { s.openAiApiKey },
                        openRouterKey = keyVault.getOpenRouterKey().ifBlank { s.openRouterApiKey }
                    )
                }
            }
        }
    }

    fun updateKey(providerId: String, key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            when (providerId.lowercase()) {
                "cartesia" -> {
                    keyVault.setCartesiaKey(trimmed)
                    settingsRepository.updateCartesiaApiKey(trimmed)
                    _uiState.update { it.copy(cartesiaKey = trimmed) }
                }
                "deepgram" -> {
                    keyVault.setDeepgramKey(trimmed)
                    settingsRepository.updateDeepgramApiKey(trimmed)
                    _uiState.update { it.copy(deepgramKey = trimmed) }
                }
                "elevenlabs" -> {
                    keyVault.setElevenLabsKey(trimmed)
                    settingsRepository.updateElevenLabsApiKey(trimmed)
                    _uiState.update { it.copy(elevenLabsKey = trimmed) }
                }
                "gemini" -> {
                    keyVault.setGeminiKey(trimmed)
                    settingsRepository.updateGeminiApiKey(trimmed)
                    _uiState.update { it.copy(geminiKey = trimmed) }
                }
                "groq" -> {
                    keyVault.setGroqKey(trimmed)
                    settingsRepository.updateGroqApiKey(trimmed)
                    _uiState.update { it.copy(groqKey = trimmed) }
                }
                "openai" -> {
                    keyVault.setOpenAiKey(trimmed)
                    settingsRepository.updateOpenAiApiKey(trimmed)
                    _uiState.update { it.copy(openAiKey = trimmed) }
                }
                "openrouter" -> {
                    keyVault.setOpenRouterKey(trimmed)
                    settingsRepository.updateOpenRouterApiKey(trimmed)
                    _uiState.update { it.copy(openRouterKey = trimmed) }
                }
            }
        }
    }

    fun validateKey(providerId: String) {
        val key = when (providerId.lowercase()) {
            "cartesia"   -> _uiState.value.cartesiaKey
            "deepgram"   -> _uiState.value.deepgramKey
            "elevenlabs" -> _uiState.value.elevenLabsKey
            "gemini"     -> _uiState.value.geminiKey
            "groq"       -> _uiState.value.groqKey
            "openai"     -> _uiState.value.openAiKey
            "openrouter" -> _uiState.value.openRouterKey
            else         -> ""
        }

        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    validationStatus = it.validationStatus + (providerId to KeyValidationStatus.Invalid("Key is empty"))
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                validationStatus = it.validationStatus + (providerId to KeyValidationStatus.Checking)
            )
        }

        viewModelScope.launch {
            val status = keyValidator.validate(providerId, key)
            _uiState.update {
                it.copy(validationStatus = it.validationStatus + (providerId to status))
            }
        }
    }
}

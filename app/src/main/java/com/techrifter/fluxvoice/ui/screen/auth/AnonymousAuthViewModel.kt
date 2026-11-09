package com.techrifter.fluxvoice.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.auth.FirebaseAuthService
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnonymousAuthUiState(
    val username: String = "",
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AnonymousAuthViewModel @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnonymousAuthUiState())
    val uiState: StateFlow<AnonymousAuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings().first()
            val initialUsername = settings.anonymousUsername.ifBlank {
                firebaseAuthService.generateRandomUsername()
            }
            _uiState.update {
                it.copy(username = initialUsername)
            }
        }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun rollRandomUsername() {
        val randomName = firebaseAuthService.generateRandomUsername()
        _uiState.update { it.copy(username = randomName, errorMessage = null) }
    }

    fun confirmIdentity(onSuccess: () -> Unit) {
        val currentUsername = _uiState.value.username.trim()
        if (currentUsername.length < 3) {
            _uiState.update { it.copy(errorMessage = "Username must be at least 3 characters") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = firebaseAuthService.initializeAnonymousIdentity(
                username = currentUsername
            )
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isComplete = true) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to set username"
                    )
                }
            }
        }
    }
}


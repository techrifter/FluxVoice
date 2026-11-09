package com.techrifter.fluxvoice.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.auth.FirebaseAuthService
import com.techrifter.fluxvoice.data.local.db.DefaultPipelines
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModel
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModelService
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.TtsProvider
import com.techrifter.fluxvoice.domain.pipeline.PipelineSessionManager
import com.techrifter.fluxvoice.domain.repository.PipelineRepository
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val openRouterModelService: OpenRouterModelService,
    private val keyVault: KeyVault,
    private val pipelineSessionManager: PipelineSessionManager,
    private val pipelineRepository: PipelineRepository,
    private val firebaseAuthService: FirebaseAuthService
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProviderModels: StateFlow<List<OpenRouterModel>> = uiState
        .map { it.settings.providerType }
        .distinctUntilChanged()
        .flatMapLatest { providerType ->
            val providerKey = when (providerType) {
                ProviderType.GROQ -> "groq"
                ProviderType.GEMINI -> "gemini"
                ProviderType.OPENAI_COMPAT -> "openai"
                ProviderType.OPENROUTER -> "openrouter"
                else -> "openrouter"
            }
            openRouterModelService.getModelsForProvider(providerKey)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            openRouterModelService.popularGroq
        )

    init {
        viewModelScope.launch {
            openRouterModelService.fetchModels()
            if (keyVault.getGroqKey().isNotBlank()) {
                openRouterModelService.fetchGroqModels()
            }
            if (keyVault.getGeminiKey().isNotBlank()) {
                openRouterModelService.fetchGeminiModels()
            }
            if (keyVault.getOpenAiKey().isNotBlank()) {
                openRouterModelService.fetchOpenAiModels()
            }
        }
        viewModelScope.launch {
            currentProviderModels.collect { models ->
                if (models.isNotEmpty()) {
                    val settings = uiState.value.settings
                    if (settings.appMode != AppMode.CUSTOM && settings.providerType == ProviderType.GROQ) {
                        val currentModel = settings.modelId
                        val isInvalid = currentModel.isBlank() ||
                                currentModel.contains("llama-3.3-70b-versatile") ||
                                models.none { it.id.equals(currentModel, ignoreCase = true) }
                        if (isInvalid) {
                            val activeDefault = models.first().id
                            settingsRepository.updateModelId(activeDefault)
                        }
                    }
                }
            }
        }
    }

    fun refreshCurrentProviderModels() = viewModelScope.launch {
        val currentProvider = uiState.value.settings.providerType
        val providerKey = when (currentProvider) {
            ProviderType.GROQ -> "groq"
            ProviderType.GEMINI -> "gemini"
            ProviderType.OPENAI_COMPAT -> "openai"
            ProviderType.OPENROUTER -> "openrouter"
            else -> "openrouter"
        }
        openRouterModelService.refreshProviderModels(providerKey, forceRefresh = true)
    }

    // ── App Mode & Pipeline Activations ───────────────────────────────────────
    fun updateAppMode(mode: AppMode) = viewModelScope.launch {
        settingsRepository.updateAppMode(mode.name)
        when (mode) {
            AppMode.FAST     -> settingsRepository.applyFastPreset()
            AppMode.THINKING -> settingsRepository.applyThinkingPreset()
            AppMode.CUSTOM   -> settingsRepository.applyCustomPreset()
        }
    }

    fun applyFastPresetAndActivate(onSuccess: () -> Unit) = viewModelScope.launch {
        settingsRepository.updateAppMode(AppMode.FAST.name)
        settingsRepository.applyFastPreset()
        pipelineRepository.getPipelineById("preset_ultra_fast")?.let {
            pipelineSessionManager.activatePipeline(it)
        }
        _savedMessage.value = "Fast Mode Activated"
        onSuccess()
    }

    fun applyThinkingPresetAndActivate(onSuccess: () -> Unit) = viewModelScope.launch {
        settingsRepository.updateAppMode(AppMode.THINKING.name)
        settingsRepository.applyThinkingPreset()
        pipelineRepository.getPipelineById("preset_reasoning")?.let {
            pipelineSessionManager.activatePipeline(it)
        }
        _savedMessage.value = "Thinking Mode Activated"
        onSuccess()
    }

    fun saveCustomPipelineAndActivate(onSuccess: () -> Unit) = viewModelScope.launch {
        settingsRepository.updateAppMode(AppMode.CUSTOM.name)
        val current = uiState.value.settings
        val existingCustom = pipelineRepository.getPipelineById(DefaultPipelines.CUSTOM_PIPELINE_ID)

        val customConfig = PipelineConfig(
            id = DefaultPipelines.CUSTOM_PIPELINE_ID,
            name = existingCustom?.name?.takeIf { it.isNotBlank() && !it.contains("(Custom)") } ?: DefaultPipelines.DEFAULT_CUSTOM_NAME,
            description = existingCustom?.description?.takeIf { it.isNotBlank() } ?: DefaultPipelines.DEFAULT_CUSTOM_DESC,
            isPreset = false,
            sttProviderId = when (current.sttProvider) {
                SttProvider.GROQ_WHISPER -> "groq_whisper"
                SttProvider.WHISPER -> "whisper"
                else -> "deepgram"
            },
            sttModelId = when (current.sttProvider) {
                SttProvider.GROQ_WHISPER -> "whisper-large-v3-turbo"
                SttProvider.WHISPER -> "whisper-1"
                else -> "nova-3"
            },
            sttEndpointingMs = 400,
            llmProviderId = when (current.providerType) {
                ProviderType.GROQ -> "groq"
                ProviderType.GEMINI -> "gemini"
                ProviderType.OPENROUTER -> "openrouter"
                ProviderType.OPENAI_COMPAT -> "openai"
                ProviderType.LOCAL -> "local"
            },
            llmModelId = current.modelId.ifBlank { current.providerType.defaultModel },
            customEndpointUrl = current.localLlmUrl,
            temperature = current.temperature,
            maxOutputTokens = 1024,
            systemPrompt = current.systemPrompt.ifBlank { PipelineConfig.DEFAULT_SYSTEM_PROMPT },
            ttsProviderId = current.ttsProvider.id,
            ttsVoiceId = current.cartesiaVoiceId.ifBlank { current.ttsProvider.defaultVoiceId },
            speechRate = current.ttsSpeechRate,
            pitch = current.ttsPitch,
            vadEnabled = current.vadEnabled,
            vadSensitivity = current.vadSensitivity,
            backchannelEnabled = current.backchannelEnabled
        )

        pipelineRepository.savePipeline(customConfig)
        pipelineSessionManager.activatePipeline(customConfig)
        _savedMessage.value = "Custom Pipeline Saved & Active"
        onSuccess()
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
        if (uiState.value.settings.appMode == AppMode.CUSTOM) {
            settingsRepository.updateModelId("")
        } else {
            val defaultModel = ProviderType.entries.firstOrNull { it.name == value }?.defaultModel
            if (defaultModel != null) settingsRepository.updateModelId(defaultModel)
        }
    }
    fun updateGeminiApiKey(value: String) = viewModelScope.launch { 
        settingsRepository.updateGeminiApiKey(value)
        keyVault.setGeminiKey(value)
        if (value.isNotBlank()) openRouterModelService.fetchGeminiModels(forceRefresh = true)
    }
    fun updateOpenAiApiKey(value: String) = viewModelScope.launch { 
        settingsRepository.updateOpenAiApiKey(value)
        keyVault.setOpenAiKey(value)
        if (value.isNotBlank()) openRouterModelService.fetchOpenAiModels(forceRefresh = true)
    }
    fun updateGroqApiKey(value: String)   = viewModelScope.launch { 
        settingsRepository.updateGroqApiKey(value)
        keyVault.setGroqKey(value)
        if (value.isNotBlank()) openRouterModelService.fetchGroqModels(forceRefresh = true)
    }
    fun getOpenRouterApiKey(): String = keyVault.getOpenRouterKey()
    fun updateOpenRouterApiKey(value: String) = viewModelScope.launch { 
        settingsRepository.updateOpenRouterApiKey(value)
        keyVault.setOpenRouterKey(value)
        if (value.isNotBlank()) openRouterModelService.fetchModels(forceRefresh = true)
    }
    fun updateLocalLlmUrl(value: String)  = viewModelScope.launch { settingsRepository.updateLocalLlmUrl(value) }
    fun updateModelId(value: String)      = viewModelScope.launch { settingsRepository.updateModelId(value) }
    fun updateTemperature(value: Float)   = viewModelScope.launch { settingsRepository.updateTemperature(value) }
    fun updateSystemPrompt(value: String) = viewModelScope.launch { settingsRepository.updateSystemPrompt(value) }

    // ── ElevenLabs ────────────────────────────────────────────────────────────
    fun updateElevenLabsAgentId(value: String) = viewModelScope.launch { settingsRepository.updateElevenLabsAgentId(value) }

    // ── Deepgram ──────────────────────────────────────────────────────────────
    fun updateDeepgramApiKey(value: String)  = viewModelScope.launch { 
        settingsRepository.updateDeepgramApiKey(value)
        keyVault.setDeepgramKey(value)
    }

    // ── Voice Conversation ────────────────────────────────────────────────────
    fun updateMaxContextTurns(value: Int) = viewModelScope.launch { settingsRepository.updateMaxContextTurns(value) }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    fun updateTtsEnabled(value: Boolean)    = viewModelScope.launch { settingsRepository.updateTtsEnabled(value) }
    fun updateTtsProvider(value: String)    = viewModelScope.launch {
        settingsRepository.updateTtsProvider(value)
        val defaultVoice = if (uiState.value.settings.appMode == AppMode.CUSTOM) {
            ""
        } else {
            when (value.uppercase()) {
                "CARTESIA" -> "638efaaa-4d0c-442e-b701-3fae16aad012"
                "ELEVENLABS" -> "JBFqnCBsd6RMkjVDRZzb"
                "OPENAI" -> "alloy"
                "DEEPGRAM_AURA", "DEEPGRAM" -> "aura-asteria-en"
                "ANDROID" -> "default"
                else -> "default"
            }
        }
        settingsRepository.updateCartesiaVoiceId(defaultVoice)
        pipelineSessionManager.getActivePipeline()?.let { active ->
            val updated = active.copy(
                ttsProviderId = value.lowercase(),
                ttsVoiceId = defaultVoice
            )
            pipelineRepository.updatePipeline(updated)
        }
    }
    fun updateTtsSpeechRate(value: Float)   = viewModelScope.launch { settingsRepository.updateTtsSpeechRate(value) }
    fun updateTtsPitch(value: Float)        = viewModelScope.launch { settingsRepository.updateTtsPitch(value) }

    // ── Cartesia TTS ──────────────────────────────────────────────────────────
    fun updateCartesiaApiKey(value: String)  = viewModelScope.launch {
        settingsRepository.updateCartesiaApiKey(value)
        keyVault.setCartesiaKey(value)
    }
    fun updateCartesiaVoiceId(value: String) = viewModelScope.launch { settingsRepository.updateCartesiaVoiceId(value) }

    // ── ElevenLabs TTS ────────────────────────────────────────────────────────
    fun getElevenLabsApiKey(): String = keyVault.getElevenLabsKey()
    fun updateElevenLabsApiKey(value: String) = viewModelScope.launch {
        settingsRepository.updateElevenLabsApiKey(value)
        keyVault.setElevenLabsKey(value)
    }

    // ── Conversational UX ─────────────────────────────────────────────────────
    fun updateVadEnabled(value: Boolean)         = viewModelScope.launch { settingsRepository.updateVadEnabled(value) }
    fun updateVadSensitivity(value: Int)         = viewModelScope.launch { settingsRepository.updateVadSensitivity(value) }
    fun updateBackchannelEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.updateBackchannelEnabled(value) }

    // ── Anonymous Identity & Username ────────────────────────────────────────
    fun getRandomUsername(): String = firebaseAuthService.generateRandomUsername()

    fun updateUsername(newUsername: String) = viewModelScope.launch {
        _isSaving.value = true
        val result = firebaseAuthService.updateUsername(newUsername)
        _isSaving.value = false
        result.onSuccess { name ->
            _savedMessage.value = "Identity updated to $name"
        }.onFailure { err ->
            _savedMessage.value = err.message ?: "Failed updating identity"
        }
    }

    fun dismissSavedMessage() { _savedMessage.value = null }
}

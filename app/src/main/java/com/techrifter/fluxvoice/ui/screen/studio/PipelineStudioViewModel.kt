package com.techrifter.fluxvoice.ui.screen.studio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.local.db.DefaultPipelines
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModel
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModelService
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.pipeline.PipelineExporter
import com.techrifter.fluxvoice.domain.pipeline.PipelineSessionManager
import com.techrifter.fluxvoice.domain.repository.PipelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PipelineStudioUiState(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val isPreset: Boolean = false,

    val sttProviderId: String = "deepgram",
    val sttModelId: String = "nova-3",
    val sttEndpointingMs: Int = 400,
    val customSttEndpointUrl: String = "",
    val customSttApiKey: String = "",

    val llmProviderId: String = "groq",
    val llmModelId: String = "openai/gpt-oss-120b",
    val customEndpointUrl: String = "",
    val customApiKey: String = "",
    val temperature: Float = 0.7f,
    val systemPrompt: String = com.techrifter.fluxvoice.domain.model.PipelineConfig.DEFAULT_SYSTEM_PROMPT,

    val ttsProviderId: String = "cartesia",
    val ttsVoiceId: String = "638efaaa-4d0c-442e-b701-3fae16aad012",
    val speechRate: Float = 1.0f,

    val vadEnabled: Boolean = true,
    val vadSensitivity: Int = 850,
    val backchannelEnabled: Boolean = true,

    val exportedSdkCode: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class PipelineStudioViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val pipelineRepository: PipelineRepository,
    private val pipelineSessionManager: PipelineSessionManager,
    private val keyVault: KeyVault,
    private val openRouterModelService: OpenRouterModelService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PipelineStudioUiState())
    val uiState: StateFlow<PipelineStudioUiState> = _uiState.asStateFlow()

    val openRouterModels: StateFlow<List<OpenRouterModel>> = openRouterModelService.models
    val popularOpenRouterModels: List<OpenRouterModel> get() = openRouterModelService.popularModels

    val isFetchingModels: StateFlow<Boolean> = openRouterModelService.isLoading

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProviderModels: StateFlow<List<OpenRouterModel>> = _uiState
        .map { it.llmProviderId }
        .distinctUntilChanged()
        .flatMapLatest { provider ->
            openRouterModelService.getModelsForProvider(provider)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private var isCreatingNew = false
    private var initializeJob: Job? = null
    private var pendingCatalogOverride: Triple<String, String, String>? = null

    init {
        val navIsNew = runCatching { savedStateHandle.get<Boolean>("isNew") }.getOrNull() ?: false
        val navPipelineId = runCatching { savedStateHandle.get<String>("pipelineId") }.getOrNull()
        initialize(navPipelineId, navIsNew)

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
    }

    fun initialize(pipelineId: String?, isNew: Boolean) {
        initializeJob?.cancel()
        initializeJob = viewModelScope.launch {
            if (isNew) {
                isCreatingNew = true
                val customCount = pipelineRepository.getCustomPipelineCount()
                val nextNum = customCount + 1
                val newId = "custom_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                _uiState.update {
                    it.copy(
                        id = newId,
                        name = "Custom Stack $nextNum",
                        description = "Tailored conversational voice stack with custom STT, LLM, and TTS providers.",
                        isPreset = false,
                        sttProviderId = "deepgram",
                        sttModelId = "nova-3",
                        sttEndpointingMs = 400,
                        customSttEndpointUrl = "",
                        customSttApiKey = "",
                        llmProviderId = "groq",
                        llmModelId = "openai/gpt-oss-120b",
                        customEndpointUrl = "",
                        customApiKey = "",
                        temperature = 0.7f,
                        systemPrompt = PipelineConfig.DEFAULT_SYSTEM_PROMPT,
                        ttsProviderId = "cartesia",
                        ttsVoiceId = "638efaaa-4d0c-442e-b701-3fae16aad012",
                        speechRate = 1.0f,
                        vadEnabled = true,
                        vadSensitivity = 850,
                        backchannelEnabled = true
                    )
                }
            } else if (!pipelineId.isNullOrBlank()) {
                isCreatingNew = false
                val target = pipelineRepository.getPipelineById(pipelineId)
                if (target != null) {
                    populateFromPipeline(target)
                } else {
                    loadDefaultOrActive()
                }
            } else {
                isCreatingNew = false
                loadDefaultOrActive()
            }

            pendingCatalogOverride?.let { (modelId, provider, category) ->
                internalApplyCatalogModel(modelId, provider, category)
            }
        }
    }

    fun hasKeyForProvider(providerId: String): Boolean {
        return when (providerId.lowercase()) {
            "cartesia"   -> keyVault.getCartesiaKey().isNotBlank()
            "deepgram"   -> keyVault.getDeepgramKey().isNotBlank()
            "elevenlabs" -> keyVault.getElevenLabsKey().isNotBlank()
            "gemini"     -> keyVault.getGeminiKey().isNotBlank()
            "groq"       -> keyVault.getGroqKey().isNotBlank()
            "openai"     -> keyVault.getOpenAiKey().isNotBlank()
            "openrouter" -> keyVault.getOpenRouterKey().isNotBlank()
            else         -> true
        }
    }

    fun refreshCurrentProviderModels() {
        viewModelScope.launch {
            openRouterModelService.refreshProviderModels(_uiState.value.llmProviderId, forceRefresh = true)
        }
    }

    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            openRouterModelService.fetchModels(forceRefresh = true)
        }
    }

    private suspend fun loadDefaultOrActive() {
        val existingCustom = pipelineRepository.getPipelineById(DefaultPipelines.CUSTOM_PIPELINE_ID)
        val active = pipelineSessionManager.getActivePipeline()

        val base = when {
            existingCustom != null -> existingCustom
            active != null && !active.isPreset -> active
            active != null -> active
            else -> null
        }

        if (base != null) {
            populateFromPipeline(base)
        }
    }

    private fun populateFromPipeline(base: PipelineConfig) {
        val resolvedName = if (base.name.isBlank()) {
            DefaultPipelines.DEFAULT_CUSTOM_NAME
        } else if (base.isPreset) {
            DefaultPipelines.DEFAULT_CUSTOM_NAME
        } else {
            base.name
        }
        val resolvedDesc = if (base.description.isBlank()) {
            DefaultPipelines.DEFAULT_CUSTOM_DESC
        } else {
            base.description
        }
        val resolvedId = if (base.isPreset) {
            "custom_" + UUID.randomUUID().toString().take(8)
        } else {
            base.id
        }

        _uiState.update {
            it.copy(
                id = resolvedId,
                name = resolvedName,
                description = resolvedDesc,
                isPreset = false,
                sttProviderId = base.sttProviderId,
                sttModelId = base.sttModelId,
                sttEndpointingMs = base.sttEndpointingMs,
                customSttEndpointUrl = keyVault.getKey("custom_stt_url").ifBlank { if (base.sttProviderId == "custom") base.customEndpointUrl else "" },
                customSttApiKey = keyVault.getKey("custom_stt_key"),
                llmProviderId = base.llmProviderId,
                llmModelId = base.llmModelId,
                customEndpointUrl = base.customEndpointUrl,
                customApiKey = keyVault.getKey("custom_endpoint"),
                temperature = base.temperature,
                systemPrompt = base.systemPrompt,
                ttsProviderId = base.ttsProviderId,
                ttsVoiceId = base.ttsVoiceId,
                speechRate = base.speechRate,
                vadEnabled = base.vadEnabled,
                vadSensitivity = base.vadSensitivity,
                backchannelEnabled = base.backchannelEnabled
            )
        }
    }

    fun applyCatalogModel(modelId: String, provider: String, categoryName: String) {
        pendingCatalogOverride = Triple(modelId, provider, categoryName)
        internalApplyCatalogModel(modelId, provider, categoryName)
    }

    private fun internalApplyCatalogModel(modelId: String, provider: String, categoryName: String) {
        val cat = categoryName.uppercase()
        when {
            cat == "STT" || modelId.contains("nova", ignoreCase = true) || modelId.contains("whisper", ignoreCase = true) -> {
                val sttProvider = when {
                    provider.contains("Groq", ignoreCase = true) || modelId.contains("whisper", ignoreCase = true) -> "groq_whisper"
                    provider.contains("OpenAI", ignoreCase = true) -> "whisper"
                    provider.contains("Deepgram", ignoreCase = true) || modelId.contains("nova", ignoreCase = true) -> "deepgram"
                    else -> "deepgram"
                }
                _uiState.update { it.copy(sttModelId = modelId, sttProviderId = sttProvider) }
            }
            cat == "TTS" || modelId.contains("sonic", ignoreCase = true) || modelId.contains("eleven", ignoreCase = true) || modelId.contains("aura", ignoreCase = true) || modelId.contains("tts-1", ignoreCase = true) || modelId.contains("android", ignoreCase = true) -> {
                val ttsProvider = when {
                    provider.contains("Cartesia", ignoreCase = true) || modelId.contains("sonic", ignoreCase = true) -> "cartesia"
                    provider.contains("ElevenLabs", ignoreCase = true) || modelId.contains("eleven", ignoreCase = true) -> "elevenlabs"
                    provider.contains("OpenAI", ignoreCase = true) || modelId.contains("tts-1", ignoreCase = true) -> "openai"
                    provider.contains("Deepgram", ignoreCase = true) || modelId.contains("aura", ignoreCase = true) -> "deepgram"
                    provider.contains("Device", ignoreCase = true) || provider.contains("Android", ignoreCase = true) || modelId.contains("android", ignoreCase = true) -> "android"
                    else -> "cartesia"
                }
                val defaultVoiceId = when (ttsProvider) {
                    "cartesia" -> if (modelId.contains("-") && modelId.length > 25) modelId else "638efaaa-4d0c-442e-b701-3fae16aad012"
                    "elevenlabs" -> if (modelId.length > 15 && !modelId.contains("eleven", ignoreCase = true)) modelId else "JBFqnCBsd6RMkjVDRZzb"
                    "openai" -> "alloy"
                    "deepgram" -> "aura-asteria-en"
                    "android" -> "default"
                    else -> modelId
                }
                _uiState.update { it.copy(ttsProviderId = ttsProvider, ttsVoiceId = defaultVoiceId) }
            }
            else -> {
                val llmProvider = when {
                    provider.contains("Groq", ignoreCase = true) -> "groq"
                    provider.contains("Google", ignoreCase = true) || provider.contains("Gemini", ignoreCase = true) || modelId.startsWith("gemini", ignoreCase = true) -> "gemini"
                    provider.contains("OpenAI", ignoreCase = true) || modelId.startsWith("gpt", ignoreCase = true) || modelId.startsWith("o1", ignoreCase = true) || modelId.startsWith("o3", ignoreCase = true) -> "openai"
                    provider.contains("OpenRouter", ignoreCase = true) || modelId.contains("/") -> "openrouter"
                    provider.contains("Custom", ignoreCase = true) -> "custom"
                    else -> "groq"
                }
                _uiState.update { it.copy(llmProviderId = llmProvider, llmModelId = modelId) }
                viewModelScope.launch {
                    openRouterModelService.refreshProviderModels(llmProvider, forceRefresh = false)
                }
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateDescription(desc: String) = _uiState.update { it.copy(description = desc) }
    fun updateSttProvider(stt: String) {
        val defaultModel = if (stt.lowercase() == "custom") "whisper-1" else if (stt.lowercase() == "deepgram") "nova-3" else "whisper-large-v3-turbo"
        _uiState.update { it.copy(sttProviderId = stt, sttModelId = defaultModel) }
    }
    fun updateSttModel(model: String) = _uiState.update { it.copy(sttModelId = model) }
    fun updateSttEndpointing(ms: Int) = _uiState.update { it.copy(sttEndpointingMs = ms) }
    fun updateCustomSttEndpointUrl(url: String) = _uiState.update { it.copy(customSttEndpointUrl = url) }
    fun updateCustomSttApiKey(key: String) = _uiState.update { it.copy(customSttApiKey = key) }

    fun updateLlmProvider(provider: String) {
        val defaultModel = when (provider.lowercase()) {
            "groq"       -> "openai/gpt-oss-120b"
            "openrouter" -> "meta-llama/llama-3.3-70b-instruct"
            "openai"     -> "gpt-4o-mini"
            "gemini"     -> "gemini-2.5-flash"
            "custom"     -> _uiState.value.llmModelId.ifBlank { "meta-llama/llama-3.3-70b-instruct" }
            else         -> "openai/gpt-oss-120b"
        }
        val defaultUrl = if (provider.lowercase() == "custom") {
            _uiState.value.customEndpointUrl.ifBlank { "https://api.together.xyz/v1" }
        } else {
            _uiState.value.customEndpointUrl
        }
        _uiState.update { it.copy(llmProviderId = provider, llmModelId = defaultModel, customEndpointUrl = defaultUrl) }
        viewModelScope.launch {
            openRouterModelService.refreshProviderModels(provider, forceRefresh = false)
        }
    }

    fun updateCustomEndpointUrl(url: String) = _uiState.update { it.copy(customEndpointUrl = url) }
    fun updateCustomApiKey(key: String) = _uiState.update { it.copy(customApiKey = key) }

    fun updateLlmModel(model: String) = _uiState.update { it.copy(llmModelId = model) }
    fun updateTemperature(temp: Float) = _uiState.update { it.copy(temperature = temp) }
    fun updateSystemPrompt(prompt: String) = _uiState.update { it.copy(systemPrompt = prompt) }

    fun updateTtsProvider(provider: String) {
        val defaultVoice = when (provider.lowercase()) {
            "cartesia" -> "638efaaa-4d0c-442e-b701-3fae16aad012"
            "elevenlabs" -> "JBFqnCBsd6RMkjVDRZzb"
            "openai" -> "alloy"
            "deepgram", "deepgram_aura" -> "aura-asteria-en"
            else -> "default"
        }
        _uiState.update { it.copy(ttsProviderId = provider, ttsVoiceId = defaultVoice) }
    }

    fun updateTtsVoiceId(voiceId: String) = _uiState.update { it.copy(ttsVoiceId = voiceId) }
    fun updateSpeechRate(rate: Float) = _uiState.update { it.copy(speechRate = rate) }

    fun updateVadEnabled(enabled: Boolean) = _uiState.update { it.copy(vadEnabled = enabled) }
    fun updateVadSensitivity(sensitivity: Int) = _uiState.update { it.copy(vadSensitivity = sensitivity) }
    fun updateBackchannelEnabled(enabled: Boolean) = _uiState.update { it.copy(backchannelEnabled = enabled) }

    fun saveAndActivate(onSuccess: () -> Unit) {
        val s = _uiState.value
        val resolvedCustomUrl = when {
            s.customEndpointUrl.isNotBlank() -> s.customEndpointUrl
            s.sttProviderId == "custom" && s.customSttEndpointUrl.isNotBlank() -> s.customSttEndpointUrl
            else -> ""
        }
        val resolvedId = if (isCreatingNew || s.id.isBlank() || s.id.startsWith("preset_")) {
            if (s.id.startsWith("custom_") && s.id != DefaultPipelines.CUSTOM_PIPELINE_ID) {
                s.id
            } else {
                "custom_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            }
        } else {
            s.id
        }
        val config = PipelineConfig(
            id = resolvedId,
            name = s.name.ifBlank { "Custom Stack" },
            description = s.description.ifBlank { DefaultPipelines.DEFAULT_CUSTOM_DESC },
            isPreset = false,
            sttProviderId = s.sttProviderId,
            sttModelId = s.sttModelId,
            sttEndpointingMs = s.sttEndpointingMs,
            llmProviderId = s.llmProviderId,
            llmModelId = s.llmModelId,
            customEndpointUrl = resolvedCustomUrl,
            temperature = s.temperature,
            systemPrompt = s.systemPrompt,
            ttsProviderId = s.ttsProviderId,
            ttsVoiceId = s.ttsVoiceId,
            speechRate = s.speechRate,
            vadEnabled = s.vadEnabled,
            vadSensitivity = s.vadSensitivity,
            backchannelEnabled = s.backchannelEnabled,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            if (s.customSttEndpointUrl.isNotBlank()) {
                keyVault.setKey("custom_stt_url", s.customSttEndpointUrl.trim())
            }
            if (s.customSttApiKey.isNotBlank()) {
                keyVault.setKey("custom_stt_key", s.customSttApiKey.trim())
            }
            if (s.customApiKey.isNotBlank()) {
                keyVault.setKey("custom_endpoint", s.customApiKey.trim())
                keyVault.setKey("openai_compat", s.customApiKey.trim())
            }
            pipelineRepository.savePipeline(config)
            pipelineSessionManager.activatePipeline(config)
            _uiState.update { it.copy(id = resolvedId, isSaved = true) }
            onSuccess()
        }
    }

    fun deletePipeline(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isPreset) return
        viewModelScope.launch {
            pipelineRepository.deletePipeline(s.id)
            onSuccess()
        }
    }

    fun generateSdkCode() {
        val s = _uiState.value
        val config = PipelineConfig(
            id = s.id,
            name = s.name,
            description = s.description,
            isPreset = false,
            sttProviderId = s.sttProviderId,
            sttModelId = s.sttModelId,
            sttEndpointingMs = s.sttEndpointingMs,
            llmProviderId = s.llmProviderId,
            llmModelId = s.llmModelId,
            temperature = s.temperature,
            systemPrompt = s.systemPrompt,
            ttsProviderId = s.ttsProviderId,
            ttsVoiceId = s.ttsVoiceId,
            speechRate = s.speechRate,
            vadEnabled = s.vadEnabled,
            vadSensitivity = s.vadSensitivity,
            backchannelEnabled = s.backchannelEnabled
        )
        val code = PipelineExporter.exportToKotlinSdk(config)
        _uiState.update { it.copy(exportedSdkCode = code) }
    }

    fun dismissSdkCode() {
        _uiState.update { it.copy(exportedSdkCode = null) }
    }
}

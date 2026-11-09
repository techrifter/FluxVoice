package com.techrifter.fluxvoice.ui.screen.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModelService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModelCategory(val displayName: String) {
    ALL("All"),
    FAST_LLM("Ultra-Fast"),
    REASONING_LLM("Reasoning"),
    STT("Speech-to-Text"),
    TTS("Voice Synthesis")
}

data class ModelCatalogItem(
    val id: String,
    val name: String,
    val provider: String,
    val category: ModelCategory,
    val description: String,
    val latencyTier: String,
    val pricing: String,
    val tags: List<String>
)

data class ModelCatalogUiState(
    val selectedCategory: ModelCategory = ModelCategory.ALL,
    val searchQuery: String = "",
    val models: List<ModelCatalogItem> = emptyList(),
    val totalModelsCount: Int = 0
)

@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val openRouterModelService: OpenRouterModelService
) : ViewModel() {

    private val baseModels = listOf(
        // Fast LLMs
        ModelCatalogItem(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            provider = "Google AI Studio",
            category = ModelCategory.FAST_LLM,
            description = "High-speed multimodal generation with generous 15 RPM / 1,000,000 TPM on free tier.",
            latencyTier = "1M Context • Multimodal",
            pricing = "Free Tier · 15 RPM / 1M TPM",
            tags = listOf("Free Tier", "Fast", "1M Context", "Multimodal")
        ),
        ModelCatalogItem(
            id = "openai/gpt-oss-120b",
            name = "GPT OSS 120B",
            provider = "Groq Cloud",
            category = ModelCategory.FAST_LLM,
            description = "High-performance open reasoning model running on Groq LPU architecture. Verified active production model.",
            latencyTier = "128k Context • Groq LPU",
            pricing = "Active Groq Model",
            tags = listOf("High IQ", "Conversational", "LPU", "Groq")
        ),
        ModelCatalogItem(
            id = "gpt-4o-mini",
            name = "GPT-4o-mini Fast",
            provider = "OpenAI",
            category = ModelCategory.FAST_LLM,
            description = "Sub-second conversational intelligence. Lowest-cost and most accessible OpenAI model.",
            latencyTier = "128k Context • Fast",
            pricing = "OpenAI API · BYOK",
            tags = listOf("Universal", "Budget", "Fast")
        ),

        // Reasoning LLMs
        ModelCatalogItem(
            id = "anthropic/claude-3.5-sonnet",
            name = "Claude 3.5 Sonnet",
            provider = "OpenRouter",
            category = ModelCategory.REASONING_LLM,
            description = "Industry benchmark for complex logic, nuanced conversational persona, and code.",
            latencyTier = "200k Context • High IQ",
            pricing = "OpenRouter Gateway · BYOK",
            tags = listOf("High Intelligence", "Coding", "Reasoning")
        ),
        ModelCatalogItem(
            id = "deepseek/deepseek-r1",
            name = "DeepSeek R1",
            provider = "OpenRouter",
            category = ModelCategory.REASONING_LLM,
            description = "Open reasoning model with chain-of-thought logic and high math/logic capability.",
            latencyTier = "64k Context • CoT Logic",
            pricing = "OpenRouter Gateway · BYOK",
            tags = listOf("Chain of Thought", "Reasoning", "Open Weights")
        ),
        ModelCatalogItem(
            id = "gpt-4o",
            name = "GPT-4o",
            provider = "OpenAI",
            category = ModelCategory.REASONING_LLM,
            description = "OpenAI flagship multimodal intelligence with deep knowledge base.",
            latencyTier = "128k Context • Flagship",
            pricing = "OpenAI API · BYOK",
            tags = listOf("Flagship", "Multimodal", "OpenAI")
        ),

        // STT
        ModelCatalogItem(
            id = "nova-3",
            name = "Deepgram Nova-3",
            provider = "Deepgram",
            category = ModelCategory.STT,
            description = "Real-time streaming speech recognition with sub-second WebSocket interim results.",
            latencyTier = "WebSocket Interim Stream",
            pricing = "Deepgram API · BYOK",
            tags = listOf("Real-Time", "Interim Transcripts", "WebSocket")
        ),
        ModelCatalogItem(
            id = "whisper-large-v3-turbo",
            name = "Groq Whisper Large v3 Turbo",
            provider = "Groq Cloud",
            category = ModelCategory.STT,
            description = "High-accuracy batch transcription with Groq LPU acceleration.",
            latencyTier = "REST Audio Inference",
            pricing = "Groq Cloud · Active",
            tags = listOf("Fast REST", "High Accuracy", "Multilingual")
        ),

        // TTS
        ModelCatalogItem(
            id = "sonic-3.6",
            name = "Cartesia Sonic 3.6",
            provider = "Cartesia",
            category = ModelCategory.TTS,
            description = "Expressive streaming voice synthesis with low-latency audio delivery.",
            latencyTier = "WebSocket Streaming Audio",
            pricing = "Cartesia API · BYOK",
            tags = listOf("Expressive", "Low Latency", "Streaming Audio")
        ),
        ModelCatalogItem(
            id = "eleven-flash-v2-5",
            name = "ElevenLabs Flash v2.5",
            provider = "ElevenLabs",
            category = ModelCategory.TTS,
            description = "Studio-grade natural prosody, human breathing, and emotional inflection.",
            latencyTier = "Studio Neural Prosody",
            pricing = "ElevenLabs API · BYOK",
            tags = listOf("Studio Grade", "Emotional Prosody", "Ultra-Natural")
        ),
        ModelCatalogItem(
            id = "openai-tts-1",
            name = "OpenAI Speech (tts-1)",
            provider = "OpenAI",
            category = ModelCategory.TTS,
            description = "Industry-standard neural voice synthesis with 6 natural voice profiles (Alloy, Echo, Shimmer).",
            latencyTier = "Neural Multi-Voice",
            pricing = "OpenAI API · BYOK",
            tags = listOf("Clear", "Reliable", "Standard API")
        ),
        ModelCatalogItem(
            id = "deepgram-aura",
            name = "Deepgram Aura",
            provider = "Deepgram",
            category = ModelCategory.TTS,
            description = "High-speed streaming neural voice optimized for conversational AI turn-taking.",
            latencyTier = "Streaming Neural Audio",
            pricing = "Deepgram API · BYOK",
            tags = listOf("Conversational", "Low Latency", "Streaming")
        ),
        ModelCatalogItem(
            id = "android-tts",
            name = "Android System TTS",
            provider = "Device Hardware",
            category = ModelCategory.TTS,
            description = "100% on-device offline voice synthesis. Zero network round-trip, zero cost.",
            latencyTier = "100% On-Device Offline",
            pricing = "Free / Offline Hardware",
            tags = listOf("On-Device", "Zero-Latency", "Offline")
        )
    )

    private var dynamicCatalog: List<ModelCatalogItem> = baseModels

    private val _uiState = MutableStateFlow(
        ModelCatalogUiState(
            models = baseModels,
            totalModelsCount = baseModels.size
        )
    )
    val uiState: StateFlow<ModelCatalogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            openRouterModelService.fetchModels()
            kotlinx.coroutines.flow.combine(
                openRouterModelService.models,
                openRouterModelService.groqModels,
                openRouterModelService.geminiModels,
                openRouterModelService.openAiModels
            ) { orModels, groqList, geminiList, openAiList ->
                val groqItems = groqList.map { model ->
                    val ctxText = if (model.contextLength > 0) "${model.contextLength / 1000}k Context" else "Groq LPU"
                    ModelCatalogItem(
                        id = model.id,
                        name = model.name,
                        provider = "Groq Cloud",
                        category = if (model.id.contains("r1", ignoreCase = true) || model.id.contains("qwq", ignoreCase = true)) ModelCategory.REASONING_LLM else ModelCategory.FAST_LLM,
                        description = model.description.ifBlank { "Groq LPU accelerated inference" },
                        latencyTier = ctxText,
                        pricing = model.quotaNote.ifBlank { "Groq LPU" },
                        tags = listOf("Groq", "LPU", if (model.isFreeTier) "Free Tier" else "Production")
                    )
                }
                val geminiItems = geminiList.map { model ->
                    val ctxText = when {
                        model.contextLength >= 1_000_000 -> "${model.contextLength / 1_000_000}M Context"
                        model.contextLength > 0 -> "${model.contextLength / 1000}k Context"
                        else -> "Google AI"
                    }
                    ModelCatalogItem(
                        id = model.id,
                        name = model.name,
                        provider = "Google AI Studio",
                        category = if (model.id.contains("pro", ignoreCase = true)) ModelCategory.REASONING_LLM else ModelCategory.FAST_LLM,
                        description = model.description.ifBlank { "Google AI Studio multimodal model" },
                        latencyTier = ctxText,
                        pricing = model.quotaNote.ifBlank { "Google AI Studio" },
                        tags = listOf("Google", "Multimodal", if (model.isFreeTier) "Free Tier" else "Standard")
                    )
                }
                val openAiItems = openAiList.map { model ->
                    val ctxText = if (model.contextLength > 0) "${model.contextLength / 1000}k Context" else "OpenAI"
                    ModelCatalogItem(
                        id = model.id,
                        name = model.name,
                        provider = "OpenAI",
                        category = if (model.id.contains("o1", ignoreCase = true) || model.id.equals("gpt-4o", ignoreCase = true)) ModelCategory.REASONING_LLM else ModelCategory.FAST_LLM,
                        description = model.description.ifBlank { "Official OpenAI foundation model" },
                        latencyTier = ctxText,
                        pricing = model.quotaNote.ifBlank { "OpenAI API" },
                        tags = listOf("OpenAI", if (model.isFreeTier) "Low Cost" else "Tier 1+")
                    )
                }
                val openRouterItems = orModels.map { model ->
                    val ctxText = when {
                        model.contextLength >= 1_000_000 -> "${model.contextLength / 1_000_000}M Context"
                        model.contextLength > 0 -> "${model.contextLength / 1000}k Context"
                        else -> "OpenRouter Gateway"
                    }
                    ModelCatalogItem(
                        id = model.id,
                        name = model.name,
                        provider = "OpenRouter",
                        category = if (model.isPopular) ModelCategory.REASONING_LLM else ModelCategory.FAST_LLM,
                        description = model.description.ifBlank { "Available via OpenRouter unified gateway." },
                        latencyTier = ctxText,
                        pricing = model.quotaNote.ifBlank { "BYOK / Dynamic" },
                        tags = if (model.isFreeTier) listOf("Free Tier", "OpenRouter", "BYOK") else listOf("OpenRouter", "BYOK")
                    )
                }
                dynamicCatalog = (baseModels + groqItems + geminiItems + openAiItems + openRouterItems).distinctBy { it.id.lowercase() }
                _uiState.update { current ->
                    current.copy(
                        models = filterModels(current.selectedCategory, current.searchQuery),
                        totalModelsCount = dynamicCatalog.size
                    )
                }
            }.collect {}
        }
    }

    fun selectCategory(category: ModelCategory) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                models = filterModels(category, current.searchQuery),
                totalModelsCount = dynamicCatalog.size
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                models = filterModels(current.selectedCategory, query),
                totalModelsCount = dynamicCatalog.size
            )
        }
    }

    private fun filterModels(category: ModelCategory, query: String): List<ModelCatalogItem> {
        return dynamicCatalog.filter { item ->
            val matchesCategory = category == ModelCategory.ALL || item.category == category
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.provider.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true) ||
                item.tags.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }
}


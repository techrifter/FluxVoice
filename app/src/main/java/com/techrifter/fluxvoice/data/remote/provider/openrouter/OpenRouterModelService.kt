package com.techrifter.fluxvoice.data.remote.provider.openrouter

import android.util.Log
import com.techrifter.fluxvoice.data.security.KeyVault
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ModelCatalogService"
private const val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"
private const val GROQ_MODELS_URL = "https://api.groq.com/openai/v1/models"
private const val OPENAI_MODELS_URL = "https://api.openai.com/v1/models"

data class OpenRouterModel(
    val id: String,
    val name: String,
    val contextLength: Int = 0,
    val description: String = "",
    val isPopular: Boolean = false,
    val provider: String = "openrouter",
    val isFreeTier: Boolean = false,
    val quotaNote: String = ""
)

@Singleton
class OpenRouterModelService @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault
) {

    // ── Pre-Cached Popular Models for Instant 0ms Zero-Latency Selection ───────
    val popularGroq = listOf(
        OpenRouterModel(
            id = "openai/gpt-oss-120b",
            name = "GPT OSS 120B",
            contextLength = 131_072,
            description = "High-performance open reasoning model running on Groq LPU.",
            isPopular = true,
            provider = "groq",
            isFreeTier = true,
            quotaNote = "Groq LPU · Active"
        ),
        OpenRouterModel(
            id = "openai/gpt-oss-20b",
            name = "GPT OSS 20B",
            contextLength = 131_072,
            description = "Fast, lightweight open conversational model running on Groq LPU.",
            isPopular = true,
            provider = "groq",
            isFreeTier = true,
            quotaNote = "Groq LPU · Active"
        ),
        OpenRouterModel(
            id = "qwen/qwen3.8-27b",
            name = "Qwen 3.8 27B",
            contextLength = 131_072,
            description = "Multilingual coding and conversational reasoning on Groq LPU.",
            isPopular = true,
            provider = "groq",
            isFreeTier = true,
            quotaNote = "Groq LPU · Active"
        ),
        OpenRouterModel(
            id = "allam-2-7b",
            name = "ALLaM 2 7B",
            contextLength = 4_096,
            description = "Bilingual conversational intelligence on Groq LPU.",
            isPopular = false,
            provider = "groq",
            isFreeTier = true,
            quotaNote = "Groq LPU · Active"
        )
    )

    val popularModels: List<OpenRouterModel> get() = _openRouterModels.value.filter { it.isPopular || it.isFreeTier }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Dynamic model state caches per provider — loaded live from open directories without hardcoded lists
    private val _openRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val models: StateFlow<List<OpenRouterModel>> = _openRouterModels.asStateFlow()

    private val _groqModels = MutableStateFlow(popularGroq)
    val groqModels: StateFlow<List<OpenRouterModel>> = _groqModels.asStateFlow()

    private val _openAiModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openAiModels: StateFlow<List<OpenRouterModel>> = _openAiModels.asStateFlow()

    private val _geminiModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val geminiModels: StateFlow<List<OpenRouterModel>> = _geminiModels.asStateFlow()

    // ── 1. OpenRouter Live Discovery (Public Endpoint) ────────────────────────
    suspend fun fetchModels(forceRefresh: Boolean = false): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        if (_openRouterModels.value.isNotEmpty() && !forceRefresh) {
            return@withContext _openRouterModels.value
        }
        _isLoading.value = true
        try {
            val key = keyVault.getOpenRouterKey()
            val response = httpClient.get(OPENROUTER_MODELS_URL) {
                if (key.isNotBlank()) header("Authorization", "Bearer ${key.trim()}")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonRoot["data"]?.jsonArray ?: return@withContext _openRouterModels.value

                val fetched = dataArray.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: id
                    val ctx = obj["context_length"]?.jsonPrimitive?.intOrNull ?: 0
                    val desc = obj["description"]?.jsonPrimitive?.content ?: ""
                    val isFree = id.endsWith(":free", ignoreCase = true)
                    val isPop = isFree || id.contains("llama-3.3-70b", ignoreCase = true) || id.contains("deepseek", ignoreCase = true) || id.contains("gemini", ignoreCase = true) || id.contains("gpt-4o", ignoreCase = true)
                    val note = if (isFree) "100% Free · $0 Credits" else "Paid Credits Required"
                    OpenRouterModel(id, name, ctx, desc, isPop, "openrouter", isFreeTier = isFree, quotaNote = note)
                }
                // Sort with free-tier and popular models on top
                val sorted = fetched
                    .distinctBy { it.id.lowercase() }
                    .sortedWith(compareByDescending<OpenRouterModel> { it.isFreeTier }.thenByDescending { it.isPopular })
                _openRouterModels.value = sorted

                // Dynamically sync Google Gemini models from live OpenRouter catalog if user has no direct key
                if (keyVault.getGeminiKey().isBlank()) {
                    val googleModels = fetched
                        .filter { it.id.startsWith("google/gemini", ignoreCase = true) || it.id.startsWith("google/gemma", ignoreCase = true) }
                        .map { item ->
                            val cleanId = item.id.removePrefix("google/").removeSuffix(":free")
                            val cleanName = item.name
                                .removePrefix("Google: ")
                                .replace(" (free)", "", ignoreCase = true)
                                .trim()
                            val isFree = cleanId.contains("flash", ignoreCase = true) || cleanId.contains("gemma", ignoreCase = true) || item.isFreeTier
                            val note = when {
                                cleanId.contains("flash-lite", ignoreCase = true) -> "Free Tier · 30 RPM / 1M TPM"
                                cleanId.contains("flash", ignoreCase = true)      -> "Free Tier · 15 RPM / 1M TPM"
                                cleanId.contains("pro", ignoreCase = true)        -> "Free Tier: 2 RPM Limit"
                                else                                              -> "Google AI Studio"
                            }
                            OpenRouterModel(
                                id = cleanId,
                                name = cleanName,
                                contextLength = item.contextLength,
                                description = item.description.ifBlank { "Google AI Studio multimodal model" },
                                isPopular = cleanId.contains("2.0-flash", ignoreCase = true) || cleanId.contains("1.5-flash", ignoreCase = true),
                                provider = "gemini",
                                isFreeTier = isFree,
                                quotaNote = note
                            )
                        }
                    if (googleModels.isNotEmpty()) {
                        _geminiModels.value = googleModels
                            .distinctBy { it.id.lowercase() }
                            .sortedWith(compareByDescending<OpenRouterModel> { it.isFreeTier }.thenByDescending { it.isPopular })
                    }
                }

                // Dynamically sync OpenAI models from live OpenRouter catalog if user has no direct key
                if (keyVault.getOpenAiKey().isBlank()) {
                    val openAiModels = fetched
                        .filter {
                            it.id.startsWith("openai/gpt-", ignoreCase = true) ||
                            it.id.startsWith("openai/o1", ignoreCase = true) ||
                            it.id.startsWith("openai/o3", ignoreCase = true) ||
                            it.id.startsWith("openai/chatgpt", ignoreCase = true)
                        }
                        .map { item ->
                            val cleanId = item.id.removePrefix("openai/").removeSuffix(":free")
                            val cleanName = item.name
                                .removePrefix("OpenAI: ")
                                .replace(" (free)", "", ignoreCase = true)
                                .trim()
                            val isFreeOrBudget = cleanId.contains("mini", ignoreCase = true) || cleanId.contains("3.5", ignoreCase = true)
                            val note = if (isFreeOrBudget) "Low Cost / Entry Tier" else "Paid Tier 1+ Required"
                            OpenRouterModel(
                                id = cleanId,
                                name = cleanName,
                                contextLength = item.contextLength,
                                description = item.description.ifBlank { "Official OpenAI foundation model" },
                                isPopular = cleanId.contains("4o-mini", ignoreCase = true) || cleanId.equals("gpt-4o", ignoreCase = true) || cleanId.contains("o3-mini", ignoreCase = true),
                                provider = "openai",
                                isFreeTier = isFreeOrBudget,
                                quotaNote = note
                            )
                        }
                    if (openAiModels.isNotEmpty()) {
                        _openAiModels.value = openAiModels
                            .distinctBy { it.id.lowercase() }
                            .sortedWith(compareByDescending<OpenRouterModel> { it.isFreeTier }.thenByDescending { it.isPopular })
                    }
                }

                return@withContext sorted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter models fetch failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
        return@withContext _openRouterModels.value
    }

    // ── 2. Groq Cloud Live Discovery (api.groq.com/openai/v1/models) ──────────
    suspend fun fetchGroqModels(forceRefresh: Boolean = false): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val key = keyVault.getGroqKey().trim()
        if (key.isBlank()) return@withContext _groqModels.value

        _isLoading.value = true
        try {
            val response = httpClient.get(GROQ_MODELS_URL) {
                header("Authorization", "Bearer $key")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonRoot["data"]?.jsonArray ?: return@withContext _groqModels.value

                val fetched = dataArray.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    // Filter out non-conversational audio, speech, and safety guard models
                    val outputModalities = obj["output_modalities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
                    if (outputModalities != null && !outputModalities.contains("text")) {
                        return@mapNotNull null
                    }
                    if (id.contains("whisper", ignoreCase = true) ||
                        id.contains("orpheus", ignoreCase = true) ||
                        id.contains("guard", ignoreCase = true) ||
                        id.contains("safeguard", ignoreCase = true)
                    ) {
                        return@mapNotNull null
                    }

                    val name = obj["name"]?.jsonPrimitive?.content ?: id
                    val ctx = obj["context_window"]?.jsonPrimitive?.intOrNull
                        ?: obj["context_length"]?.jsonPrimitive?.intOrNull
                        ?: 131_072
                    val owner = obj["owned_by"]?.jsonPrimitive?.content ?: "Groq"
                    val desc = "Groq LPU accelerated inference ($owner)"

                    OpenRouterModel(
                        id = id,
                        name = name,
                        contextLength = ctx,
                        description = desc,
                        isPopular = true,
                        provider = "groq",
                        isFreeTier = true,
                        quotaNote = "Active on your Groq key"
                    )
                }
                if (fetched.isNotEmpty()) {
                    val sorted = fetched.sortedBy { it.name }
                    _groqModels.value = sorted
                    Log.d(TAG, "Loaded ${sorted.size} live Groq models from API key")
                    return@withContext sorted
                }
                return@withContext _groqModels.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Groq models fetch failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
        return@withContext _groqModels.value
    }

    // ── 3. OpenAI Live Discovery (api.openai.com/v1/models) ───────────────────
    suspend fun fetchOpenAiModels(forceRefresh: Boolean = false): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val key = keyVault.getOpenAiKey().trim()
        if (key.isBlank()) return@withContext _openAiModels.value

        _isLoading.value = true
        try {
            val response = httpClient.get(OPENAI_MODELS_URL) {
                header("Authorization", "Bearer $key")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val dataArray = jsonRoot["data"]?.jsonArray ?: return@withContext _openAiModels.value

                val fetched = dataArray.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    // Only include conversational GPT / O1 models
                    val isChatModel = id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("chatgpt")
                    if (!isChatModel) return@mapNotNull null

                    val isPop = id.contains("4o", ignoreCase = true) || id.contains("o1", ignoreCase = true) || id.contains("o3", ignoreCase = true)
                    val name = id
                    val isFreeOrBudget = id.contains("mini", ignoreCase = true) || id.contains("3.5", ignoreCase = true)
                    val note = if (isFreeOrBudget) "Low Cost / Entry Tier" else "Paid Tier 1+ Required"
                    OpenRouterModel(
                        id = id,
                        name = name,
                        contextLength = 128_000,
                        description = "Official OpenAI foundation model",
                        isPopular = isPop,
                        provider = "openai",
                        isFreeTier = isFreeOrBudget,
                        quotaNote = note
                    )
                }
                if (fetched.isNotEmpty()) {
                    val sorted = fetched
                        .distinctBy { it.id.lowercase() }
                        .sortedWith(compareByDescending<OpenRouterModel> { it.isFreeTier }.thenByDescending { it.isPopular })
                    _openAiModels.value = sorted
                    Log.d(TAG, "Loaded ${sorted.size} live OpenAI models")
                    return@withContext sorted
                }
                return@withContext _openAiModels.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI models fetch failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
        return@withContext _openAiModels.value
    }

    // ── 4. Google Gemini Live Discovery (generativelanguage.googleapis.com) ───
    suspend fun fetchGeminiModels(forceRefresh: Boolean = false): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val key = keyVault.getGeminiKey().trim()
        if (key.isBlank()) return@withContext _geminiModels.value

        _isLoading.value = true
        try {
            val response = httpClient.get("https://generativelanguage.googleapis.com/v1beta/models") {
                header("x-goog-api-key", key)
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val modelsArray = jsonRoot["models"]?.jsonArray ?: return@withContext _geminiModels.value

                val fetched = modelsArray.mapNotNull { el ->
                    val obj = el.jsonObject
                    val rawName = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val cleanId = rawName.removePrefix("models/")
                    val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    if (!methods.contains("generateContent")) return@mapNotNull null

                    val displayName = obj["displayName"]?.jsonPrimitive?.content ?: cleanId
                    val desc = obj["description"]?.jsonPrimitive?.content ?: "Google AI Studio multimodal model"
                    val inputLimit = obj["inputTokenLimit"]?.jsonPrimitive?.intOrNull ?: 1_000_000
                    val isPop = cleanId.contains("flash", ignoreCase = true)
                    val isFreeTier = cleanId.contains("flash", ignoreCase = true)
                    val note = when {
                        cleanId.contains("2.0-flash-lite", ignoreCase = true) -> "Free Tier · 30 RPM / 1M TPM"
                        cleanId.contains("flash", ignoreCase = true)          -> "Free Tier · 15 RPM / 1M TPM"
                        cleanId.contains("pro", ignoreCase = true)            -> "Free Tier: 2 RPM Limit"
                        else                                                  -> "Google AI Studio"
                    }

                    OpenRouterModel(cleanId, displayName, inputLimit, desc, isPop, "gemini", isFreeTier = isFreeTier, quotaNote = note)
                }
                if (fetched.isNotEmpty()) {
                    val sorted = fetched
                        .distinctBy { it.id.lowercase() }
                        .sortedWith(compareByDescending<OpenRouterModel> { it.isFreeTier }.thenByDescending { it.isPopular })
                    _geminiModels.value = sorted
                    Log.d(TAG, "Loaded ${sorted.size} live Gemini models")
                    return@withContext sorted
                }
                return@withContext _geminiModels.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini models fetch failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
        return@withContext _geminiModels.value
    }

    /**
     * Returns the cached or popular models for the specified provider.
     */
    fun getModelsForProvider(providerId: String): StateFlow<List<OpenRouterModel>> = when (providerId.lowercase()) {
        "groq"                    -> groqModels
        "openai", "openai_compat" -> openAiModels
        "gemini"                  -> geminiModels
        else                      -> models
    }

    /**
     * Refreshes models for the specified provider from their official API.
     */
    suspend fun refreshProviderModels(providerId: String, forceRefresh: Boolean = true): List<OpenRouterModel> = when (providerId.lowercase()) {
        "groq"                    -> fetchGroqModels(forceRefresh)
        "openai", "openai_compat" -> if (keyVault.getOpenAiKey().isNotBlank()) fetchOpenAiModels(forceRefresh) else { fetchModels(forceRefresh); _openAiModels.value }
        "gemini"                  -> if (keyVault.getGeminiKey().isNotBlank()) fetchGeminiModels(forceRefresh) else { fetchModels(forceRefresh); _geminiModels.value }
        "openrouter"              -> fetchModels(forceRefresh)
        else                      -> emptyList()
    }
}

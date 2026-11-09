package com.techrifter.fluxvoice.data.remote.scenario

import android.content.Context
import android.util.Log
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.model.VoiceScenario
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceScenarioService"
private const val PREFS_NAME = "flux_voice_dynamic_scenarios"
private const val KEY_CACHED_SCENARIOS = "cached_scenarios_json"
private const val KEY_LAST_UPDATE = "last_scenarios_timestamp"

@Singleton
class VoiceScenarioService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault,
    private val settingsRepository: SettingsRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns instant scenarios on app startup (0ms latency).
     * Loads from cache if available and fresh (< 24 hours), otherwise returns time-adaptive prompts.
     */
    fun getInitialScenarios(): List<VoiceScenario> {
        val cached = loadCachedScenarios()
        if (cached.isNotEmpty()) return cached
        return getTimeAdaptiveScenarios()
    }

    /**
     * Generates or refreshes scenarios dynamically.
     * If an API key is available (Groq, Gemini, OpenAI, OpenRouter), queries the LLM for fresh trending prompts.
     * Otherwise, generates dynamically based on time of day and calendar context.
     */
    suspend fun refreshScenarios(): List<VoiceScenario> = withContext(Dispatchers.IO) {
        val aiScenarios = tryGenerateAiScenarios()
        if (aiScenarios.isNotEmpty()) {
            saveCachedScenarios(aiScenarios)
            return@withContext aiScenarios
        }

        // Fallback: Dynamic time & day algorithmic generator
        val timeBased = getTimeAdaptiveScenarios().shuffled()
        saveCachedScenarios(timeBased)
        timeBased
    }

    // ── 1. Live AI Scenario Generation ────────────────────────────────────────

    private suspend fun tryGenerateAiScenarios(): List<VoiceScenario> {
        val settings = settingsRepository.getSettings().firstOrNull()

        // 1. Try Groq (Ultra-fast <250ms LPU inference)
        val groqKey = keyVault.getGroqKey().ifBlank { settings?.groqApiKey ?: "" }
        if (groqKey.isNotBlank()) {
            try {
                val groqModel = settings?.modelId?.takeIf { it.isNotBlank() }
                    ?: "openai/gpt-oss-120b"
                val result = callOpenAiCompatibleLlm(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = groqKey,
                    model = groqModel
                )
                if (result.isNotEmpty()) return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Groq dynamic scenario generation failed: ${e.message}")
            }
        }

        // 2. Try Google Gemini (Fast multimodal, free tier)
        val geminiKey = keyVault.getGeminiKey().ifBlank { settings?.geminiApiKey ?: "" }
        if (geminiKey.isNotBlank()) {
            try {
                val result = callGeminiLlm(geminiKey)
                if (result.isNotEmpty()) return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini dynamic scenario generation failed: ${e.message}")
            }
        }

        // 3. Try OpenAI
        val openAiKey = keyVault.getOpenAiKey().ifBlank { settings?.openAiApiKey ?: "" }
        if (openAiKey.isNotBlank()) {
            try {
                val result = callOpenAiCompatibleLlm(
                    url = "https://api.openai.com/v1/chat/completions",
                    apiKey = openAiKey,
                    model = "gpt-4o-mini"
                )
                if (result.isNotEmpty()) return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OpenAI dynamic scenario generation failed: ${e.message}")
            }
        }

        // 4. Try OpenRouter
        val openRouterKey = keyVault.getOpenRouterKey()
        if (openRouterKey.isNotBlank()) {
            try {
                val result = callOpenAiCompatibleLlm(
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = openRouterKey,
                    model = "meta-llama/llama-3.3-70b-instruct"
                )
                if (result.isNotEmpty()) return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter dynamic scenario generation failed: ${e.message}")
            }
        }

        return emptyList()
    }

    private suspend fun callOpenAiCompatibleLlm(
        url: String,
        apiKey: String,
        model: String
    ): List<VoiceScenario> {
        val requestJson = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", "You are a curator for FluxVoice, an elite real-time voice AI assistant designed specifically for software engineers, hackers, tech founders, and hardware makers. Return ONLY a valid JSON array of 6 fresh, diverse, trending conversational starter prompts for a voice assistant. Format: [{\"icon\":\"<single emoji>\",\"title\":\"<2-4 words>\",\"subtitle\":\"<2-4 words>\",\"prompt\":\"<1-2 natural sentences for user to say aloud>\",\"category\":\"<1 word>\"}]. No markdown or markdown backticks.")
                }
                addJsonObject {
                    put("role", "user")
                    put("content", "Generate 6 captivating, timely voice conversation starters tailored for tech enthusiasts right now across developer tools, AI models, engineering deep work, hardware gear, system design, and techie lifestyle.")
                }
            }
            put("temperature", 0.85)
            put("max_tokens", 550)
        }.toString()

        val response = httpClient.post(url) {
            header("Authorization", "Bearer ${apiKey.trim()}")
            contentType(ContentType.Application.Json)
            headers.set("Authorization", "Bearer ${apiKey.trim()}")
            setBody(requestJson)
        }

        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val jsonRoot = json.parseToJsonElement(body).jsonObject
            val content = jsonRoot["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: return emptyList()

            return parseScenariosJson(content)
        }
        return emptyList()
    }

    private suspend fun callGeminiLlm(apiKey: String): List<VoiceScenario> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        val requestJson = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "Return a raw JSON array of 6 fresh, diverse, trending conversational voice starter prompts for software engineers, hackers, and tech enthusiasts. Format: [{\"icon\":\"<single emoji>\",\"title\":\"<2-4 words>\",\"subtitle\":\"<2-4 words>\",\"prompt\":\"<1-2 conversational sentences for user to say aloud>\",\"category\":\"<1 word>\"}]. Do not wrap in markdown. Cover AI breakthroughs, system architecture, hardware setups, deep productivity, and tech lifestyle.")
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.85)
                put("maxOutputTokens", 600)
                put("responseMimeType", "application/json")
            }
        }.toString()

        val response = httpClient.post(url) {
            header("x-goog-api-key", apiKey.trim())
            contentType(ContentType.Application.Json)
            setBody(requestJson)
        }

        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val jsonRoot = json.parseToJsonElement(body).jsonObject
            val text = jsonRoot["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content ?: return emptyList()

            return parseScenariosJson(text)
        }
        return emptyList()
    }

    private fun parseScenariosJson(raw: String): List<VoiceScenario> {
        val clean = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = clean.indexOf('[')
        val end = clean.lastIndexOf(']')
        if (start != -1 && end != -1 && end > start) {
            val arrayStr = clean.substring(start, end + 1)
            val jsonArray = json.parseToJsonElement(arrayStr).jsonArray
            return jsonArray.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val icon = obj["icon"]?.jsonPrimitive?.content ?: "⚡"
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val subtitle = obj["subtitle"]?.jsonPrimitive?.content ?: ""
                    val prompt = obj["prompt"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val category = obj["category"]?.jsonPrimitive?.content ?: "Tech"
                    VoiceScenario(icon, title, subtitle, prompt, category)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return emptyList()
    }

    // ── 2. Time-of-Day & Calendar Adaptive Procedural Matrix (Tech-Focused) ────

    fun getTimeAdaptiveScenarios(): List<VoiceScenario> {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        return when {
            hour in 5..11 -> getMorningScenarios(isWeekend)
            hour in 12..17 -> getAfternoonScenarios(isWeekend)
            else -> getEveningScenarios(isWeekend)
        }
    }

    private fun getMorningScenarios(isWeekend: Boolean): List<VoiceScenario> {
        val base = mutableListOf(
            VoiceScenario(
                icon = "⚡",
                title = "Hacker News & AI Brief",
                subtitle = "60-second tech brief",
                prompt = "Give me a high-bandwidth 60-second morning brief covering top Hacker News discussions, open-source AI releases, and major tech headlines today.",
                category = "News"
            ),
            VoiceScenario(
                icon = "🎯",
                title = "Priority Sprint",
                subtitle = "Top 3 architecture goals",
                prompt = "Help me identify and prioritize my top 3 high-impact engineering goals today using first-principles thinking, and block focused time before meetings.",
                category = "Focus"
            ),
            VoiceScenario(
                icon = "⏱️",
                title = "Latency Benchmark",
                subtitle = "Sub-second LPU speed test",
                prompt = "Explain in two punchy sentences why dedicated LPUs achieve lower time-to-first-token than GPU clusters for real-time voice streaming.",
                category = "Benchmark"
            ),
            VoiceScenario(
                icon = "🛑",
                title = "Barge-In Stress Test",
                subtitle = "Interrupt mid-sentence",
                prompt = "Start a detailed explanation of how Unix pipes and file descriptors work. Speak continuously—I am going to interrupt you to test voice barge-in.",
                category = "StressTest"
            ),
            VoiceScenario(
                icon = "🧩",
                title = "Systems Mental Model",
                subtitle = "Distributed systems logic",
                prompt = "Explain an elegant concept from distributed systems or operating systems, like CAP theorem or zero-copy memory, and how it applies to daily problem solving.",
                category = "Systems"
            ),
            VoiceScenario(
                icon = "🎙️",
                title = "Tech Audio Warm-up",
                subtitle = "Interactive dev warmup",
                prompt = "Act as an interactive tech podcast host. Quiz me on an interesting technical problem or ask for my architectural take on software scalability.",
                category = "Warmup"
            )
        )
        if (isWeekend) {
            base[1] = VoiceScenario(
                icon = "🥞",
                title = "Skillet Brunch & Pour-Over",
                subtitle = "Cast-iron weekend recipe",
                prompt = "Guide me through a delicious 20-minute cast-iron skillet sourdough shakshuka recipe, along with tips on dialing in pour-over coffee grind size and ratio.",
                category = "Brunch"
            )
        }
        return base
    }

    private fun getAfternoonScenarios(isWeekend: Boolean): List<VoiceScenario> {
        val base = mutableListOf(
            VoiceScenario(
                icon = "⚡",
                title = "Voice Pipeline Audit",
                subtitle = "Analyze the latency critical path",
                prompt = "Analyze the latency critical path in a real-time voice AI loop across AudioRecord capture, WebSocket STT, LLM first token, and PCM audio playback. Where is the biggest latency bottleneck?",
                category = "Performance"
            ),
            VoiceScenario(
                icon = "💻",
                title = "Tech Buying Guide",
                subtitle = "Developer workstation & gear",
                prompt = "Help me evaluate the best lightweight developer laptops and ultrawide monitors on the market. Compare display panel specs, port bandwidth, and thermals.",
                category = "Hardware"
            ),
            VoiceScenario(
                icon = "🎙️",
                title = "Spoken Fluency Coach",
                subtitle = "Concise tech communication",
                prompt = "Act as my executive communication coach. Help me explain complex distributed system architecture to non-technical stakeholders concisely and convincingly.",
                category = "Leadership"
            ),
            VoiceScenario(
                icon = "📈",
                title = "Tech Wealth Compounding",
                subtitle = "Equity, RSUs & Index SIPs",
                prompt = "Walk me through the mathematical compounding advantage of disciplined Index Fund SIPs and equity management over 15 years, with concrete calculations.",
                category = "Wealth"
            ),
            VoiceScenario(
                icon = "🛠️",
                title = "Architecture Sounding Board",
                subtitle = "Tradeoffs & design debate",
                prompt = "Act as a principal software architect. I want to debate the architectural tradeoffs between event-driven streaming versus REST batching for real-time mobile apps.",
                category = "Architecture"
            ),
            VoiceScenario(
                icon = "🔬",
                title = "Edge vs Cloud LLM",
                subtitle = "Quantization & speed tradeoffs",
                prompt = "Explain the practical latency, memory, and voice synthesis quality tradeoffs between 4-bit on-device SLMs and ultra-fast cloud inference.",
                category = "Inference"
            )
        )
        if (isWeekend) {
            base[0] = VoiceScenario(
                icon = "🎨",
                title = "Creative Micro-Hobbies",
                subtitle = "Maker & homelab projects",
                prompt = "Recommend 3 rewarding creative projects for a techie this weekend, like tinkering with ESP32 microcontrollers, 3D printing custom desk gear, or home-lab self-hosting.",
                category = "Maker"
            )
        }
        return base
    }

    private fun getEveningScenarios(isWeekend: Boolean): List<VoiceScenario> {
        val base = mutableListOf(
            VoiceScenario(
                icon = "🌙",
                title = "Work Shutdown Ritual",
                subtitle = "Clear engineering debt",
                prompt = "Guide me through a structured 3-minute evening shutdown ritual to clear working memory, note open git branches and pull requests, and detach from work.",
                category = "Debrief"
            ),
            VoiceScenario(
                icon = "🏛️",
                title = "Semiconductor Geopolitics",
                subtitle = "ASML, TSMC & AI chip wars",
                prompt = "Give me a sharp, objective breakdown of the global semiconductor supply chain, ASML photolithography dominance, and leading-edge AI chip packaging battles.",
                category = "Geopolitics"
            ),
            VoiceScenario(
                icon = "🏕️",
                title = "Weekend Road Trip",
                subtitle = "Scenic digital detox route",
                prompt = "Help me plan a scenic weekend road trip itinerary away from screens, with great nature trails, local mountain food, and stargazing viewpoints.",
                category = "Travel"
            ),
            VoiceScenario(
                icon = "📖",
                title = "Key Engineering Lore",
                subtitle = "High Output Management",
                prompt = "Explain the most actionable framework from Andy Grove's High Output Management regarding leverage, task-relevant maturity, and engineering bottlenecks.",
                category = "Wisdom"
            ),
            VoiceScenario(
                icon = "🌌",
                title = "Cosmic Deep Space",
                subtitle = "Webb telescope & quantum",
                prompt = "Explain how the latest James Webb Space Telescope deep-field observations challenge standard galaxy formation models, or how quantum error correction works.",
                category = "Science"
            ),
            VoiceScenario(
                icon = "🎧",
                title = "Atmospheric Bedtime Wind-Down",
                subtitle = "Satellite observatory story",
                prompt = "Tell me a slow, soothing, beautifully descriptive bedtime story about an automated deep-space satellite in orbit around Saturn listening to radio waves in silent space.",
                category = "Story"
            )
        )
        if (isWeekend) {
            base[1] = VoiceScenario(
                icon = "🍿",
                title = "Documentary & Sci-Fi Cinema",
                subtitle = "Mind-bending weekend picks",
                prompt = "Recommend 2 compelling tech documentaries or cerebral sci-fi films with thought-provoking plots, high realism, and great cinematography.",
                category = "Cinema"
            )
        }
        return base
    }

    // ── 3. Persistent Local Caching ───────────────────────────────────────────

    private fun loadCachedScenarios(): List<VoiceScenario> {
        val raw = prefs.getString(KEY_CACHED_SCENARIOS, null) ?: return emptyList()
        val timestamp = prefs.getLong(KEY_LAST_UPDATE, 0L)
        // Refresh cache if older than 18 hours
        if (System.currentTimeMillis() - timestamp > 18 * 3600 * 1000L) {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<VoiceScenario>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedScenarios(scenarios: List<VoiceScenario>) {
        try {
            val encoded = json.encodeToString(scenarios)
            prefs.edit()
                .putString(KEY_CACHED_SCENARIOS, encoded)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache scenarios: ${e.message}")
        }
    }
}

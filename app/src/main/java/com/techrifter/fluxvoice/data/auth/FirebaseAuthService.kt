package com.techrifter.fluxvoice.data.auth

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "FirebaseAuthService"

@Singleton
class FirebaseAuthService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    val currentUid: String? get() = auth.currentUser?.uid
    val currentUsername: String? get() = auth.currentUser?.displayName
    val isAnonymousSignedIn: Boolean get() = auth.currentUser != null

    /**
     * Signs the user in anonymously via Firebase Auth, sets their public anonymous username,
     * persists their metadata doc to Firestore under /anonymous_users/{uid}, and saves
     * their preferences locally.
     */
    suspend fun initializeAnonymousIdentity(
        username: String
    ): Result<String> {
        val cleanUsername = username.trim().ifBlank { generateRandomUsername() }
        return try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            val user = auth.currentUser ?: error("Firebase user is null after anonymous sign-in")
            val uid = user.uid

            // 1. Update Firebase Auth user display name
            try {
                val profileChange = userProfileChangeRequest {
                    displayName = cleanUsername
                }
                user.updateProfile(profileChange).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating auth profile displayName: ${e.message}")
            }

            // 2. Persist to Firestore under anonymous_users collection and initialize modular collections
            try {
                val batch = firestore.batch()
                val telemetryOptIn = settingsRepository.getSettings().firstOrNull()?.telemetryOptIn == true

                // 2a. anonymous_users/{uid}
                val userDocRef = firestore.collection("anonymous_users").document(uid)
                val userDoc = hashMapOf<String, Any>(
                    "uid" to uid,
                    "username" to cleanUsername,
                    "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "androidSdk" to android.os.Build.VERSION.SDK_INT,
                    "createdAt" to System.currentTimeMillis(),
                    "telemetryOptIn" to telemetryOptIn
                )
                batch.set(userDocRef, userDoc, SetOptions.merge())

                // 2b. user_model_stats/{uid}
                val userStatsRef = firestore.collection("user_model_stats").document(uid)
                val userStatsDoc = hashMapOf<String, Any>(
                    "uid" to uid,
                    "username" to cleanUsername,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "totalTurns" to 0,
                    "totalTokens" to 0L,
                    "bargeInCount" to 0
                )
                batch.set(userStatsRef, userStatsDoc, SetOptions.merge())

                batch.commit().await()
                Log.d(TAG, "Anonymous profile successfully persisted to Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed persisting profile to Firestore (may be offline): ${e.message}")
            }

            // 3. Save locally in DataStore so user is permanently authenticated locally
            settingsRepository.updateAnonymousIdentity(cleanUsername)

            Log.i(TAG, "Anonymous identity created successfully")
            Result.success(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase anonymous sign-in failed, falling back to local-only identity: ${e.message}", e)
            // Safety net: preserve local-first guarantee. If device is offline, still let them proceed!
            settingsRepository.updateAnonymousIdentity(cleanUsername)
            Result.success(auth.currentUser?.uid ?: "local_anon_${System.currentTimeMillis()}")
        }
    }

    /**
     * Generates a friendly, modern anonymous username.
     */
    fun generateRandomUsername(): String {
        val names = listOf(
            "Alex", "Jordan", "Taylor", "Morgan", "Sam", "Chris", "Nova", "Echo",
            "Aero", "Atlas", "Sage", "River", "Kai", "Rowan", "Orion", "Pixel",
            "Casey", "Logan", "Riley", "Avery", "Sky", "Robin", "Finley", "Eden"
        )
        val suffixes = listOf(
            "Dev", "Tech", "Studio", "Voice", "Flow", "Wave", "Craft",
            "Spark", "Cloud", "User", "Explorer", "Maker"
        )
        val name = names.random()
        val suffix = suffixes.random()
        val num = Random.nextInt(10, 99)
        return "$name$suffix-$num"
    }

    /**
     * Updates the anonymous developer username locally, updates Firebase Auth display name,
     * and synchronizes the username with Firestore.
     */
    suspend fun updateUsername(newUsername: String): Result<String> {
        val cleanUsername = newUsername.trim().ifBlank { generateRandomUsername() }
        val user = auth.currentUser
        if (user != null) {
            try {
                val profileChange = userProfileChangeRequest {
                    displayName = cleanUsername
                }
                user.updateProfile(profileChange).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating auth profile display name: ${e.message}")
            }

            try {
                val batch = firestore.batch()
                val userRef = firestore.collection("anonymous_users").document(user.uid)
                batch.set(userRef, mapOf(
                    "username" to cleanUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())

                val userStatsRef = firestore.collection("user_model_stats").document(user.uid)
                batch.set(userStatsRef, mapOf(
                    "username" to cleanUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())

                batch.commit().await()
                Log.d(TAG, "Username successfully updated in Firestore: $cleanUsername")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating username in Firestore (proceeding with local update): ${e.message}")
            }
        }
        settingsRepository.updateAnonymousIdentity(cleanUsername)
        return Result.success(cleanUsername)
    }

    suspend fun updateTelemetryConsent(enabled: Boolean) {
        val uid = currentUid ?: return
        try {
            firestore.collection("anonymous_users")
                .document(uid)
                .set(
                    mapOf(
                        "telemetryOptIn" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating telemetry consent in Firestore: ${e.message}")
        }
    }

    /**
     * Records anonymous, privacy-preserving turn telemetry across dedicated, modular
     * Firestore collections to eliminate document clutter and ensure high scalability.
     *
     * ARCHITECTURE & COLLECTION PARTITIONING:
     * 1. `anonymous_users/{uid}`: Lightweight user account metadata & activity heartbeat only.
     * 2. `user_model_stats/{uid}`: Per-user model usage frequency and preferences.
     * 3. `model_telemetry/{domain}/shards/{uid}`: Per-user model/provider usage counters.
     * 4. `latency_telemetry/realtime_averages/shards/{uid}`: Per-user latency benchmarks.
     *
     * STRICT LOCAL PRIVACY GUARANTEE:
     * - Zero transcripts or conversational content are sent (kept 100% locally on device).
     * - Zero user audio recordings or PCM buffers are sent.
     * - Zero API keys, tokens, or custom endpoints are sent.
     */
    suspend fun recordTurnTelemetry(
        sttProvider: String,
        llmModel: String,
        ttsProvider: String,
        ttftMs: Long,
        ttfaMs: Long,
        totalTurnMs: Long,
        tokens: Int,
        bargeInOccurred: Boolean,
        pipelineName: String
    ) {
        val uid = currentUid ?: return
        try {
            if (settingsRepository.getSettings().firstOrNull()?.telemetryOptIn != true) return
            val sanitizedModelKey = telemetryFieldKey(llmModel)
            val sanitizedSttKey = telemetryFieldKey(sttProvider)
            val sanitizedTtsKey = telemetryFieldKey(ttsProvider)
            val sanitizedPipeKey = telemetryFieldKey(pipelineName)

            val batch = firestore.batch()

            // ── 1. Lightweight User Heartbeat (anonymous_users collection) ─────────
            val userHeartbeat = hashMapOf<String, Any>(
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "totalLifetimeTurns" to FieldValue.increment(1)
            )
            val userRef = firestore.collection("anonymous_users").document(uid)
            batch.set(userRef, userHeartbeat, SetOptions.merge())

            // ── 2. Per-User Model Usage (user_model_stats collection) ───────────────
            val userModelStats = hashMapOf<String, Any>(
                "uid" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastPipeline" to pipelineName,
                "mostRecentLlm" to llmModel,
                "mostRecentStt" to sttProvider,
                "mostRecentTts" to ttsProvider,
                "totalTurns" to FieldValue.increment(1),
                "totalTokens" to FieldValue.increment(tokens.toLong()),
                "bargeInCount" to FieldValue.increment(if (bargeInOccurred) 1 else 0),
                "llmUsage" to mapOf(sanitizedModelKey to FieldValue.increment(1)),
                "sttUsage" to mapOf(sanitizedSttKey to FieldValue.increment(1)),
                "ttsUsage" to mapOf(sanitizedTtsKey to FieldValue.increment(1))
            )
            val userStatsRef = firestore.collection("user_model_stats").document(uid)
            batch.set(userStatsRef, userModelStats, SetOptions.merge())

            // ── 3. Per-user telemetry shards ─────────────────────────────────
            // Per-user shards avoid contention on shared singleton counter documents.
            val llmTelemetry = hashMapOf<String, Any>(
                "totalQueries" to FieldValue.increment(1),
                "totalTokens" to FieldValue.increment(tokens.toLong()),
                "models" to mapOf(sanitizedModelKey to FieldValue.increment(1)),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            val llmRef = firestore.collection("model_telemetry")
                .document("llm_usage")
                .collection("shards")
                .document(uid)
            batch.set(llmRef, llmTelemetry, SetOptions.merge())

            val sttTelemetry = hashMapOf<String, Any>(
                "totalTranscriptions" to FieldValue.increment(1),
                "providers" to mapOf(sanitizedSttKey to FieldValue.increment(1)),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            val sttRef = firestore.collection("model_telemetry")
                .document("stt_usage")
                .collection("shards")
                .document(uid)
            batch.set(sttRef, sttTelemetry, SetOptions.merge())

            val ttsTelemetry = hashMapOf<String, Any>(
                "totalSyntheses" to FieldValue.increment(1),
                "providers" to mapOf(sanitizedTtsKey to FieldValue.increment(1)),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            val ttsRef = firestore.collection("model_telemetry")
                .document("tts_usage")
                .collection("shards")
                .document(uid)
            batch.set(ttsRef, ttsTelemetry, SetOptions.merge())

            val pipeTelemetry = hashMapOf<String, Any>(
                "totalTurns" to FieldValue.increment(1),
                "pipelines" to mapOf(sanitizedPipeKey to FieldValue.increment(1)),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            val pipeRef = firestore.collection("model_telemetry")
                .document("pipeline_usage")
                .collection("shards")
                .document(uid)
            batch.set(pipeRef, pipeTelemetry, SetOptions.merge())

            // ── 4. Latency & Performance Benchmarks (latency_telemetry collection) ──
            if (ttftMs > 0 || ttfaMs > 0 || totalTurnMs > 0) {
                val latencyData = hashMapOf<String, Any>(
                    "measuredTurns" to FieldValue.increment(1),
                    "bargeInsDetected" to FieldValue.increment(if (bargeInOccurred) 1 else 0),
                    "sumTtftMs" to FieldValue.increment(ttftMs),
                    "sumTtfaMs" to FieldValue.increment(ttfaMs),
                    "sumTotalTurnMs" to FieldValue.increment(totalTurnMs),
                    "latestBenchmark" to hashMapOf(
                        "llmModel" to llmModel,
                        "sttProvider" to sttProvider,
                        "ttsProvider" to ttsProvider,
                        "ttftMs" to ttftMs,
                        "ttfaMs" to ttfaMs,
                        "totalTurnMs" to totalTurnMs,
                        "tokens" to tokens,
                        "bargeIn" to bargeInOccurred,
                        "timestamp" to System.currentTimeMillis()
                    ),
                    "lastUpdated" to FieldValue.serverTimestamp()
                )
                val latencyRef = firestore.collection("latency_telemetry")
                    .document("realtime_averages")
                    .collection("shards")
                    .document(uid)
                batch.set(latencyRef, latencyData, SetOptions.merge())
            }

            batch.commit().await()
            Log.d(TAG, "Partitioned anonymous telemetry batch successfully committed to Firestore")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed recording partitioned telemetry (offline or network failure): ${e.message}", e)
        }
    }

    /**
     * Anonymously logs operational pipeline errors to Firebase Analytics.
     *
     * STRICT PRIVACY GUARANTEES:
     * - ZERO user prompts, audio, or conversational transcripts are logged.
     * - ZERO API keys, tokens, or custom endpoints are logged.
     * - Only operational metadata is recorded: stage ("STT", "LLM", "TTS"),
     *   provider ("groq", "openai", "gemini"), sanitized error category/code ("HTTP_429", "HTTP_401", "TIMEOUT").
     */
    fun recordPipelineError(
        stage: String,
        provider: String,
        errorCode: String,
        errorMessage: String = "",
        model: String = ""
    ) {
        try {
            val bundle = Bundle().apply {
                putString("stage", stage.take(32))
                putString("provider", provider.take(32))
                putString("error_code", errorCode.take(32))
                if (model.isNotBlank()) putString("model", model.take(50))
                if (errorMessage.isNotBlank()) {
                    val sanitized = sanitizeErrorMessage(errorMessage).take(100)
                    putString("error_detail", sanitized)
                }
            }
            analytics.logEvent("pipeline_error", bundle)
            Log.d(TAG, "Logged pipeline_error event to Firebase Analytics: stage=$stage provider=$provider code=$errorCode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed logging pipeline error to Firebase Analytics: ${e.message}")
        }
    }

    fun extractErrorCode(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("429") || lower.contains("rate limit") -> "HTTP_429"
            lower.contains("401") || lower.contains("unauthor") || lower.contains("invalid") -> "HTTP_401"
            lower.contains("403") || lower.contains("forbidden") -> "HTTP_403"
            lower.contains("404") || lower.contains("not found") -> "HTTP_404"
            lower.contains("400") || lower.contains("bad request") -> "HTTP_400"
            lower.contains("500") || lower.contains("503") || lower.contains("server error") -> "HTTP_SERVER_ERROR"
            lower.contains("timeout") -> "TIMEOUT"
            lower.contains("connect") || lower.contains("network") -> "NETWORK_ERROR"
            lower.contains("websocket") -> "WS_ERROR"
            else -> "PIPELINE_ERROR"
        }
    }

    private fun sanitizeErrorMessage(raw: String): String = raw
        .replace(Regex("(Bearer\\s+)[A-Za-z0-9_.-]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        .replace(Regex("(key=|token=|api_key=)[A-Za-z0-9_.-]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        .replace(Regex("https?://[^\\s]+"), "[URL]")
        .trim()

    private fun telemetryFieldKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .take(100)
            .ifBlank { "unknown" }
}

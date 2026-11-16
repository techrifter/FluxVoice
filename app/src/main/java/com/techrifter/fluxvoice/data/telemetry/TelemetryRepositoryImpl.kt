package com.techrifter.fluxvoice.data.telemetry

import android.util.Log
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.techrifter.fluxvoice.domain.repository.TelemetryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

private const val TAG = "TelemetryRepository"

@Singleton
class TelemetryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : TelemetryRepository {

    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    override suspend fun recordTurnTelemetry(
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
        val uid = auth.currentUser?.uid ?: return
        
        try {
            val date = getCurrentUtcDate()
            val sanitizedModelKey = telemetryFieldKey(llmModel)
            val sanitizedSttKey = telemetryFieldKey(sttProvider)
            val sanitizedTtsKey = telemetryFieldKey(ttsProvider)
            val sanitizedPipeKey = telemetryFieldKey(pipelineName)
            
            val pipelineKey = "${sanitizedPipeKey}__${sanitizedSttKey}__${sanitizedModelKey}__${sanitizedTtsKey}"
            
            val ttftBucket = getTtftBucket(ttftMs)
            val ttfaBucket = getTtfaBucket(ttfaMs)
            val turnBucket = getTotalTurnBucket(totalTurnMs)
            
            val batch = firestore.batch()
            
            val commonUpdates = mutableMapOf<String, Any>(
                "date" to date,
                "pipeline" to pipelineName,
                "sttProvider" to sttProvider,
                "llmModel" to llmModel,
                "ttsProvider" to ttsProvider,
                "turns" to FieldValue.increment(1),
                "tokens" to FieldValue.increment(tokens.toLong()),
                "bargeIns" to FieldValue.increment(if (bargeInOccurred) 1 else 0),
                "sumTtftMs" to FieldValue.increment(ttftMs),
                "sumTtfaMs" to FieldValue.increment(ttfaMs),
                "sumTotalTurnMs" to FieldValue.increment(totalTurnMs),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            
            if (ttftMs > 0) {
                commonUpdates["ttftSamples"] = FieldValue.increment(1)
                commonUpdates["ttftBuckets.$ttftBucket"] = FieldValue.increment(1)
            }
            if (ttfaMs > 0) {
                commonUpdates["ttfaSamples"] = FieldValue.increment(1)
                commonUpdates["ttfaBuckets.$ttfaBucket"] = FieldValue.increment(1)
            }
            if (totalTurnMs > 0) {
                commonUpdates["totalTurnSamples"] = FieldValue.increment(1)
                commonUpdates["totalTurnBuckets.$turnBucket"] = FieldValue.increment(1)
            }
            
            // 1. User daily pipeline document
            val userUpdates = HashMap(commonUpdates)
            userUpdates["uid"] = uid
            val userRef = firestore.collection("telemetry_daily")
                .document(date)
                .collection("users")
                .document(uid)
                .collection("pipeline_stats")
                .document(pipelineKey)
                
            batch.set(userRef, userUpdates, SetOptions.merge())
            
            // 2. Global pipeline shard
            val shardId = Random.nextInt(20).toString().padStart(2, '0')
            val shardRef = firestore.collection("telemetry_daily")
                .document(date)
                .collection("global_pipelines")
                .document(pipelineKey)
                .collection("shards")
                .document("shard_$shardId")
                
            batch.set(shardRef, commonUpdates, SetOptions.merge())
            
            batch.commit().await()
            Log.d(TAG, "Successfully recorded telemetry for $pipelineKey")
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed recording telemetry: ${e.message}", e)
        }
    }

    override fun recordPipelineError(
        stage: String,
        provider: String,
        errorCode: String,
        errorMessage: String,
        model: String
    ) {
        try {
            val code = errorCode.ifBlank { extractErrorCode(errorMessage) }
            val bundle = Bundle().apply {
                putString("stage", stage.take(32))
                putString("provider", provider.take(32))
                putString("error_code", code.take(32))
                if (model.isNotBlank()) putString("model", model.take(50))
                if (errorMessage.isNotBlank()) {
                    val sanitized = sanitizeErrorMessage(errorMessage).take(100)
                    putString("error_detail", sanitized)
                }
            }
            analytics.logEvent("pipeline_error", bundle)
            Log.d(TAG, "Logged pipeline_error event to Firebase Analytics: stage=$stage provider=$provider code=$code")
        } catch (e: Exception) {
            Log.w(TAG, "Failed logging pipeline error to Firebase Analytics: ${e.message}")
        }
    }

    private fun extractErrorCode(message: String): String {
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

    private fun getCurrentUtcDate(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
    
    private fun getTtftBucket(ms: Long): String = when {
        ms < 200 -> "lt200"
        ms < 400 -> "200_400"
        ms < 600 -> "400_600"
        ms < 800 -> "600_800"
        ms < 1200 -> "800_1200"
        ms < 2000 -> "1200_2000"
        else -> "gt2000"
    }

    private fun getTtfaBucket(ms: Long): String = when {
        ms < 200 -> "lt200"
        ms < 400 -> "200_400"
        ms < 600 -> "400_600"
        ms < 800 -> "600_800"
        ms < 1200 -> "800_1200"
        ms < 2000 -> "1200_2000"
        else -> "gt2000"
    }

    private fun getTotalTurnBucket(ms: Long): String = when {
        ms < 1000 -> "lt1000"
        ms < 2000 -> "1000_2000"
        ms < 3000 -> "2000_3000"
        ms < 5000 -> "3000_5000"
        ms < 8000 -> "5000_8000"
        ms < 12000 -> "8000_12000"
        else -> "gt12000"
    }

    private fun telemetryFieldKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .take(100)
            .ifBlank { "unknown" }
}

package com.techrifter.fluxvoice.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "turn_benchmarks")
data class TurnBenchmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pipelineId: String,
    val pipelineName: String,
    val timestamp: Long,
    val sttLatencyMs: Long,
    val ttftMs: Long,
    val ttfaMs: Long,
    val totalTurnMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double,
    val bargeInOccurred: Boolean,
    val userTranscriptionSnippet: String,
    val assistantResponseSnippet: String,
    val isTokensEstimated: Boolean = false,
    val sttProvider: String = "",
    val llmModel: String = "",
    val ttsProvider: String = ""
)


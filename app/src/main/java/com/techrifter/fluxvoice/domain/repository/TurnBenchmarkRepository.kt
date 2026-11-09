package com.techrifter.fluxvoice.domain.repository

import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkEntity
import kotlinx.coroutines.flow.Flow

interface TurnBenchmarkRepository {
    fun getAllBenchmarks(): Flow<List<TurnBenchmarkEntity>>
    fun getBenchmarksForPipeline(pipelineId: String): Flow<List<TurnBenchmarkEntity>>
    fun getRecentBenchmarks(limit: Int = 20): Flow<List<TurnBenchmarkEntity>>
    suspend fun recordBenchmark(benchmark: TurnBenchmarkEntity)
    suspend fun clearBenchmarksForPipeline(pipelineId: String)
    suspend fun clearAllBenchmarks()
}


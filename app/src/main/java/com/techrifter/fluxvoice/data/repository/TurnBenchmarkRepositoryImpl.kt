package com.techrifter.fluxvoice.data.repository

import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkDao
import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkEntity
import com.techrifter.fluxvoice.domain.repository.TurnBenchmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_RETAINED_BENCHMARKS = 1_000

@Singleton
class TurnBenchmarkRepositoryImpl @Inject constructor(
    private val turnBenchmarkDao: TurnBenchmarkDao
) : TurnBenchmarkRepository {

    override fun getAllBenchmarks(): Flow<List<TurnBenchmarkEntity>> =
        turnBenchmarkDao.getAllBenchmarks()

    override fun getBenchmarksForPipeline(pipelineId: String): Flow<List<TurnBenchmarkEntity>> =
        turnBenchmarkDao.getBenchmarksForPipeline(pipelineId)

    override fun getRecentBenchmarks(limit: Int): Flow<List<TurnBenchmarkEntity>> =
        turnBenchmarkDao.getRecentBenchmarks(limit)

    override suspend fun recordBenchmark(benchmark: TurnBenchmarkEntity) {
        turnBenchmarkDao.insertBenchmark(benchmark)
        turnBenchmarkDao.deleteOlderThan(MAX_RETAINED_BENCHMARKS)
    }

    override suspend fun clearBenchmarksForPipeline(pipelineId: String) =
        turnBenchmarkDao.clearBenchmarksForPipeline(pipelineId)

    override suspend fun clearAllBenchmarks() =
        turnBenchmarkDao.clearAllBenchmarks()
}

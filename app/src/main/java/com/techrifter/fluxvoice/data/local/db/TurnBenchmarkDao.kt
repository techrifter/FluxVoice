package com.techrifter.fluxvoice.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TurnBenchmarkDao {

    @Query("SELECT * FROM turn_benchmarks ORDER BY timestamp DESC")
    fun getAllBenchmarks(): Flow<List<TurnBenchmarkEntity>>

    @Query("SELECT * FROM turn_benchmarks WHERE pipelineId = :pipelineId ORDER BY timestamp DESC")
    fun getBenchmarksForPipeline(pipelineId: String): Flow<List<TurnBenchmarkEntity>>

    @Query("SELECT * FROM turn_benchmarks ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBenchmarks(limit: Int): Flow<List<TurnBenchmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(benchmark: TurnBenchmarkEntity)

    @Query(
        "DELETE FROM turn_benchmarks WHERE id NOT IN " +
            "(SELECT id FROM turn_benchmarks ORDER BY timestamp DESC LIMIT :retainedCount)"
    )
    suspend fun deleteOlderThan(retainedCount: Int)

    @Query("DELETE FROM turn_benchmarks WHERE pipelineId = :pipelineId")
    suspend fun clearBenchmarksForPipeline(pipelineId: String)

    @Query("DELETE FROM turn_benchmarks")
    suspend fun clearAllBenchmarks()
}

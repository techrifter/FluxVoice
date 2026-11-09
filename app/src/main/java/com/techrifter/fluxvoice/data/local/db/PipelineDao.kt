package com.techrifter.fluxvoice.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Query("SELECT * FROM voice_pipelines ORDER BY isPreset DESC, createdAt ASC")
    fun getAllPipelines(): Flow<List<PipelineEntity>>

    @Query("SELECT * FROM voice_pipelines WHERE id = :id LIMIT 1")
    suspend fun getPipelineById(id: String): PipelineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(pipeline: PipelineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefaultPresets(presets: List<PipelineEntity>)

    @Update
    suspend fun updatePipeline(pipeline: PipelineEntity)

    @Query("DELETE FROM voice_pipelines WHERE id = :id AND isPreset = 0")
    suspend fun deleteCustomPipeline(id: String)

    @Query("DELETE FROM voice_pipelines WHERE isPreset = 0 AND id != :retainedId")
    suspend fun deleteOtherCustomPipelines(retainedId: String)

    @Query("SELECT COUNT(*) FROM voice_pipelines")
    suspend fun getPipelineCount(): Int

    @Query("SELECT COUNT(*) FROM voice_pipelines WHERE isPreset = 0")
    suspend fun getCustomPipelineCount(): Int
}


package com.techrifter.fluxvoice.domain.repository

import com.techrifter.fluxvoice.domain.model.PipelineConfig
import kotlinx.coroutines.flow.Flow

interface PipelineRepository {
    fun getAllPipelines(): Flow<List<PipelineConfig>>
    suspend fun getPipelineById(id: String): PipelineConfig?
    suspend fun savePipeline(pipeline: PipelineConfig)
    suspend fun updatePipeline(pipeline: PipelineConfig)
    suspend fun deletePipeline(id: String)
    suspend fun ensureDefaultPresets()
    suspend fun getCustomPipelineCount(): Int
    fun getActivePipelineId(): Flow<String>
    suspend fun setActivePipelineId(id: String)
}


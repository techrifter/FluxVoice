package com.techrifter.fluxvoice.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techrifter.fluxvoice.data.local.db.DefaultPipelines
import com.techrifter.fluxvoice.data.local.db.PipelineDao
import com.techrifter.fluxvoice.data.local.db.PipelineEntity
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.domain.repository.PipelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipelineRepositoryImpl @Inject constructor(
    private val pipelineDao: PipelineDao,
    private val dataStore: DataStore<Preferences>
) : PipelineRepository {

    companion object {
        private val KEY_ACTIVE_PIPELINE_ID = stringPreferencesKey("active_pipeline_id")
        private val KEY_CUSTOM_SEEDED = booleanPreferencesKey("custom_pipeline_seeded")
        private const val DEFAULT_PIPELINE_ID = "preset_ultra_fast"
    }

    override fun getAllPipelines(): Flow<List<PipelineConfig>> {
        return pipelineDao.getAllPipelines().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPipelineById(id: String): PipelineConfig? {
        return pipelineDao.getPipelineById(id)?.toDomain()
    }

    override suspend fun savePipeline(pipeline: PipelineConfig) {
        pipelineDao.insertPipeline(PipelineEntity.fromDomain(pipeline))
    }

    override suspend fun updatePipeline(pipeline: PipelineConfig) {
        pipelineDao.updatePipeline(PipelineEntity.fromDomain(pipeline))
    }

    override suspend fun deletePipeline(id: String) {
        pipelineDao.deleteCustomPipeline(id)
        val currentActive = dataStore.data.first()[KEY_ACTIVE_PIPELINE_ID] ?: DEFAULT_PIPELINE_ID
        if (currentActive == id) {
            setActivePipelineId(DEFAULT_PIPELINE_ID)
        }
    }

    override suspend fun ensureDefaultPresets() {
        pipelineDao.insertDefaultPresets(DefaultPipelines.PRESETS)
        val prefs = dataStore.data.first()
        if (prefs[KEY_CUSTOM_SEEDED] != true) {
            if (pipelineDao.getPipelineById(DefaultPipelines.CUSTOM_PIPELINE_ID) == null &&
                pipelineDao.getCustomPipelineCount() == 0
            ) {
                pipelineDao.insertPipeline(DefaultPipelines.DEFAULT_CUSTOM)
            }
            dataStore.edit { it[KEY_CUSTOM_SEEDED] = true }
        }
    }

    override suspend fun getCustomPipelineCount(): Int {
        return pipelineDao.getCustomPipelineCount()
    }

    override fun getActivePipelineId(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[KEY_ACTIVE_PIPELINE_ID] ?: DEFAULT_PIPELINE_ID
        }
    }

    override suspend fun setActivePipelineId(id: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_PIPELINE_ID] = id
        }
    }
}

package com.techrifter.fluxvoice.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.techrifter.fluxvoice.domain.model.PipelineConfig

@Entity(tableName = "voice_pipelines")
data class PipelineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isPreset: Boolean,

    val sttProviderId: String,
    val sttModelId: String,
    val sttEndpointingMs: Int,

    val llmProviderId: String,
    val llmModelId: String,
    val customEndpointUrl: String,
    val temperature: Float,
    val maxOutputTokens: Int,
    val systemPrompt: String,

    val ttsProviderId: String,
    val ttsVoiceId: String,
    val speechRate: Float,
    val pitch: Float,

    val vadEnabled: Boolean,
    val vadSensitivity: Int,
    val backchannelEnabled: Boolean,
    val backchannelDelayMs: Long,

    val createdAt: Long
) {
    fun toDomain(): PipelineConfig = PipelineConfig(
        id = id,
        name = name,
        description = description,
        isPreset = isPreset,
        sttProviderId = sttProviderId,
        sttModelId = sttModelId,
        sttEndpointingMs = sttEndpointingMs,
        llmProviderId = llmProviderId,
        llmModelId = llmModelId,
        customEndpointUrl = customEndpointUrl,
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        systemPrompt = systemPrompt,
        ttsProviderId = ttsProviderId,
        ttsVoiceId = ttsVoiceId,
        speechRate = speechRate,
        pitch = pitch,
        vadEnabled = vadEnabled,
        vadSensitivity = vadSensitivity,
        backchannelEnabled = backchannelEnabled,
        backchannelDelayMs = backchannelDelayMs,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: PipelineConfig): PipelineEntity = PipelineEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            isPreset = domain.isPreset,
            sttProviderId = domain.sttProviderId,
            sttModelId = domain.sttModelId,
            sttEndpointingMs = domain.sttEndpointingMs,
            llmProviderId = domain.llmProviderId,
            llmModelId = domain.llmModelId,
            customEndpointUrl = domain.customEndpointUrl,
            temperature = domain.temperature,
            maxOutputTokens = domain.maxOutputTokens,
            systemPrompt = domain.systemPrompt,
            ttsProviderId = domain.ttsProviderId,
            ttsVoiceId = domain.ttsVoiceId,
            speechRate = domain.speechRate,
            pitch = domain.pitch,
            vadEnabled = domain.vadEnabled,
            vadSensitivity = domain.vadSensitivity,
            backchannelEnabled = domain.backchannelEnabled,
            backchannelDelayMs = domain.backchannelDelayMs,
            createdAt = domain.createdAt
        )
    }
}


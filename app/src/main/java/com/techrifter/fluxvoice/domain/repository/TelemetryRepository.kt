package com.techrifter.fluxvoice.domain.repository

interface TelemetryRepository {
    suspend fun recordTurnTelemetry(
        sttProvider: String,
        llmModel: String,
        ttsProvider: String,
        ttftMs: Long,
        ttfaMs: Long,
        totalTurnMs: Long,
        tokens: Int,
        bargeInOccurred: Boolean,
        pipelineName: String,
        languageCode: String
    )
    
    fun recordPipelineError(
        stage: String,
        provider: String,
        errorCode: String,
        errorMessage: String = "",
        model: String = ""
    )
}

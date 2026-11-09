package com.techrifter.fluxvoice.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PipelineEntity::class,
        TurnBenchmarkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FluxVoiceDatabase : RoomDatabase() {
    abstract fun pipelineDao(): PipelineDao
    abstract fun turnBenchmarkDao(): TurnBenchmarkDao

    companion object {
        const val DATABASE_NAME = "fluxvoice.db"
    }
}


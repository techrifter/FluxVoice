package com.techrifter.fluxvoice.core.di

import android.content.Context
import androidx.room.Room
import com.techrifter.fluxvoice.data.local.db.FluxVoiceDatabase
import com.techrifter.fluxvoice.data.local.db.PipelineDao
import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FluxVoiceDatabase {
        return Room.databaseBuilder(
            context,
            FluxVoiceDatabase::class.java,
            FluxVoiceDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun providePipelineDao(database: FluxVoiceDatabase): PipelineDao {
        return database.pipelineDao()
    }

    @Provides
    @Singleton
    fun provideTurnBenchmarkDao(database: FluxVoiceDatabase): TurnBenchmarkDao {
        return database.turnBenchmarkDao()
    }
}


package com.techrifter.fluxvoice.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.techrifter.fluxvoice.core.util.DefaultDispatcherProvider
import com.techrifter.fluxvoice.core.util.DispatcherProvider
import com.techrifter.fluxvoice.data.conversation.ConversationRepositoryImpl
import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
import com.techrifter.fluxvoice.data.repository.PipelineRepositoryImpl
import com.techrifter.fluxvoice.data.repository.SettingsRepositoryImpl
import com.techrifter.fluxvoice.data.repository.TurnBenchmarkRepositoryImpl
import com.techrifter.fluxvoice.data.security.EncryptedKeyVault
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.repository.ConversationRepository
import com.techrifter.fluxvoice.domain.repository.PipelineRepository
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import com.techrifter.fluxvoice.domain.repository.TelemetryRepository
import com.techrifter.fluxvoice.domain.repository.TurnBenchmarkRepository
import com.techrifter.fluxvoice.data.telemetry.TelemetryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
            keyVault: KeyVault
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(LegacyApiKeyMigration(keyVault)),
                produceFile = { context.preferencesDataStoreFile("settings") }
            )

        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    }

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindKeyVault(impl: EncryptedKeyVault): KeyVault

    @Binds
    @Singleton
    abstract fun bindPipelineRepository(impl: PipelineRepositoryImpl): PipelineRepository

    @Binds
    @Singleton
    abstract fun bindTurnBenchmarkRepository(impl: TurnBenchmarkRepositoryImpl): TurnBenchmarkRepository

    @Binds
    @Singleton
    abstract fun bindTelemetryRepository(impl: TelemetryRepositoryImpl): TelemetryRepository
}

private class LegacyApiKeyMigration(
    private val keyVault: KeyVault
) : DataMigration<Preferences> {

    private data class LegacyApiKey(
        val preferenceKey: Preferences.Key<String>,
        val providerId: String
    )

    private val legacyKeys = listOf(
        LegacyApiKey(SettingsDataStore.GEMINI_API_KEY, "gemini"),
        LegacyApiKey(SettingsDataStore.OPENAI_API_KEY, "openai"),
        LegacyApiKey(SettingsDataStore.GROQ_API_KEY, "groq"),
        LegacyApiKey(SettingsDataStore.OPENROUTER_API_KEY, "openrouter"),
        LegacyApiKey(SettingsDataStore.ELEVENLABS_API_KEY, "elevenlabs"),
        LegacyApiKey(SettingsDataStore.DEEPGRAM_API_KEY, "deepgram"),
        LegacyApiKey(SettingsDataStore.CARTESIA_API_KEY, "cartesia")
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        legacyKeys.any { it.preferenceKey in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migratedData = currentData.toMutablePreferences()
        legacyKeys.forEach { legacyApiKey ->
            currentData[legacyApiKey.preferenceKey]?.trim()?.takeIf(String::isNotEmpty)?.let { legacyKey ->
                if (keyVault.getKey(legacyApiKey.providerId).isBlank()) {
                    keyVault.setKey(legacyApiKey.providerId, legacyKey)
                }
            }
            migratedData.remove(legacyApiKey.preferenceKey)
        }
        return migratedData
    }

    override suspend fun cleanUp() = Unit
}

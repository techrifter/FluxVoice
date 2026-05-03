package com.techrifter.fluxvoice.core.di

import com.techrifter.fluxvoice.data.remote.provider.gemini.GeminiProvider
import com.techrifter.fluxvoice.data.remote.provider.groq.GroqProvider
import com.techrifter.fluxvoice.data.remote.provider.local.LocalLlmProvider
import com.techrifter.fluxvoice.data.remote.provider.openai.OpenAiProvider
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.streaming.AiStreamingProvider
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Custom MapKey for the AI provider multi-binding.
 * To add a new provider:
 *  1. Add an entry to [ProviderType]
 *  2. Implement [AiStreamingProvider] in data/remote/provider/
 *  3. Add a @Provides binding below with the new @ProviderTypeKey
 */
@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderTypeKey(val value: ProviderType)

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    companion object {
        @Provides
        @Singleton
        @IntoMap
        @ProviderTypeKey(ProviderType.GEMINI)
        fun provideGeminiProvider(provider: GeminiProvider): AiStreamingProvider = provider

        @Provides
        @Singleton
        @IntoMap
        @ProviderTypeKey(ProviderType.GROQ)
        fun provideGroqProvider(provider: GroqProvider): AiStreamingProvider = provider

        @Provides
        @Singleton
        @IntoMap
        @ProviderTypeKey(ProviderType.OPENAI_COMPAT)
        fun provideOpenAiProvider(provider: OpenAiProvider): AiStreamingProvider = provider

        @Provides
        @Singleton
        @IntoMap
        @ProviderTypeKey(ProviderType.LOCAL)
        fun provideLocalLlmProvider(provider: LocalLlmProvider): AiStreamingProvider = provider
    }
}

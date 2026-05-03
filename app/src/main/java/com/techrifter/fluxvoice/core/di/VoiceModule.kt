package com.techrifter.fluxvoice.core.di

import com.techrifter.fluxvoice.data.stt.SttEngineMux
import com.techrifter.fluxvoice.data.tts.CartesiaTtsEngine
import com.techrifter.fluxvoice.domain.stt.SttEngine
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI bindings for the voice pipeline engines.
 *
 * To swap STT or TTS:
 *  - Implement [SttEngine] / [TtsEngine]
 *  - Replace the @Binds binding below with your implementation
 *  - No other code changes required
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    /**
     * Bind [SttEngineMux] as the active [SttEngine].
     * The mux delegates to [WhisperSttEngine] or [DeepgramSttEngine]
     * based on the user's STT provider setting.
     */
    @Binds
    @Singleton
    abstract fun bindSttEngine(impl: SttEngineMux): SttEngine

    /**
     * Bind [CartesiaTtsEngine] as the active [TtsEngine].
     * CartesiaTtsEngine delegates to [AndroidTtsEngine] automatically when
     * no Cartesia API key is configured.
     */
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: CartesiaTtsEngine): TtsEngine
}

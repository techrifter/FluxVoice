package com.techrifter.fluxvoice.data.tts

import android.util.Log
import com.techrifter.fluxvoice.domain.model.TtsProvider
import com.techrifter.fluxvoice.domain.pipeline.PipelineSessionManager
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import com.techrifter.fluxvoice.domain.tts.TtsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TtsEngineMux"

/**
 * Universal multiplexing [TtsEngine] for the FluxVoice Voice AI Playground.
 * Automatically and dynamically routes voice synthesis to the active pipeline's configured provider:
 *
 * 1. [CartesiaTtsEngine]     — Ultra-low latency (~90ms) neural streaming voice
 * 2. [ElevenLabsTtsEngine]   — Hyper-realistic conversational voice (Flash / Turbo)
 * 3. [OpenAiTtsEngine]       — Standard OpenAI Speech (tts-1 / tts-1-hd)
 * 4. [DeepgramAuraTtsEngine] — Low-latency Deepgram Aura streaming voice
 * 5. [AndroidTtsEngine]      — 100% on-device offline Android TextToSpeech
 * 6. [CustomTtsEngine]       — Custom BYOK OpenAI-compatible speech endpoint
 */
@Singleton
class TtsEngineMux @Inject constructor(
    private val cartesia: CartesiaTtsEngine,
    private val elevenLabs: ElevenLabsTtsEngine,
    private val openAi: OpenAiTtsEngine,
    private val deepgramAura: DeepgramAuraTtsEngine,
    private val androidTts: AndroidTtsEngine,
    private val customTts: CustomTtsEngine,
    pipelineSessionManager: PipelineSessionManager
) : TtsEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeProvider = MutableStateFlow(TtsProvider.CARTESIA)
    val activeProvider: StateFlow<TtsProvider> = _activeProvider.asStateFlow()

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    init {
        // Observe pipeline changes and dynamically update active provider and voice IDs
        scope.launch {
            pipelineSessionManager.observeActivePipeline().collect { pipeline ->
                if (pipeline != null) {
                    val provider = TtsProvider.fromId(pipeline.ttsProviderId)
                    _activeProvider.value = provider
                    Log.d(TAG, "Active TTS provider switched to: ${provider.displayName}")

                    // Configure provider-specific voice IDs
                    when (provider) {
                        TtsProvider.ELEVENLABS -> {
                            if (pipeline.ttsVoiceId.isNotBlank()) {
                                elevenLabs.activeVoiceId = pipeline.ttsVoiceId
                            }
                        }
                        TtsProvider.OPENAI -> {
                            if (pipeline.ttsVoiceId.isNotBlank()) {
                                openAi.activeVoice = pipeline.ttsVoiceId
                            }
                        }
                        TtsProvider.DEEPGRAM_AURA -> {
                            if (pipeline.ttsVoiceId.isNotBlank()) {
                                deepgramAura.activeModel = pipeline.ttsVoiceId
                            }
                        }
                        TtsProvider.CUSTOM -> {
                            customTts.activeEndpointUrl = pipeline.customEndpointUrl
                            customTts.activeVoiceId = pipeline.ttsVoiceId
                        }
                        else -> { /* handled by engine */ }
                    }
                }
            }
        }

        // Bridge active engine state
        scope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _activeProvider.flatMapLatest { p ->
                activeEngine(p).state
            }.collect { s ->
                _state.value = s
            }
        }
    }

    override fun speak(text: String, utteranceId: String) {
        val engine = activeEngine(_activeProvider.value)
        Log.d(TAG, "Routing speak() to ${_activeProvider.value.name}")
        engine.speak(text, utteranceId)
    }

    override fun stop() {
        // Stop ALL engines immediately to guarantee instant silence on barge-in
        cartesia.stop()
        elevenLabs.stop()
        openAi.stop()
        deepgramAura.stop()
        androidTts.stop()
        customTts.stop()
        _state.value = TtsState.Idle
    }

    override fun setSpeechRate(rate: Float) {
        cartesia.setSpeechRate(rate)
        elevenLabs.setSpeechRate(rate)
        openAi.setSpeechRate(rate)
        deepgramAura.setSpeechRate(rate)
        androidTts.setSpeechRate(rate)
        customTts.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        cartesia.setPitch(pitch)
        elevenLabs.setPitch(pitch)
        openAi.setPitch(pitch)
        deepgramAura.setPitch(pitch)
        androidTts.setPitch(pitch)
        customTts.setPitch(pitch)
    }

    override fun shutdown() {
        cartesia.shutdown()
        elevenLabs.shutdown()
        openAi.shutdown()
        deepgramAura.shutdown()
        androidTts.shutdown()
        customTts.shutdown()
    }

    private fun activeEngine(provider: TtsProvider): TtsEngine = when (provider) {
        TtsProvider.CARTESIA      -> cartesia
        TtsProvider.ELEVENLABS    -> elevenLabs
        TtsProvider.OPENAI        -> openAi
        TtsProvider.DEEPGRAM_AURA -> deepgramAura
        TtsProvider.ANDROID       -> androidTts
        TtsProvider.CUSTOM        -> customTts
    }
}


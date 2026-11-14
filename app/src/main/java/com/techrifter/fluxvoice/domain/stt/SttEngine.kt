package com.techrifter.fluxvoice.domain.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a speech-to-text engine.
 *
 * The default implementation uses Android's built-in [SpeechRecognizer].
 * To swap the engine (e.g., Whisper, Deepgram) provide an alternative @Binds
 * binding in the Hilt DI module — no other code changes required.
 *
 * **Thread safety**: [startListening] and [stopListening] must be called from
 * the **main thread**. [VoiceViewModel] launches them in a Main-scoped coroutine.
 */
interface SttEngine {

    /** Live recognition state — collect from any coroutine. */
    val state: StateFlow<SttState>

    /**
     * Start listening for user speech.
     * Transitions [state] to [SttState.Listening] immediately, then emits
     * [SttState.PartialResult] updates until silence is detected, at which point
     * [SttState.FinalResult] is emitted followed by [SttState.Idle].
     */
    fun startListening()

    /**
     * Manually stop listening before silence detection triggers.
     * Useful for implementing a push-to-talk button.
     */
    fun stopListening()

    /**
     * Pre-warm the engine's network connection in the background so that the
     * next [startListening] call incurs zero connection-setup latency.
     * Safe to call multiple times — subsequent calls are no-ops while a
     * pre-warmed connection is already waiting.
     * Default implementation is a no-op for engines that don't need it.
     */
    fun preConnect() {}

    /** Optional config update for engines that support dynamic models and endpointing. */
    fun updateConfig(modelId: String? = null, endpointingMs: Int? = null) {}

    /** Release native resources. Call from [ViewModel.onCleared]. */
    fun destroy()
}


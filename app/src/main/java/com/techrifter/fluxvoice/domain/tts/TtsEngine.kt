package com.techrifter.fluxvoice.domain.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a text-to-speech engine.
 *
 * Designed for **sentence-level streaming**: call [speak] for each sentence as
 * it arrives from the LLM stream. Sentences are queued and played back-to-back.
 * This pipeline parallelism (LLM generating + TTS playing simultaneously) is the
 * primary mechanism for achieving <2s end-to-end voice response latency.
 *
 * The default implementation uses Android's built-in [TextToSpeech].
 * To swap in a higher-quality engine (e.g., ElevenLabs, Azure TTS), provide an
 * alternative @Binds binding in the Hilt DI module — no other code changes required.
 */
interface TtsEngine {

    /** Live synthesis state — [TtsState.Idle] only when ALL queued utterances finish. */
    val state: StateFlow<TtsState>

    /**
     * Queue [text] for playback.
     *
     * Call this for each extracted sentence from the LLM token stream for minimal latency.
     * [state] stays [TtsState.Speaking] until the last queued sentence finishes.
     *
     * @param text        The sentence/fragment to synthesise.
     * @param utteranceId Unique string used to track this utterance (for logging).
     */
    fun speak(text: String, utteranceId: String)

    /** Immediately halt playback and flush the queue. Transitions [state] to [TtsState.Idle]. */
    fun stop()

    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)

    /** Release TTS resources. Call from [ViewModel.onCleared]. */
    fun shutdown()
}


package com.techrifter.fluxvoice.core.tts

import com.techrifter.fluxvoice.core.state.TtsState
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a text-to-speech provider.
 *
 * Designed for **sentence-level streaming**: call [speak] for each sentence as
 * it arrives from the LLM token stream. Sentences are queued and played back-to-back,
 * enabling LLM generation and TTS playback to run in parallel for sub-2s latency.
 *
 * Implement this to plug in Cartesia, ElevenLabs, Azure TTS, Play.ht, on-device TTS,
 * or any other synthesis engine.
 *
 * Example:
 * ```kotlin
 * class MyTts : TtsProvider {
 *     override val state = MutableStateFlow<TtsState>(TtsState.Idle)
 *     override fun speak(text: String, utteranceId: String) { /* synthesize */ }
 *     override fun stop() { /* cancel all queued audio */ }
 *     override fun shutdown() { /* cleanup */ }
 * }
 * ```
 */
public interface TtsProvider {

    /** Live synthesis state — [TtsState.Idle] only when ALL queued utterances finish. */
    public val state: StateFlow<TtsState>

    /**
     * Queue [text] for synthesis and playback.
     * [state] stays [TtsState.Speaking] until the last queued sentence completes.
     *
     * @param text        The sentence or fragment to synthesize.
     * @param utteranceId Unique ID for tracking/logging this utterance.
     */
    public fun speak(text: String, utteranceId: String)

    /** Halt playback immediately and flush the queue → [TtsState.Idle]. */
    public fun stop()

    /** Optional: adjust speech rate (1.0 = normal). No-op by default. */
    public fun setSpeechRate(rate: Float) {}

    /** Optional: adjust pitch (1.0 = normal). No-op by default. */
    public fun setPitch(pitch: Float) {}

    /** Release TTS resources. Call when the host ViewModel is cleared. */
    public fun shutdown()
}

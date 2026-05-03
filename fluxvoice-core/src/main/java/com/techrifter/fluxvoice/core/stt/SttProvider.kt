package com.techrifter.fluxvoice.core.stt

import com.techrifter.fluxvoice.core.state.SttState
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a speech-to-text provider.
 *
 * Implement this interface to plug any STT engine into the FluxVoice pipeline —
 * Deepgram, Whisper, Azure Speech, on-device ML Kit, or your own proprietary service.
 *
 * **Thread safety**: [startListening] and [stopListening] must be called from the main thread.
 *
 * **Lifecycle**: [state] emits IDLE → LISTENING → PartialResult* → FinalResult → IDLE on success.
 *
 * Example:
 * ```kotlin
 * class MyCustomStt : SttProvider {
 *     override val state = MutableStateFlow<SttState>(SttState.Idle)
 *     override fun startListening() { /* open mic */ }
 *     override fun stopListening()  { /* close mic */ }
 *     override fun destroy()        { /* cleanup */ }
 * }
 * ```
 */
public interface SttProvider {

    /** Live recognition state — collect from any coroutine. */
    public val state: StateFlow<SttState>

    /**
     * Open the microphone and begin recognizing speech.
     * Transitions [state] → [SttState.Listening] immediately.
     */
    public fun startListening()

    /**
     * Stop listening before silence detection fires naturally.
     * Useful for push-to-talk or programmatic interruption.
     */
    public fun stopListening()

    /**
     * Optionally pre-warm any network connection so [startListening] has
     * zero connection-setup latency. Safe to call repeatedly; no-op by default.
     */
    public fun preConnect() {}

    /** Release native resources. Call when the host ViewModel is cleared. */
    public fun destroy()
}

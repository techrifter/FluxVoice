package com.techrifter.fluxvoice.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Runtime controller returned by the FluxVoice engine.
 *
 * Use [state] to drive your own UI, or pass this controller to `FluxVoiceView`
 * (from `fluxvoice-compose`) to get the built-in animated FluxVoice UI for free.
 *
 * ```kotlin
 * // Headless — build your own UI
 * val state by controller.state.collectAsStateWithLifecycle()
 * Text(state.aiResponse)
 * Button(onClick = controller::tap) { Text("Speak") }
 *
 * // Built-in UI
 * FluxVoiceView(controller = controller)
 * ```
 */
public interface FluxVoiceController {

    /** Reactive state — collect this to drive any UI. */
    public val state: StateFlow<FluxVoiceState>

    /**
     * Primary action — context-sensitive:
     * - IDLE        → start listening (requests mic permission if needed)
     * - LISTENING   → stop listening early
     * - THINKING    → cancel the in-progress AI request
     * - SPEAKING    → barge in (interrupt the AI and start listening)
     * - INTERRUPTING → no-op (already transitioning)
     */
    public fun tap()

    /** Cancel any in-flight request and clear conversation history. */
    public fun clear()

    /** Dismiss the current error banner. */
    public fun dismissError()

    /** Optional: dynamically adjust TTS speech rate (0.5–2.0). */
    public fun setSpeechRate(rate: Float) {}

    /** Optional: dynamically adjust TTS pitch (0.5–2.0). */
    public fun setPitch(pitch: Float) {}

    /** Release all resources. Call from your ViewModel's `onCleared`. */
    public fun destroy()
}

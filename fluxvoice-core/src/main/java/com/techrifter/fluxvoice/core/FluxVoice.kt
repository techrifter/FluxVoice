package com.techrifter.fluxvoice.core

/**
 * # FluxVoice SDK
 *
 * Real-time voice AI pipeline for Android. Drop-in STT → LLM → TTS with a
 * single composable, or go fully headless and build any UI you want.
 *
 * ---
 *
 * ## Quickstart (built-in UI, 3 lines)
 *
 * ```kotlin
 * // In your Composable
 * val controller = rememberFluxVoice {           // from fluxvoice-compose
 *     llmProvider = GroqLlmProvider("gsk_...")   // from fluxvoice-provider-groq
 *     sttProvider = DeepgramSttProvider("...")   // from fluxvoice-stt-deepgram
 *     ttsProvider = CartesiaTtsProvider("...")   // from fluxvoice-tts-cartesia
 * }
 * FluxVoiceView(controller)                       // animated FluxVoice UI + transcript
 * ```
 *
 * ---
 *
 * ## Headless (custom UI)
 *
 * ```kotlin
 * val controller = rememberFluxVoice { ... }
 * val state by controller.state.collectAsStateWithLifecycle()
 *
 * when (state.voiceState) {
 *     VoiceState.IDLE      -> MyIdleUI(onTap = controller::tap)
 *     VoiceState.LISTENING -> MyListeningUI(transcript = state.partialTranscript)
 *     VoiceState.THINKING  -> MyThinkingUI()
 *     VoiceState.SPEAKING  -> MySpeakingUI(response = state.aiResponse)
 *     else                 -> Unit
 * }
 * ```
 *
 * ---
 *
 * ## Bring Your Own Provider
 *
 * ```kotlin
 * // Any LLM backend in ~30 lines:
 * class MyCompanyLlm : LlmProvider {
 *     override fun streamChat(messages: List<Message>, config: GenerationConfig) = flow {
 *         emit(StreamEvent.Start)
 *         // call your API, emit tokens...
 *         emit(StreamEvent.Done)
 *     }
 * }
 *
 * val controller = rememberFluxVoice {
 *     llmProvider = MyCompanyLlm()
 *     sttProvider = DeepgramSttProvider("...")
 * }
 * ```
 *
 * ---
 *
 * ## Hooks
 *
 * ```kotlin
 * rememberFluxVoice {
 *     onTranscript  { text     -> analytics.track("spoke", text) }
 *     onResponse    { response -> db.save(sessionId, response) }
 *     onError       { error    -> crashlytics.record(error) }
 *     onStateChange { from, to -> Timber.d("$from → $to") }
 * }
 * ```
 *
 * ---
 *
 * @see FluxVoiceConfig  — full configuration reference
 * @see FluxVoiceController — runtime control + state
 * @see com.techrifter.fluxvoice.core.llm.LlmProvider — implement to add any LLM backend
 * @see com.techrifter.fluxvoice.core.stt.SttProvider — implement to add any STT engine
 * @see com.techrifter.fluxvoice.core.tts.TtsProvider — implement to add any TTS engine
 */
public object FluxVoice {

    /** Current SDK version. */
    public const val VERSION: String = "1.0.0"

    /** DSL shorthand for [FluxVoiceConfig]. */
    public fun config(block: FluxVoiceConfig.Builder.() -> Unit): FluxVoiceConfig =
        FluxVoiceConfig(block)
}

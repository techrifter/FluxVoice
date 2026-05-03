package com.techrifter.fluxvoice.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.techrifter.fluxvoice.android.FluxVoiceEngine
import com.techrifter.fluxvoice.core.FluxVoiceConfig
import com.techrifter.fluxvoice.core.FluxVoiceController

/**
 * Create and remember a [FluxVoiceController] tied to the composition lifecycle.
 *
 * The engine starts immediately and is destroyed automatically when this composable
 * leaves the composition. Typically called once at the top of your screen composable.
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val controller = rememberFluxVoice(mode) {
 *         sttProvider = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_KEY)
 *         llmProvider = GroqLlmProvider(apiKey = BuildConfig.GROQ_KEY)
 *         ttsProvider = CartesiaTtsProvider(apiKey = BuildConfig.CARTESIA_KEY, voiceId = "...")
 *         systemPrompt = "You are a friendly barista assistant."
 *         vadEnabled   = true
 *
 *         onTranscript  { text     -> analytics.track(text) }
 *         onResponse    { response -> db.save(response) }
 *         onError       { error    -> crashlytics.record(error) }
 *     }
 *
 *     FluxVoiceView(controller = controller)
 * }
 * ```
 *
 * **Permissions**: `RECORD_AUDIO` must be granted before [FluxVoiceController.tap] is
 * called for the first time. [FluxVoiceView] handles the runtime permission request automatically.
 */
@Composable
public fun rememberFluxVoice(
    vararg keys: Any?,
    block: FluxVoiceConfig.Builder.() -> Unit
): FluxVoiceController {
    val scope = rememberCoroutineScope()
    val engine = remember(*keys) {
        FluxVoiceEngine(
            config = FluxVoiceConfig(block),
            scope  = scope
        )
    }
    DisposableEffect(engine) {
        onDispose { engine.destroy() }
    }
    return engine
}

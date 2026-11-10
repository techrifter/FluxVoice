/**
 * EXAMPLE 05 — Custom providers via fluxvoice-core interfaces
 *
 * Demonstrates:
 *  - Implementing SttProvider with all SttState variants
 *  - Implementing LlmProvider returning Flow<StreamEvent>
 *  - Implementing TtsProvider with TtsState management
 *  - Plugging all three into FluxVoiceConfig — identical to built-in providers
 *
 * Use this to connect any STT/LLM/TTS service that FluxVoice doesn't ship a
 * provider for (e.g. OpenAI Whisper, Google Gemini, ElevenLabs, Azure TTS).
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.1.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-core:1.1.0")
 *   // No provider modules needed — you're implementing them yourself
 */

import android.util.Log
import androidx.compose.runtime.Composable
import com.techrifter.fluxvoice.compose.FluxVoiceScreen
import com.techrifter.fluxvoice.compose.FluxVoiceTheme
import com.techrifter.fluxvoice.compose.rememberFluxVoice
import com.techrifter.fluxvoice.core.llm.LlmProvider
import com.techrifter.fluxvoice.core.model.GenerationConfig
import com.techrifter.fluxvoice.core.model.Message
import com.techrifter.fluxvoice.core.state.SttState
import com.techrifter.fluxvoice.core.state.TtsState
import com.techrifter.fluxvoice.core.streaming.StreamEvent
import com.techrifter.fluxvoice.core.stt.SttProvider
import com.techrifter.fluxvoice.core.tts.TtsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Custom STT — replace with your real STT service WebSocket / SDK
// ─────────────────────────────────────────────────────────────────────────────

class MyCustomSttProvider : SttProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    override fun startListening() {
        Log.d("MY_STT", "startListening")
        _state.value = SttState.Listening

        // Replace with your real audio streaming logic.
        // Emit PartialResult continuously as the user speaks,
        // then FinalResult when the utterance is complete.
        scope.launch {
            delay(800)
            _state.value = SttState.PartialResult("what time")
            delay(600)
            _state.value = SttState.FinalResult("What time is it in Tokyo?")
        }
    }

    override fun stopListening() {
        Log.d("MY_STT", "stopListening")
        if (_state.value == SttState.Listening) {
            _state.value = SttState.Idle
        }
    }

    // Optional — pre-warm your connection between turns for lower latency
    override fun preConnect() {
        Log.d("MY_STT", "preConnect")
    }

    override fun destroy() {
        Log.d("MY_STT", "destroy")
        _state.value = SttState.Idle
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom LLM — replace with your real LLM API (OpenAI, Gemini, Ollama, etc.)
// ─────────────────────────────────────────────────────────────────────────────

class MyCustomLlmProvider : LlmProvider {

    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig
    ): Flow<StreamEvent> = flow {
        Log.d("MY_LLM", "streamChat: ${messages.size} messages, temp=${config.temperature}")
        // config.systemPrompt, config.temperature, config.maxOutputTokens all available

        emit(StreamEvent.Start)

        // Replace with your real streaming API call.
        // Emit Token for each text chunk as it arrives.
        val reply = "It is currently 3:47 PM in Tokyo."
        reply.split(" ").forEach { word ->
            delay(60)
            emit(StreamEvent.Token("$word "))
        }

        // Emitting token usage informs FluxVoice telemetry & benchmark records
        emit(StreamEvent.Usage(promptTokens = 24, completionTokens = 9))

        emit(StreamEvent.Done)

        // On error: emit(StreamEvent.Error(exception))
        // The engine will retry up to 2 times automatically.
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom TTS — replace with your real TTS service (ElevenLabs, Azure, etc.)
// ─────────────────────────────────────────────────────────────────────────────

class MyCustomTtsProvider : TtsProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    override fun speak(text: String, utteranceId: String) {
        Log.d("MY_TTS", "speak: \"$text\" (id=$utteranceId)")
        _state.value = TtsState.Speaking

        // Replace with your real synthesis + playback.
        // Must set _state.value = TtsState.Idle when playback finishes —
        // the engine watches this to know when to reopen the mic.
        scope.launch {
            delay((text.length * 60).toLong()) // simulate playback duration
            Log.d("MY_TTS", "playback finished")
            _state.value = TtsState.Idle
        }
    }

    override fun stop() {
        Log.d("MY_TTS", "stop")
        // Cancel ongoing playback immediately
        _state.value = TtsState.Idle
    }

    override fun setSpeechRate(rate: Float) {
        Log.d("MY_TTS", "setSpeechRate: $rate")
    }

    override fun setPitch(pitch: Float) {
        Log.d("MY_TTS", "setPitch: $pitch")
    }

    override fun shutdown() {
        Log.d("MY_TTS", "shutdown")
        // Release all resources (audio tracks, HTTP clients, etc.)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Usage — identical to built-in providers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceScreenWithCustomProviders() {
    FluxVoiceTheme {
        val controller = rememberFluxVoice {
            sttProvider = MyCustomSttProvider()
            llmProvider = MyCustomLlmProvider()
            ttsProvider = MyCustomTtsProvider()
            systemPrompt    = "You are Flux, an intelligent voice AI assistant. Deliver high-signal, direct answers tailored for spoken audio without preamble, markdown, or lists."
            temperature     = 0.7f
            maxOutputTokens = 1024
            vadEnabled      = true
            backchannelEnabled = false
            onTurnMetrics { metrics ->
                Log.d("MY_APP", "Turn TTFA=${metrics.timeToFirstAudioMs}ms, TTFT=${metrics.timeToFirstTokenMs}ms")
            }
        }

        FluxVoiceScreen(controller = controller)
    }
}

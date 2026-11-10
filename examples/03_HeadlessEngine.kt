/**
 * EXAMPLE 03 — Headless Engine (fluxvoice-android, no Compose UI dependency)
 *
 * Demonstrates:
 *  - FluxVoiceEngine with FluxVoiceConfig DSL
 *  - All configuration options: systemPrompt, maxContextTurns, temperature,
 *    maxOutputTokens, vadEnabled, vadSensitivity, backchannelEnabled, backchannelDelayMs
 *  - All callbacks: onTranscript, onResponse, onError, onStateChange
 *  - FluxVoiceState fields: voiceState, partialTranscript, transcript, aiResponse, errorMessage
 *  - Controller methods: tap(), clear(), dismissError(), destroy()
 *  - DeepgramSttProvider model param, OpenAiCompatibleLlmProvider modelId param,
 *    CartesiaTtsProvider voiceId + modelId params
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-android:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
 *   implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 */

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.android.FluxVoiceEngine
import com.techrifter.fluxvoice.core.FluxVoiceConfig
import com.techrifter.fluxvoice.provider.llm.OpenAiCompatibleLlmProvider
import com.techrifter.fluxvoice.stt.deepgram.DeepgramSttProvider
import com.techrifter.fluxvoice.tts.cartesia.CARTESIA_VOICE
import com.techrifter.fluxvoice.tts.cartesia.CartesiaTtsProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()

                val engine = remember {
                    FluxVoiceEngine(
                        config = FluxVoiceConfig {
                            // ── Providers ─────────────────────────────────────
                            sttProvider = DeepgramSttProvider(
                                apiKey = "YOUR_DEEPGRAM_KEY",
                                model  = "nova-3"             // default
                            )
                            llmProvider = OpenAiCompatibleLlmProvider(
                                apiKey  = "YOUR_GROQ_KEY",
                                modelId = "openai/gpt-oss-120b"  // default
                            )
                            ttsProvider = CartesiaTtsProvider(
                                apiKey  = "YOUR_CARTESIA_KEY",
                                voiceId = CARTESIA_VOICE,     // named constant
                                modelId = "sonic-3.6"         // default
                            )

                            // ── Conversation ──────────────────────────────────
                            systemPrompt    = "You are Flux, an ultra-fast voice AI assistant. Speak with clarity and natural rhythm, delivering direct, high-signal spoken answers."
                            maxContextTurns = 6      // default 10

                            // ── LLM generation ───────────────────────────────
                            temperature     = 0.5f   // 0.0–2.0, default 0.7
                            maxOutputTokens = 512    // default 2048

                            // ── Barge-in (VAD) ───────────────────────────────
                            vadEnabled      = true   // default true
                            vadSensitivity  = 800    // 200–3000, default 1000

                            // ── Backchannels ─────────────────────────────────
                            backchannelEnabled  = true    // default false
                            backchannelDelayMs  = 1500L   // default 1500

                            // ── Callbacks ────────────────────────────────────
                            onTranscript  { text     -> Log.d("FV", "User said: $text") }
                            onResponse    { response -> Log.d("FV", "AI said:   $response") }
                            onError       { error    -> Log.e("FV", "Error: ${error.message}") }
                            onStateChange { from, to -> Log.d("FV", "State: $from → $to") }
                            onTurnMetrics { metrics  ->
                                Log.d("FV", "Turn metrics: TTFA=${metrics.timeToFirstAudioMs}ms, TTFT=${metrics.timeToFirstTokenMs}ms, tokens=${metrics.totalTokens}")
                            }
                        },
                        scope = scope
                    )
                }

                DisposableEffect(engine) { onDispose { engine.destroy() } }

                val state by engine.state.collectAsStateWithLifecycle()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF080C12))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Headless Engine", color = Color.White,
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))

                    // ── State display ─────────────────────────────────────────
                    Text("voiceState:        ${state.voiceState}",        color = Color(0xFF94A3B8))
                    Text("partialTranscript: ${state.partialTranscript}", color = Color(0xFF94A3B8))
                    Text("transcript:        ${state.transcript}",        color = Color(0xFF94A3B8))
                    Text("aiResponse:        ${state.aiResponse}",        color = Color(0xFF94A3B8))
                    Text("errorMessage:      ${state.errorMessage ?: "—"}", color = Color(0xFFEF4444))
                    state.metrics?.let { m ->
                        Text("metrics:           TTFT=${m.timeToFirstTokenMs}ms | TTFA=${m.timeToFirstAudioMs}ms | STT=${m.sttLatencyMs}ms", color = Color(0xFF10B981))
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Controller methods ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // tap() is context-sensitive:
                        //   IDLE        → start listening
                        //   LISTENING   → stop and send to LLM
                        //   THINKING    → cancel LLM request
                        //   SPEAKING    → barge-in (stop TTS, reopen mic)
                        //   INTERRUPTING → no-op
                        Button(onClick = { engine.tap() }) { Text("tap()") }

                        Button(
                            onClick = { engine.clear() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                        ) { Text("clear()") }

                        Button(
                            onClick = { engine.dismissError() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) { Text("dismissError()") }
                    }
                }
            }
        }
    }
}

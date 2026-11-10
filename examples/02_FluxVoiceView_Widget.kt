/**
 * EXAMPLE 02 — Embedded Widget with FluxVoiceView
 *
 * Demonstrates:
 *  - FluxVoiceView embedded inside your own screen layout
 *  - FluxVoiceViewConfig: size, showBrandName, showTranscript, showStateLabel, showHintLabel
 *  - FluxVoiceColors: custom colours for all 5 voice states
 *  - No background — widget inherits parent colour
 *
 * Use this when you already have a screen and want to add voice into it,
 * rather than replacing the whole screen with FluxVoiceScreen.
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
 */

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techrifter.fluxvoice.compose.FluxVoiceColors
import com.techrifter.fluxvoice.compose.FluxVoiceTheme
import com.techrifter.fluxvoice.compose.FluxVoiceView
import com.techrifter.fluxvoice.compose.FluxVoiceViewConfig
import com.techrifter.fluxvoice.compose.rememberFluxVoice
import com.techrifter.fluxvoice.provider.llm.OpenAiCompatibleLlmProvider
import com.techrifter.fluxvoice.stt.deepgram.DeepgramSttProvider
import com.techrifter.fluxvoice.tts.cartesia.CartesiaTtsProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxVoiceTheme {
                val controller = rememberFluxVoice {
                    systemPrompt = "You are Flux, an intelligent voice AI assistant. Deliver high-signal, direct answers tailored for spoken audio without preamble, markdown, or lists."
                    sttProvider  = DeepgramSttProvider(apiKey = "YOUR_DEEPGRAM_KEY")
                    llmProvider  = OpenAiCompatibleLlmProvider(apiKey = "YOUR_GROQ_KEY")
                    ttsProvider  = CartesiaTtsProvider(apiKey = "YOUR_CARTESIA_KEY")
                }

                // Your own screen: FluxVoiceView sits inside it
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        "My App",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )

                    FluxVoiceView(
                        controller = controller,
                        config = FluxVoiceViewConfig(
                            size           = 200.dp,   // default 240.dp
                            showBrandName  = false,    // hide "FluxVoice" label
                            showTranscript = true,
                            showStateLabel = true,
                            showHintLabel  = true,
                            colors = FluxVoiceColors(
                                idle         = Color(0xFF64748B),
                                listening    = Color(0xFF3B82F6),
                                thinking     = Color(0xFF8B5CF6),
                                speaking     = Color(0xFF10B981),
                                interrupting = Color(0xFFEF4444),
                            )
                        )
                    )

                    Text(
                        "Tap the orb to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

/**
 * EXAMPLE 01 — Full-Screen UI with FluxVoiceScreen
 *
 * Demonstrates:
 *  - FluxVoiceTheme (dynamic color + system dark/light)
 *  - FluxVoiceScreen with all parameters
 *  - FluxVoiceMode switching with per-mode system prompts
 *  - rememberFluxVoice(key) — engine recreates when mode changes
 *  - autoShowSettingsForCustomMode — settings icon auto-appears in CUSTOM mode
 *  - showConfigureCard — "Configure your AI" card in empty state
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-provider-groq:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
 */

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.techrifter.fluxvoice.compose.FluxVoiceMode
import com.techrifter.fluxvoice.compose.FluxVoiceScreen
import com.techrifter.fluxvoice.compose.FluxVoiceTheme
import com.techrifter.fluxvoice.compose.rememberFluxVoice
import com.techrifter.fluxvoice.provider.groq.GroqLlmProvider
import com.techrifter.fluxvoice.stt.deepgram.DeepgramSttProvider
import com.techrifter.fluxvoice.tts.cartesia.CartesiaTtsProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // FluxVoiceTheme: dynamic Material You on Android 12+, system dark/light on older.
            // Replace with darkColorScheme() or your own MaterialTheme if preferred.
            FluxVoiceTheme {

                var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }

                // Pass mode as a key — engine recreates with new systemPrompt when mode changes.
                val controller = rememberFluxVoice(mode) {
                    systemPrompt = mode.defaultPrompt  // built-in per mode, or supply your own
                    sttProvider  = DeepgramSttProvider(apiKey = "YOUR_DEEPGRAM_KEY")
                    llmProvider  = GroqLlmProvider(apiKey = "YOUR_GROQ_KEY")
                    ttsProvider  = CartesiaTtsProvider(apiKey = "YOUR_CARTESIA_KEY")
                }

                FluxVoiceScreen(
                    controller    = controller,
                    initialMode   = mode,
                    onModeChange  = { mode = it },

                    // Tapping ⚙ navigates to your settings screen.
                    // Remove this param to hide the icon entirely (except in CUSTOM mode).
                    onSettingsClick = {
                        Toast.makeText(this, "Set your settings navigation for mode: ${mode.displayName}", Toast.LENGTH_SHORT).show()
                        // navController.navigate("settings")
                    },

                    // true (default): ⚙ icon auto-appears when user switches to CUSTOM mode,
                    // even if onSettingsClick wasn't provided.
                    // false: icon only appears when onSettingsClick is provided.
                    autoShowSettingsForCustomMode = true,

                    // true (default): "Configure your AI" card shows in empty state.
                    // Card is always hidden in CUSTOM mode regardless — header icon handles it.
                    // Set false to remove the card entirely.
                    showConfigureCard = true,
                )
            }
        }
    }
}

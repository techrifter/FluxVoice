/**
 * EXAMPLE 06 — Settings screen pattern for CUSTOM mode
 *
 * Demonstrates:
 *  - Storing voice config in DataStore (or any persistence layer)
 *  - Wiring stored settings back into rememberFluxVoice
 *  - Navigation from FluxVoiceScreen settings icon to a settings composable
 *  - Per-field controls for: systemPrompt, temperature, maxOutputTokens,
 *    maxContextTurns, vadEnabled, vadSensitivity, backchannelEnabled
 *  - onTurnMetrics callback — log real-time TTFT, TTFA, STT latency per turn
 *
 * The library provides the engine and config DSL.
 * Your app owns persistence, navigation, and settings UI.
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.1.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.1.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.1.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.1.0")
 *   implementation("androidx.datastore:datastore-preferences:1.1.4")
 *   implementation("androidx.navigation:navigation-compose:2.9.0")
 */

import android.util.Log
import android.content.Context
import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.techrifter.fluxvoice.compose.FluxVoiceMode
import com.techrifter.fluxvoice.compose.FluxVoiceScreen
import com.techrifter.fluxvoice.compose.FluxVoiceTheme
import com.techrifter.fluxvoice.compose.rememberFluxVoice
import com.techrifter.fluxvoice.provider.llm.OpenAiCompatibleLlmProvider
import com.techrifter.fluxvoice.stt.deepgram.DeepgramSttProvider
import com.techrifter.fluxvoice.tts.cartesia.CartesiaTtsProvider
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── DataStore setup ───────────────────────────────────────────────────────────

private val Context.voicePrefs by preferencesDataStore("voice_settings")

private object Keys {
    val SYSTEM_PROMPT     = stringPreferencesKey("system_prompt")
    val TEMPERATURE       = floatPreferencesKey("temperature")
    val MAX_TOKENS        = intPreferencesKey("max_tokens")
    val MAX_TURNS         = intPreferencesKey("max_turns")
    val VAD_ENABLED       = booleanPreferencesKey("vad_enabled")
    val VAD_SENSITIVITY   = intPreferencesKey("vad_sensitivity")
    val BACKCHANNEL       = booleanPreferencesKey("backchannel_enabled")
}

// Defaults: same as FluxVoiceConfig defaults
private const val DEFAULT_PROMPT       = "You are Flux, an intelligent voice AI assistant. Deliver high-signal, direct answers tailored for spoken audio without pleasantry filler, markdown, or lists."
private const val DEFAULT_TEMPERATURE  = 0.7f
private const val DEFAULT_MAX_TOKENS   = 2048
private const val DEFAULT_MAX_TURNS    = 10
private const val DEFAULT_VAD_ENABLED  = true
private const val DEFAULT_VAD_SENS     = 1000
private const val DEFAULT_BACKCHANNEL  = false

// ── Data class holding all settings ──────────────────────────────────────────

data class VoiceSettings(
    val systemPrompt: String  = DEFAULT_PROMPT,
    val temperature: Float    = DEFAULT_TEMPERATURE,
    val maxOutputTokens: Int  = DEFAULT_MAX_TOKENS,
    val maxContextTurns: Int  = DEFAULT_MAX_TURNS,
    val vadEnabled: Boolean   = DEFAULT_VAD_ENABLED,
    val vadSensitivity: Int   = DEFAULT_VAD_SENS,
    val backchannelEnabled: Boolean = DEFAULT_BACKCHANNEL,
)

// ── Read all settings from DataStore as a single flow ────────────────────────

fun settingsFlow(context: Context) = context.voicePrefs.data.map { prefs ->
    VoiceSettings(
        systemPrompt       = prefs[Keys.SYSTEM_PROMPT]   ?: DEFAULT_PROMPT,
        temperature        = prefs[Keys.TEMPERATURE]     ?: DEFAULT_TEMPERATURE,
        maxOutputTokens    = prefs[Keys.MAX_TOKENS]      ?: DEFAULT_MAX_TOKENS,
        maxContextTurns    = prefs[Keys.MAX_TURNS]       ?: DEFAULT_MAX_TURNS,
        vadEnabled         = prefs[Keys.VAD_ENABLED]     ?: DEFAULT_VAD_ENABLED,
        vadSensitivity     = prefs[Keys.VAD_SENSITIVITY] ?: DEFAULT_VAD_SENS,
        backchannelEnabled = prefs[Keys.BACKCHANNEL]     ?: DEFAULT_BACKCHANNEL,
    )
}

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxVoiceTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "voice") {

                    composable("voice") {
                        val context  = LocalContext.current
                        val settings by settingsFlow(context).collectAsState(initial = VoiceSettings())
                        var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }

                        // settings is a key — engine recreates when settings change
                        val controller = rememberFluxVoice(mode, settings) {
                            systemPrompt    = if (mode == FluxVoiceMode.CUSTOM) settings.systemPrompt
                                             else mode.defaultPrompt
                            temperature     = settings.temperature
                            maxOutputTokens = settings.maxOutputTokens
                            maxContextTurns = settings.maxContextTurns
                            vadEnabled      = settings.vadEnabled
                            vadSensitivity  = settings.vadSensitivity
                            backchannelEnabled = settings.backchannelEnabled

                            sttProvider = DeepgramSttProvider(apiKey = "YOUR_DEEPGRAM_KEY")
                            llmProvider = OpenAiCompatibleLlmProvider(apiKey = "YOUR_GROQ_KEY")
                            ttsProvider = CartesiaTtsProvider(apiKey = "YOUR_CARTESIA_KEY")

                            // onTurnMetrics fires after every turn with real latency data.
                            // Wire up your analytics, benchmark UI, or telemetry pipeline here.
                            onTurnMetrics { metrics ->
                                Log.d("VoiceMetrics",
                                    "STT=${metrics.sttLatencyMs}ms " +
                                    "TTFT=${metrics.timeToFirstTokenMs}ms " +
                                    "TTFA=${metrics.timeToFirstAudioMs}ms " +
                                    "total=${metrics.totalTurnDurationMs}ms " +
                                    "tokens=${metrics.totalTokens}"
                                )
                            }
                        }

                        FluxVoiceScreen(
                            controller      = controller,
                            initialMode     = mode,
                            onModeChange    = { mode = it },
                            onSettingsClick = { navController.navigate("settings") },
                        )
                    }

                    composable("settings") {
                        VoiceSettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

// ── Settings screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val settings by settingsFlow(context).collectAsState(initial = VoiceSettings())

    fun <T> save(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { context.voicePrefs.edit { block(it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── System prompt ─────────────────────────────────────────────────
            Text("System Prompt", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { save { prefs -> prefs[Keys.SYSTEM_PROMPT] = it } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Describe how the AI should behave…") }
            )

            // ── Temperature ───────────────────────────────────────────────────
            SettingSlider(
                label   = "Temperature",
                value   = settings.temperature,
                range   = 0f..2f,
                display = { "%.1f".format(it) },
                onChange = { save { prefs -> prefs[Keys.TEMPERATURE] = it } }
            )

            // ── Max output tokens ─────────────────────────────────────────────
            SettingSlider(
                label   = "Max Response Tokens",
                value   = settings.maxOutputTokens.toFloat(),
                range   = 128f..4096f,
                steps   = 30,
                display = { it.roundToInt().toString() },
                onChange = { save { prefs -> prefs[Keys.MAX_TOKENS] = it.roundToInt() } }
            )

            // ── Context turns ─────────────────────────────────────────────────
            SettingSlider(
                label   = "Context Window (turns)",
                value   = settings.maxContextTurns.toFloat(),
                range   = 2f..20f,
                steps   = 17,
                display = { it.roundToInt().toString() },
                onChange = { save { prefs -> prefs[Keys.MAX_TURNS] = it.roundToInt() } }
            )

            // ── Barge-in ──────────────────────────────────────────────────────
            SettingToggle(
                label    = "Barge-in (interrupt AI while speaking)",
                checked  = settings.vadEnabled,
                onChange = { save { prefs -> prefs[Keys.VAD_ENABLED] = it } }
            )

            if (settings.vadEnabled) {
                SettingSlider(
                    label   = "Barge-in Sensitivity",
                    value   = settings.vadSensitivity.toFloat(),
                    range   = 200f..3000f,
                    display = { it.roundToInt().toString() },
                    onChange = { save { prefs -> prefs[Keys.VAD_SENSITIVITY] = it.roundToInt() } }
                )
            }

            // ── Backchannels ──────────────────────────────────────────────────
            SettingToggle(
                label    = "Thinking fillers ("Got it.", "Sure.")",
                checked  = settings.backchannelEnabled,
                onChange = { save { prefs -> prefs[Keys.BACKCHANNEL] = it } }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Reusable setting widgets ──────────────────────────────────────────────────

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                display(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

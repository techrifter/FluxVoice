/**
 * EXAMPLE 04 — No TTS provider: use Android's built-in TextToSpeech (Fallback)
 *
 * Demonstrates:
 *  - Omitting ttsProvider entirely
 *  - onResponse callback receives the full AI response text
 *  - Feeding the response to android.speech.tts.TextToSpeech
 *
 * Use this when you don't want a Cartesia account, or want to control
 * speech synthesis yourself (rate, pitch, language, etc.).
 *
 * Dependencies (app/build.gradle.kts):
 *   implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
 *   implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
 *   // No fluxvoice-tts-cartesia needed
 */

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techrifter.fluxvoice.compose.FluxVoiceScreen
import com.techrifter.fluxvoice.compose.FluxVoiceTheme
import com.techrifter.fluxvoice.compose.rememberFluxVoice
import com.techrifter.fluxvoice.provider.llm.OpenAiCompatibleLlmProvider
import com.techrifter.fluxvoice.stt.deepgram.DeepgramSttProvider
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(1.0f)
            }
            Log.d("TTS", "Init status: $status")
        }

        setContent {
            FluxVoiceTheme {
                val controller = rememberFluxVoice {
                    sttProvider = DeepgramSttProvider(apiKey = "YOUR_DEEPGRAM_KEY")
                    llmProvider = OpenAiCompatibleLlmProvider(apiKey = "YOUR_GROQ_KEY")
                    // no ttsProvider — pipeline stops after LLM, fires onResponse

                    onResponse { response ->
                        Log.d("FV", "Response: $response")
                        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }

                FluxVoiceScreen(controller = controller)
            }
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

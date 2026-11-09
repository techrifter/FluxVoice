package com.techrifter.fluxvoice.data.tts

import android.util.Log
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import com.techrifter.fluxvoice.domain.tts.TtsState
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenAiTTS"
private const val OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech"
const val DEFAULT_OPENAI_VOICE = "alloy"
private const val SAMPLE_RATE = 24000

@Serializable
private data class OpenAiTtsRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String = DEFAULT_OPENAI_VOICE,
    val response_format: String = "pcm"
)

private data class OpenAiSpeakRequest(
    val text: String,
    val utteranceId: String,
    val apiKey: String,
    val voice: String
)

@Singleton
class OpenAiTtsEngine @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault,
    private val androidFallback: AndroidTtsEngine
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakChannel = Channel<OpenAiSpeakRequest>(Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)
    private val audioPlayer = PcmAudioTrackPlayer(TAG)

    private var workerJob: Job? = null
    private var idleDebounceJob: Job? = null

    @Volatile var activeVoice: String = DEFAULT_OPENAI_VOICE

    init {
        startWorker()
        engineScope.launch {
            androidFallback.state.collect { s ->
                if (pendingCount.get() == 0 && keyVault.getOpenAiKey().isBlank()) {
                    _state.value = s
                }
            }
        }
    }

    private fun startWorker() {
        workerJob = engineScope.launch {
            for (req in speakChannel) {
                processRequest(req)
            }
        }
    }

    override fun speak(text: String, utteranceId: String) {
        if (text.isBlank()) return
        val key = keyVault.getOpenAiKey()

        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking

        if (key.isBlank()) {
            Log.d(TAG, "No OpenAI key — delegating to Android TTS")
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                _state.value = TtsState.Idle
            }
            androidFallback.speak(text, utteranceId)
            return
        }

        speakChannel.trySend(OpenAiSpeakRequest(text, utteranceId, key, activeVoice.ifBlank { DEFAULT_OPENAI_VOICE }))
    }

    override fun stop() {
        idleDebounceJob?.cancel()
        idleDebounceJob = null
        workerJob?.cancel()
        audioPlayer.stop()
        pendingCount.set(0)
        while (speakChannel.tryReceive().isSuccess) { /* discard */ }
        androidFallback.stop()
        _state.value = TtsState.Idle
        startWorker()
    }

    override fun setSpeechRate(rate: Float) {
        androidFallback.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        androidFallback.setPitch(pitch)
    }

    override fun shutdown() {
        engineScope.cancel()
        audioPlayer.stop()
        androidFallback.shutdown()
    }

    private suspend fun processRequest(req: OpenAiSpeakRequest) {
        try {
            Log.d(TAG, "OpenAI synthesizing '${req.text.take(50)}'")
            val pcmBytes = callOpenAiApi(req) ?: return
            audioPlayer.playPcm(pcmBytes, sampleRate = SAMPLE_RATE, numChannels = 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI TTS error: ${e.message}", e)
            _state.value = TtsState.Error("OpenAI TTS error: ${e.message}")
        } finally {
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                idleDebounceJob?.cancel()
                idleDebounceJob = engineScope.launch {
                    delay(350L)
                    if (pendingCount.get() == 0) _state.value = TtsState.Idle
                }
            }
        }
    }

    private suspend fun callOpenAiApi(req: OpenAiSpeakRequest): ByteArray? {
        val payload = OpenAiTtsRequest(
            model = "tts-1",
            input = req.text,
            voice = req.voice
        )

        val response = httpClient.post(OPENAI_TTS_URL) {
            contentType(ContentType.Application.Json)
            bearerAuth(req.apiKey.trim())
            setBody(json.encodeToString(payload))
        }

        if (!response.status.isSuccess()) {
            val status = response.status.value
            val hint = when (status) {
                401 -> "Invalid OpenAI API key. Check Key Vault."
                429 -> "OpenAI TTS rate limit reached."
                else -> "OpenAI HTTP $status"
            }
            _state.value = TtsState.Error(hint)
            return null
        }

        return response.bodyAsBytes()
    }
}


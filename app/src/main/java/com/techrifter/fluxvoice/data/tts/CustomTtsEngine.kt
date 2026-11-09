package com.techrifter.fluxvoice.data.tts

import android.util.Log
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import com.techrifter.fluxvoice.domain.tts.TtsState
import io.ktor.client.HttpClient
import io.ktor.client.request.header
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

private const val TAG = "CustomBYOKTTS"
private const val SAMPLE_RATE = 24000

@Serializable
private data class CustomTtsRequestBody(
    val model: String = "default",
    val input: String,
    val voice: String = "default",
    val response_format: String = "pcm"
)

private data class CustomSpeakRequest(
    val text: String,
    val utteranceId: String,
    val endpointUrl: String,
    val apiKey: String,
    val model: String,
    val voice: String
)

@Singleton
class CustomTtsEngine @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault,
    private val androidFallback: AndroidTtsEngine
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakChannel = Channel<CustomSpeakRequest>(Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)
    private val audioPlayer = PcmAudioTrackPlayer(TAG)

    private var workerJob: Job? = null
    private var idleDebounceJob: Job? = null

    @Volatile var activeEndpointUrl: String = ""
    @Volatile var activeApiKey: String = ""
    @Volatile var activeModelId: String = "default"
    @Volatile var activeVoiceId: String = "default"

    init {
        startWorker()
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
        val endpoint = activeEndpointUrl.ifBlank { keyVault.getKey("custom_tts_url") }
        val key = activeApiKey.ifBlank { keyVault.getKey("custom_tts_key") }

        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking

        if (endpoint.isBlank()) {
            Log.d(TAG, "No Custom TTS endpoint specified — delegating to Android TTS")
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                _state.value = TtsState.Idle
            }
            androidFallback.speak(text, utteranceId)
            return
        }

        speakChannel.trySend(
            CustomSpeakRequest(
                text = text,
                utteranceId = utteranceId,
                endpointUrl = endpoint,
                apiKey = key,
                model = activeModelId,
                voice = activeVoiceId
            )
        )
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

    private suspend fun processRequest(req: CustomSpeakRequest) {
        try {
            Log.d(TAG, "Custom TTS synthesizing on ${req.endpointUrl}")
            val audioBytes = callCustomApi(req) ?: return
            audioPlayer.playWav(audioBytes, defaultSampleRate = SAMPLE_RATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom TTS error: ${e.message}", e)
            _state.value = TtsState.Error("Custom TTS error: ${e.message}")
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

    private suspend fun callCustomApi(req: CustomSpeakRequest): ByteArray? {
        val normalizedUrl = if (req.endpointUrl.endsWith("/v1/audio/speech") || req.endpointUrl.endsWith("/audio/speech")) {
            req.endpointUrl
        } else {
            "${req.endpointUrl.trimEnd('/')}/v1/audio/speech"
        }

        val payload = CustomTtsRequestBody(
            model = req.model,
            input = req.text,
            voice = req.voice
        )

        val response = httpClient.post(normalizedUrl) {
            contentType(ContentType.Application.Json)
            if (req.apiKey.isNotBlank()) {
                header("Authorization", "Bearer ${req.apiKey.trim()}")
            }
            setBody(json.encodeToString(payload))
        }

        if (!response.status.isSuccess()) {
            val status = response.status.value
            _state.value = TtsState.Error("Custom TTS HTTP $status on $normalizedUrl")
            return null
        }

        return response.bodyAsBytes()
    }
}


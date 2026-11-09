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

private const val TAG = "ElevenLabsTTS"
private const val ELEVENLABS_BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
const val DEFAULT_ELEVENLABS_VOICE_ID = "JBFqnCBsd6RMkjVDRZzb" // George
private const val DEFAULT_MODEL_ID = "eleven_flash_v2_5"
private const val SAMPLE_RATE = 24000

@Serializable
private data class ElevenLabsRequestBody(
    val text: String,
    val model_id: String = DEFAULT_MODEL_ID
)

private data class ElevenLabsSpeakRequest(
    val text: String,
    val utteranceId: String,
    val apiKey: String,
    val voiceId: String
)

@Singleton
class ElevenLabsTtsEngine @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault,
    private val androidFallback: AndroidTtsEngine
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakChannel = Channel<ElevenLabsSpeakRequest>(Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)
    private val audioPlayer = PcmAudioTrackPlayer(TAG)

    private var workerJob: Job? = null
    private var idleDebounceJob: Job? = null

    @Volatile var activeVoiceId: String = DEFAULT_ELEVENLABS_VOICE_ID

    init {
        startWorker()
        engineScope.launch {
            androidFallback.state.collect { s ->
                if (pendingCount.get() == 0 && keyVault.getElevenLabsKey().isBlank()) {
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
        val key = keyVault.getElevenLabsKey()

        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking

        if (key.isBlank()) {
            Log.d(TAG, "No ElevenLabs key — delegating to Android TTS")
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                _state.value = TtsState.Idle
            }
            androidFallback.speak(text, utteranceId)
            return
        }

        speakChannel.trySend(ElevenLabsSpeakRequest(text, utteranceId, key, activeVoiceId.ifBlank { DEFAULT_ELEVENLABS_VOICE_ID }))
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

    private suspend fun processRequest(req: ElevenLabsSpeakRequest) {
        try {
            Log.d(TAG, "ElevenLabs synthesizing '${req.text.take(50)}'")
            val pcmBytes = callElevenLabsApi(req) ?: return
            audioPlayer.playPcm(pcmBytes, sampleRate = SAMPLE_RATE, numChannels = 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs synthesis error: ${e.message}", e)
            _state.value = TtsState.Error("ElevenLabs error: ${e.message}")
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

    private suspend fun callElevenLabsApi(req: ElevenLabsSpeakRequest): ByteArray? {
        val url = "$ELEVENLABS_BASE_URL/${req.voiceId}?output_format=pcm_24000"
        val payload = ElevenLabsRequestBody(text = req.text)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("xi-api-key", req.apiKey.trim())
            setBody(json.encodeToString(payload))
        }

        if (!response.status.isSuccess()) {
            val status = response.status.value
            val hint = when (status) {
                401 -> "Invalid ElevenLabs API key. Check Key Vault."
                429 -> "ElevenLabs quota/rate limit reached."
                else -> "ElevenLabs HTTP $status"
            }
            _state.value = TtsState.Error(hint)
            return null
        }

        return response.bodyAsBytes()
    }
}


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

private const val TAG = "DeepgramAuraTTS"
private const val DEEPGRAM_TTS_URL = "https://api.deepgram.com/v1/speak"
const val DEFAULT_DEEPGRAM_AURA_VOICE = "aura-asteria-en"
private const val SAMPLE_RATE = 24000

@Serializable
private data class DeepgramAuraRequestBody(
    val text: String
)

private data class DeepgramSpeakRequest(
    val text: String,
    val utteranceId: String,
    val apiKey: String,
    val model: String
)

@Singleton
class DeepgramAuraTtsEngine @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val keyVault: KeyVault,
    private val androidFallback: AndroidTtsEngine
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakChannel = Channel<DeepgramSpeakRequest>(Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)
    private val audioPlayer = PcmAudioTrackPlayer(TAG)

    private var workerJob: Job? = null
    private var idleDebounceJob: Job? = null

    @Volatile var activeModel: String = DEFAULT_DEEPGRAM_AURA_VOICE

    init {
        startWorker()
        engineScope.launch {
            androidFallback.state.collect { s ->
                if (pendingCount.get() == 0 && keyVault.getDeepgramKey().isBlank()) {
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
        val key = keyVault.getDeepgramKey()

        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking

        if (key.isBlank()) {
            Log.d(TAG, "No Deepgram key — delegating to Android TTS")
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                _state.value = TtsState.Idle
            }
            androidFallback.speak(text, utteranceId)
            return
        }

        speakChannel.trySend(DeepgramSpeakRequest(text, utteranceId, key, activeModel.ifBlank { DEFAULT_DEEPGRAM_AURA_VOICE }))
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

    private suspend fun processRequest(req: DeepgramSpeakRequest) {
        try {
            Log.d(TAG, "Deepgram Aura synthesizing '${req.text.take(50)}'")
            val audioBytes = callDeepgramApi(req) ?: return
            audioPlayer.playWav(audioBytes, defaultSampleRate = SAMPLE_RATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Deepgram Aura error: ${e.message}", e)
            _state.value = TtsState.Error("Deepgram Aura error: ${e.message}")
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

    private suspend fun callDeepgramApi(req: DeepgramSpeakRequest): ByteArray? {
        val url = "$DEEPGRAM_TTS_URL?model=${req.model}&encoding=linear16&sample_rate=24000"
        val payload = DeepgramAuraRequestBody(text = req.text)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Token ${req.apiKey.trim()}")
            setBody(json.encodeToString(payload))
        }

        if (!response.status.isSuccess()) {
            val status = response.status.value
            val hint = when (status) {
                401 -> "Invalid Deepgram API key. Check Key Vault."
                429 -> "Deepgram rate limit reached."
                else -> "Deepgram HTTP $status"
            }
            _state.value = TtsState.Error(hint)
            return null
        }

        return response.bodyAsBytes()
    }
}


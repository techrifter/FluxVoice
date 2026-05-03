package com.techrifter.fluxvoice.tts.cartesia

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.techrifter.fluxvoice.core.state.TtsState
import com.techrifter.fluxvoice.core.tts.TtsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "CartesiaTtsProvider"
private const val CARTESIA_TTS_URL = "https://api.cartesia.ai/tts/bytes"
private const val CARTESIA_API_VERSION = "2026-03-01"
private const val DEFAULT_MODEL_ID = "sonic-3"
private const val SAMPLE_RATE = 44100

/** Cartesia's voice — natural, conversational. */
public const val CARTESIA_VOICE: String = "638efaaa-4d0c-442e-b701-3fae16aad012"

private data class SpeakItem(val text: String, val utteranceId: String)

/**
 * TTS provider backed by Cartesia's Sonic model.
 *
 * Streams WAV audio from the `/tts/bytes` endpoint and plays it via [AudioTrack].
 * Sentences are queued and played back-to-back, supporting sentence-level streaming
 * from the LLM for sub-2s end-to-end latency.
 *
 * ```kotlin
 * val controller = rememberFluxVoice {
 *     ttsProvider = CartesiaTtsProvider(
 *         apiKey  = BuildConfig.CARTESIA_API_KEY,
 *         voiceId = CARTESIA_VOICE  // or any voice from your Cartesia dashboard
 *     )
 * }
 * ```
 *
 * @param apiKey   Cartesia API key. Get one at play.cartesia.ai.
 * @param voiceId  Cartesia voice ID. Defaults to [CARTESIA_VOICE].
 * @param modelId  Cartesia model. Defaults to `sonic-3` (best quality + speed balance).
 */
public class CartesiaTtsProvider(
    private val apiKey: String,
    private val voiceId: String = CARTESIA_VOICE,
    private val modelId: String = DEFAULT_MODEL_ID
) : TtsProvider {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakChannel = Channel<SpeakItem>(Channel.UNLIMITED)
    private val pendingCount = AtomicInteger(0)

    @Volatile private var activeTrack: AudioTrack? = null
    private var workerJob: Job? = null
    private var idleDebounceJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    private companion object {
        const val IDLE_DEBOUNCE_MS = 400L
    }

    init {
        startWorker()
    }

    // ── TtsProvider ───────────────────────────────────────────────────────────

    override fun speak(text: String, utteranceId: String) {
        if (text.isBlank()) return
        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking
        speakChannel.trySend(SpeakItem(text, utteranceId))
    }

    override fun stop() {
        idleDebounceJob?.cancel(); idleDebounceJob = null
        workerJob?.cancel()
        activeTrack?.stop()
        activeTrack?.release()
        activeTrack = null
        pendingCount.set(0)
        while (speakChannel.tryReceive().isSuccess) { /* drain */ }
        _state.value = TtsState.Idle
        startWorker()
    }

    override fun shutdown() {
        engineScope.cancel()
        activeTrack?.stop()
        activeTrack?.release()
        activeTrack = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startWorker() {
        workerJob = engineScope.launch {
            for (item in speakChannel) processItem(item)
        }
    }

    private suspend fun processItem(item: SpeakItem) {
        try {
            Log.d(TAG, "Synthesizing '${item.text.take(60)}'")
            val bytes = fetchAudio(item.text) ?: return
            playWav(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}", e)
            _state.value = TtsState.Error("Cartesia TTS error: ${e.message}")
        } finally {
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                idleDebounceJob?.cancel()
                idleDebounceJob = engineScope.launch {
                    delay(IDLE_DEBOUNCE_MS)
                    if (pendingCount.get() == 0) _state.value = TtsState.Idle
                }
            }
        }
    }

    private suspend fun fetchAudio(text: String): ByteArray? {
        val body = CartesiaRequest(
            modelId    = modelId,
            transcript = text,
            voice      = CartesiaVoice("id", voiceId),
            outputFormat = CartesiaOutputFormat("wav", "pcm_s16le", SAMPLE_RATE)
        )
        val response = httpClient.post(CARTESIA_TTS_URL) {
            contentType(ContentType.Application.Json)
            header("X-API-Key", apiKey)
            header("Cartesia-Version", CARTESIA_API_VERSION)
            setBody(json.encodeToString(CartesiaRequest.serializer(), body))
        }
        Log.d(TAG, "HTTP ${response.status.value} for '${text.take(30)}'")
        if (!response.status.isSuccess()) {
            val hint = when (response.status.value) {
                401, 403 -> "Invalid Cartesia API key."
                429      -> "Cartesia rate limit. Try again shortly."
                else     -> "Cartesia HTTP ${response.status.value}"
            }
            _state.value = TtsState.Error(hint)
            return null
        }
        return response.bodyAsBytes()
    }

    private suspend fun playWav(wav: ByteArray) {
        val dataOffset = findDataOffset(wav)
        if (dataOffset < 0 || dataOffset >= wav.size) {
            Log.e(TAG, "WAV data chunk not found (offset=$dataOffset)")
            return
        }
        val channels = readShortLE(wav, 22).coerceIn(1, 2)
        val channelMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO
                          else AudioFormat.CHANNEL_OUT_MONO
        val pcm = wav.copyOfRange(dataOffset, wav.size)
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(minBuf))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        activeTrack = track
        track.write(pcm, 0, pcm.size)
        track.play()
        val durationMs = pcm.size.toLong() * 1000L / (SAMPLE_RATE.toLong() * channels * 2)
        try {
            delay(durationMs + 300L)
        } finally {
            track.stop()
            track.release()
            if (activeTrack === track) activeTrack = null
        }
    }

    private fun findDataOffset(wav: ByteArray): Int {
        for (i in 12 until wav.size - 8) {
            if (wav[i] == 'd'.code.toByte() && wav[i + 1] == 'a'.code.toByte() &&
                wav[i + 2] == 't'.code.toByte() && wav[i + 3] == 'a'.code.toByte()
            ) return i + 8
        }
        return -1
    }

    private fun readShortLE(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 1
        return (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

@Serializable
private data class CartesiaRequest(
    @SerialName("model_id") val modelId: String,
    val transcript: String,
    val voice: CartesiaVoice,
    @SerialName("output_format") val outputFormat: CartesiaOutputFormat
)

@Serializable
private data class CartesiaVoice(val mode: String, val id: String)

@Serializable
private data class CartesiaOutputFormat(
    val container: String,
    val encoding: String,
    @SerialName("sample_rate") val sampleRate: Int
)

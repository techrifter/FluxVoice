package com.techrifter.fluxvoice.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CartesiaTTS"
private const val CARTESIA_TTS_URL = "https://api.cartesia.ai/tts/bytes"
private const val CARTESIA_API_VERSION = "2024-06-10"
private const val DEFAULT_MODEL_ID = "sonic-3.6"
const val DEFAULT_CARTESIA_VOICE_ID = "638efaaa-4d0c-442e-b701-3fae16aad012"
private const val SAMPLE_RATE = 44100

// ── Cartesia request DTOs ──────────────────────────────────────────────────────

@Serializable
private data class CartesiaRequest(
    @SerialName("model_id") val modelId: String,
    val transcript: String,
    val voice: CartesiaVoice,
    @SerialName("output_format") val outputFormat: CartesiaOutputFormat,
    @SerialName("generation_config") val generationConfig: CartesiaGenerationConfig = CartesiaGenerationConfig()
)

@Serializable
private data class CartesiaVoice(val mode: String, val id: String)

@Serializable
private data class CartesiaOutputFormat(
    val container: String,
    val encoding: String,
    @SerialName("sample_rate") val sampleRate: Int
)

@Serializable
private data class CartesiaGenerationConfig(
    val speed: Float = 1.0f,
    val volume: Float = 1.0f
)

private data class SpeakRequest(
    val text: String,
    val utteranceId: String,
    val apiKey: String,
    val voiceId: String
)

// ── Engine ────────────────────────────────────────────────────────────────────

/**
 * TTS engine backed by Cartesia [sonic-3].
 *
 * Calls the `/tts/bytes` endpoint (WAV, PCM S16LE 44100 Hz) and plays the
 * result via [AudioTrack]. Sentences are queued and played back-to-back.
 * Falls back silently to [AndroidTtsEngine] when no Cartesia API key is set.
 */
@Singleton
class CartesiaTtsEngine @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    private val keyVault: KeyVault,
    /** Fallback used when cartesiaApiKey is blank. */
    private val androidFallback: AndroidTtsEngine
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Channel of items waiting to be synthesized and played. */
    private val speakChannel = Channel<SpeakRequest>(Channel.UNLIMITED)

    /**
     * Number of items currently queued or playing.
     *
     * **AtomicInteger** ensures that the synchronous [speak] increment and the
     * background [processRequest] decrement are race-free even when called from
     * different threads/coroutines simultaneously.
     */
    private val pendingCount = AtomicInteger(0)

    /** The [AudioTrack] currently playing, if any. */
    @Volatile private var activeTrack: AudioTrack? = null

    /** The sequential worker coroutine. Replaced on [stop]. */
    private var workerJob: Job? = null

    /** Cached settings — updated by a background collector so speak() never suspends. */
    @Volatile private var cachedApiKey  = ""
    @Volatile private var cachedVoiceId = DEFAULT_CARTESIA_VOICE_ID

    /**
     * Debounce job that delays the [TtsState.Idle] emission by [IDLE_DEBOUNCE_MS].
     *
     * When the LLM streams slowly, [pendingCount] can momentarily hit 0 between
     * consecutive sentences while the next HTTP fetch is in-flight. Without debouncing,
     * [TtsState.Idle] fires on every sentence boundary — the VAD stops and restarts,
     * the state machine oscillates, and [finishTurnAndRestart] could fire prematurely.
     * Debounce absorbs these brief gaps so the state stays [TtsState.Speaking]
     * for the entire multi-sentence response.
     */
    private var idleDebounceJob: Job? = null

    private companion object {
        private const val IDLE_DEBOUNCE_MS = 400L
    }

    init {
        startWorker()
        // Keep cachedApiKey/cachedVoiceId in sync so speak() never suspends to fetch settings.
        engineScope.launch {
            settingsRepository.getSettings().collect { s ->
                cachedApiKey  = s.cartesiaApiKey.ifBlank { keyVault.getCartesiaKey() }
                cachedVoiceId = s.cartesiaVoiceId.ifBlank { DEFAULT_CARTESIA_VOICE_ID }
            }
        }
        // Forward Android TTS fallback state whenever Cartesia has nothing pending.
        engineScope.launch {
            androidFallback.state.collect { s ->
                if (pendingCount.get() == 0) _state.value = s
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

    // ── TtsEngine ─────────────────────────────────────────────────────────────

    override fun speak(text: String, utteranceId: String) {
        if (text.isBlank()) return
        // Increment and flip state SYNCHRONOUSLY — no coroutine launch here.
        // This guarantees sentences are enqueued in the exact order speak() is called,
        // eliminating the race where multiple engineScope.launch blocks yield at the
        // settings fetch and reach speakChannel.send() in non-deterministic order.
        pendingCount.incrementAndGet()
        _state.value = TtsState.Speaking
        if (cachedApiKey.isBlank()) {
            Log.d(TAG, "No Cartesia key — falling back to Android TTS")
            val remaining = pendingCount.decrementAndGet()
            if (remaining <= 0) {
                pendingCount.set(0)
                _state.value = TtsState.Idle
            }
            androidFallback.speak(text, utteranceId)
            return
        }
        speakChannel.trySend(SpeakRequest(text, utteranceId, cachedApiKey, cachedVoiceId))
    }

    override fun stop() {
        // Cancel any pending debounced-idle so we emit Idle immediately.
        idleDebounceJob?.cancel()
        idleDebounceJob = null
        // Cancel the active playback: interrupts delay() → triggers AudioTrack cleanup
        workerJob?.cancel()
        activeTrack?.stop()
        activeTrack?.release()
        activeTrack = null
        pendingCount.set(0)
        // Drain remaining queued items
        while (speakChannel.tryReceive().isSuccess) { /* discard */ }
        androidFallback.stop()
        _state.value = TtsState.Idle
        // Restart the worker so future speak() calls work again
        startWorker()
    }

    override fun setSpeechRate(rate: Float) {
        androidFallback.setSpeechRate(rate) // only meaningful for the Android fallback
    }

    override fun setPitch(pitch: Float) {
        androidFallback.setPitch(pitch)
    }

    override fun shutdown() {
        engineScope.cancel()
        activeTrack?.stop()
        activeTrack?.release()
        activeTrack = null
        androidFallback.shutdown()
    }

    // ── Internal pipeline ─────────────────────────────────────────────────────

    private suspend fun processRequest(req: SpeakRequest) {
        try {
            Log.d(TAG, "Synthesizing '${req.text.take(60)}'")
            val bytes = callCartesiaApi(req) ?: return
            playWavBytes(bytes)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}", e)
            _state.value = TtsState.Error("Cartesia TTS error: ${e.message}")
        } finally {
            val remaining = pendingCount.decrementAndGet()
            Log.d(TAG, "Done — pending=$remaining")
            if (remaining <= 0) {
                // Clamp counter — stop() sets it to 0 before this finally block runs,
                // so decrement can go negative. Keeping it at 0 prevents the next
                // speak+done cycle from missing the == 0 check and staying stuck in Speaking.
                pendingCount.set(0)
                idleDebounceJob?.cancel()
                idleDebounceJob = engineScope.launch {
                    delay(IDLE_DEBOUNCE_MS)
                    if (pendingCount.get() == 0) _state.value = TtsState.Idle
                }
            }
        }
    }

    private suspend fun callCartesiaApi(req: SpeakRequest): ByteArray? {
        val body = CartesiaRequest(
            modelId    = DEFAULT_MODEL_ID,
            transcript = req.text,
            voice      = CartesiaVoice(mode = "id", id = req.voiceId),
            outputFormat = CartesiaOutputFormat(
                container  = "wav",
                encoding   = "pcm_s16le",
                sampleRate = SAMPLE_RATE
            )
        )

        val response = httpClient.post(CARTESIA_TTS_URL) {
            contentType(ContentType.Application.Json)
            header("X-API-Key", req.apiKey)
            header("Cartesia-Version", CARTESIA_API_VERSION)
            setBody(json.encodeToString(CartesiaRequest.serializer(), body))
        }

        Log.d(TAG, "HTTP ${response.status.value} for '${req.text.take(30)}'")

        if (!response.status.isSuccess()) {
            val errorHint = when (response.status.value) {
                401, 403 -> "Invalid Cartesia API key — check Settings → TTS."
                429      -> "Cartesia rate limit reached. Try again shortly."
                else     -> "Cartesia HTTP ${response.status.value}"
            }
            _state.value = TtsState.Error(errorHint)
            return null
        }

        return response.bodyAsBytes()
    }

    private suspend fun playWavBytes(wavBytes: ByteArray) {
        // Find the "data" chunk to get the PCM payload start offset
        val dataOffset = findDataChunkOffset(wavBytes)
        if (dataOffset < 0 || dataOffset >= wavBytes.size) {
            Log.e(TAG, "Could not find WAV data chunk (offset=$dataOffset, size=${wavBytes.size})")
            return
        }

        // Parse channel count from WAV header bytes 22–23 (little-endian)
        val numChannels = readShortLE(wavBytes, 22).coerceIn(1, 2)
        val channelConfig = if (numChannels == 2)
            AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

        val pcm = wavBytes.copyOfRange(dataOffset, wavBytes.size)
        Log.d(TAG, "Playing ${pcm.size} PCM bytes, channels=$numChannels")

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, channelConfig, AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_ASSISTANT plays through the main loudspeaker at full media
                    // volume so the user can hear the response clearly.
                    // AEC in VAD and Deepgram's AudioRecord (VOICE_COMMUNICATION source)
                    // still suppresses echo independently of the AudioTrack usage flag.
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(minBuf))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeTrack = track
        track.write(pcm, 0, pcm.size)
        track.play()

        // Duration in ms from PCM buffer size (S16LE = 2 bytes per sample per channel)
        val durationMs = pcm.size.toLong() * 1000L / (SAMPLE_RATE.toLong() * numChannels * 2)

        try {
            delay(durationMs + 300L) // 300 ms tail guard
        } finally {
            track.stop()
            track.release()
            if (activeTrack === track) activeTrack = null
        }
    }

    /** Searches for the "data" RIFF chunk and returns the byte offset of the PCM payload. */
    private fun findDataChunkOffset(wav: ByteArray): Int {
        for (i in 12 .. wav.size - 8) {
            if (wav[i] == 'd'.code.toByte() &&
                wav[i + 1] == 'a'.code.toByte() &&
                wav[i + 2] == 't'.code.toByte() &&
                wav[i + 3] == 'a'.code.toByte()
            ) {
                return i + 8 // skip "data" tag (4 bytes) + chunk size (4 bytes)
            }
        }
        return -1
    }

    private fun readShortLE(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 1
        return (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }
}

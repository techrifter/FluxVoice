package com.techrifter.fluxvoice.data.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.stt.SttEngine
import com.techrifter.fluxvoice.domain.stt.SttState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WhisperSttEngine"
private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
private const val SAMPLE_RATE = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/**
 * Offline-capable STT engine backed by the OpenAI Whisper API.
 *
 * Lifecycle:
 *  1. [startListening] — opens the microphone and starts streaming PCM to an in-memory buffer.
 *  2. [stopListening] — closes the microphone, wraps the PCM in a WAV header and POSTs it to
 *     the Whisper transcription endpoint, then emits [SttState.FinalResult].
 *
 * Requires the OpenAI API key to be set in app settings.
 * Requires RECORD_AUDIO permission to be granted before calling [startListening].
 */
@Singleton
class WhisperSttEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val settingsDataStore: SettingsDataStore,
    private val keyVault: KeyVault
) : SttEngine {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    @Volatile private var isRecording = false

    // ── SttEngine ──────────────────────────────────────────────────────────────

    override fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = SttState.Error("Microphone permission required")
            return
        }

        recordingJob?.cancel()
        recordingJob = scope.launch { captureAndTranscribe() }
    }

    override fun stopListening() {
        isRecording = false           // signals the recording loop to finish and upload
    }

    override fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        _state.value = SttState.Idle
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    private suspend fun captureAndTranscribe() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuffer, 4096)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: SecurityException) {
            _state.value = SttState.Error("Microphone permission required")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = SttState.Error("Failed to initialize AudioRecord")
            recorder.release()
            return
        }

        val pcmOut = ByteArrayOutputStream()
        val chunk = ByteArray(bufferSize)

        try {
            recorder.startRecording()
            isRecording = true
            _state.value = SttState.Listening

            while (isRecording) {
                val read = recorder.read(chunk, 0, bufferSize)
                if (read > 0) pcmOut.write(chunk, 0, read)
            }
        } catch (e: SecurityException) {
            _state.value = SttState.Error("Microphone permission required")
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.isEmpty() || pcmBytes.size < 320) {
            _state.value = SttState.Idle
            return
        }

        transcribe(buildWav(pcmBytes))
    }

    // ── Transcription ─────────────────────────────────────────────────────────

    private suspend fun transcribe(wavBytes: ByteArray) {
        val apiKey = keyVault.getOpenAiKey().ifBlank { settingsDataStore.settings.first().openAiApiKey }
        if (apiKey.isBlank()) {
            _state.value = SttState.Error("OpenAI API key not configured: set it in Settings")
            return
        }

        try {
            val response = httpClient.post(WHISPER_API_URL) {
                bearerAuth(apiKey)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("model", "whisper-1")
                            append(
                                key = "file",
                                value = wavBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                }
                            )
                        }
                    )
                )
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val parsedMsg = try {
                    Json.parseToJsonElement(errorBody).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                } catch (_: Exception) { null }
                val errorMsg = parsedMsg ?: "Whisper API error ${response.status.value}: ${response.status.description}"
                Log.e(TAG, "Whisper API error ${response.status.value}: $errorBody")
                _state.value = SttState.Error(errorMsg)
                return
            }

            val body = response.bodyAsText()
            val text = Json.parseToJsonElement(body)
                .jsonObject["text"]
                ?.jsonPrimitive
                ?.content
                ?.trim()

            _state.value = if (!text.isNullOrBlank()) SttState.FinalResult(text) else SttState.Idle

        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = SttState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = SttState.Error("Transcription failed: ${e.message ?: "unknown error"}")
        }
    }

    // ── WAV encoding ──────────────────────────────────────────────────────────

    /**
     * Wraps raw 16-bit mono PCM samples (recorded at [SAMPLE_RATE] Hz) in a
     * standard 44-byte RIFF/WAVE header so Whisper can identify the format.
     */
    private fun buildWav(pcm: ByteArray): ByteArray {
        val channels     = 1
        val bitsPerSample = 16
        val byteRate     = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign   = channels * bitsPerSample / 8
        val dataSize     = pcm.size
        val riffSize     = dataSize + 36           // total file size − 8

        val header = ByteArray(44)
        fun Int.le4(off: Int) { header[off] = (this and 0xff).toByte(); header[off+1] = ((this shr 8) and 0xff).toByte(); header[off+2] = ((this shr 16) and 0xff).toByte(); header[off+3] = ((this shr 24) and 0xff).toByte() }
        fun Int.le2(off: Int) { header[off] = (this and 0xff).toByte(); header[off+1] = ((this shr 8) and 0xff).toByte() }

        "RIFF".forEachIndexed { i, c -> header[i]      = c.code.toByte() }
        riffSize.le4(4)
        "WAVE".forEachIndexed { i, c -> header[8  + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12 + i] = c.code.toByte() }
        16.le4(16)                  // fmt chunk size
        1.le2(20)                   // PCM = 1
        channels.le2(22)
        SAMPLE_RATE.le4(24)
        byteRate.le4(28)
        blockAlign.le2(32)
        bitsPerSample.le2(34)
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        dataSize.le4(40)

        return header + pcm
    }
}

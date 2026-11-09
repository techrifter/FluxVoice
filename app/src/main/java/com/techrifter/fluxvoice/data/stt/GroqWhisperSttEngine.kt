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

private const val TAG = "GroqWhisperStt"
private const val GROQ_AUDIO_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
private const val SAMPLE_RATE = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

@Singleton
class GroqWhisperSttEngine @Inject constructor(
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
        isRecording = false
    }

    override fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        _state.value = SttState.Idle
    }

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
        val readBuffer = ByteArray(bufferSize)

        try {
            recorder.startRecording()
            isRecording = true
            _state.value = SttState.Listening
            Log.d(TAG, "AudioRecord started")

            while (isRecording) {
                val bytesRead = recorder.read(readBuffer, 0, readBuffer.size)
                if (bytesRead > 0) {
                    pcmOut.write(readBuffer, 0, bytesRead)
                }
            }
        } finally {
            try {
                recorder.stop()
                recorder.release()
            } catch (_: Exception) {}
        }

        val pcm = pcmOut.toByteArray()
        if (pcm.isEmpty() || pcm.size < 320) {
            _state.value = SttState.Idle
            return
        }

        sendToGroqWhisper(buildWav(pcm))
    }

    private suspend fun sendToGroqWhisper(wavBytes: ByteArray) {
        val apiKey = keyVault.getGroqKey().ifBlank {
            settingsDataStore.settings.first().groqApiKey
        }

        if (apiKey.isBlank()) {
            _state.value = SttState.Error("Groq API key not set. Add it in Key Vault or Settings.")
            return
        }

        try {
            val response = httpClient.post(GROQ_AUDIO_URL) {
                bearerAuth(apiKey.trim())
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = wavBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                }
                            )
                            append("model", "whisper-large-v3-turbo")
                            append("language", "en")
                            append("response_format", "json")
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
                val errorMsg = parsedMsg ?: "Groq Whisper API error: ${response.status.value}"
                Log.e(TAG, "Groq Whisper error ${response.status.value}: $errorBody")
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
            Log.e(TAG, "Transcription failed: ${e.message}", e)
            _state.value = SttState.Error("Transcription failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val riffSize = dataSize + 36

        val header = ByteArray(44)
        fun Int.le4(off: Int) {
            header[off] = (this and 0xff).toByte()
            header[off + 1] = ((this shr 8) and 0xff).toByte()
            header[off + 2] = ((this shr 16) and 0xff).toByte()
            header[off + 3] = ((this shr 24) and 0xff).toByte()
        }
        fun Int.le2(off: Int) {
            header[off] = (this and 0xff).toByte()
            header[off + 1] = ((this shr 8) and 0xff).toByte()
        }

        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        riffSize.le4(4)
        "WAVE".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12 + i] = c.code.toByte() }
        16.le4(16)
        1.le2(20)
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


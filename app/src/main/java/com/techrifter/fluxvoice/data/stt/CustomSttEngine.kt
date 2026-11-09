package com.techrifter.fluxvoice.data.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CustomBYOKSTT"
private const val SAMPLE_RATE = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

/**
 * Universal Custom (BYOK) Speech-to-Text engine.
 * Supports any OpenAI-compatible transcription endpoint (e.g. Together AI, FastWhisper, Whisper.cpp,
 * LocalAI, vLLM, Groq, or self-hosted audio transcription server).
 */
@Singleton
class CustomSttEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val keyVault: KeyVault
) : SttEngine {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    @Volatile private var isRecording = false

    @Volatile var activeEndpointUrl: String = ""
    @Volatile var activeApiKey: String = ""
    @Volatile var activeModelId: String = "whisper-1"

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
            Log.d(TAG, "AudioRecord started for Custom STT")

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

        sendToCustomStt(buildWav(pcm))
    }

    private suspend fun sendToCustomStt(wavBytes: ByteArray) {
        val rawEndpoint = activeEndpointUrl.trim().ifBlank {
            keyVault.getKey("custom_stt_url")
        }

        if (rawEndpoint.isBlank()) {
            _state.value = SttState.Error("Custom STT endpoint URL not configured. Enter endpoint in Studio.")
            return
        }

        val effectiveUrl = normalizeEndpoint(rawEndpoint)
        val apiKey = activeApiKey.trim().ifBlank {
            keyVault.getKey("custom_stt_key").ifBlank {
                keyVault.getKey("custom_endpoint")
            }
        }
        val model = activeModelId.trim().ifBlank { "whisper-1" }

        try {
            val response = httpClient.post(effectiveUrl) {
                if (apiKey.isNotBlank()) {
                    bearerAuth(apiKey)
                }
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
                            append("model", model)
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
                val errorMsg = parsedMsg ?: "Custom STT API error ${response.status.value}: ${response.status.description}"
                Log.e(TAG, "Custom STT error ${response.status.value}: $errorBody")
                _state.value = SttState.Error(errorMsg)
                return
            }

            val body = response.bodyAsText()
            val text = try {
                Json.parseToJsonElement(body)
                    .jsonObject["text"]
                    ?.jsonPrimitive
                    ?.content
                    ?.trim() ?: body.trim()
            } catch (_: Exception) {
                body.trim()
            }

            _state.value = if (text.isNotBlank()) SttState.FinalResult(text) else SttState.Idle

        } catch (e: CancellationException) {
            _state.value = SttState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom STT transcription failed: ${e.message}", e)
            _state.value = SttState.Error("Custom STT error: ${e.message ?: "network failure"}")
        }
    }

    private fun normalizeEndpoint(raw: String): String {
        val clean = raw.trim().removeSuffix("/")
        return when {
            clean.endsWith("/audio/transcriptions") || clean.endsWith("/transcriptions") -> clean
            clean.endsWith("/v1") -> "$clean/audio/transcriptions"
            clean.contains("/v1/") -> "$clean/audio/transcriptions"
            else -> "$clean/v1/audio/transcriptions"
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


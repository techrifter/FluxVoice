package com.techrifter.fluxvoice.data.elevenlabs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ElevenLabsVoiceSession"
private const val BASE_URL = "wss://api.elevenlabs.io/v1/convai/conversation"
private const val INPUT_SAMPLE_RATE = 16_000
private const val OUTPUT_SAMPLE_RATE = 16_000
private const val CHUNK_MS = 100  // send audio in 100 ms chunks

/**
 * Singleton wrapper around the ElevenLabs Conversational-AI WebSocket API.
 *
 * Usage:
 * 1. Call [attachActivity] from VoiceScreen once.
 * 2. Call [start] with your public agent ID.
 * 3. Observe [status], [conversationMode], [userTranscript], [aiResponse] and [error].
 * 4. Call [stop] to end the session.
 *
 * Audio pipeline: AudioRecord (VOICE_COMMUNICATION + AEC + NS) → base64 PCM 16 kHz → WebSocket
 *                WebSocket → base64 PCM 16 kHz chunks → AudioTrack (USAGE_VOICE_COMMUNICATION)
 */
@Singleton
class ElevenLabsVoiceSession @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {

    // ── Public state ───────────────────────────────────────────────────────────

    private val _status = MutableStateFlow("disconnected")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _conversationMode = MutableStateFlow("listening")
    val conversationMode: StateFlow<String> = _conversationMode.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Internal ───────────────────────────────────────────────────────────────

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activityRef: WeakReference<Activity>? = null

    private var websocket: WebSocket? = null
    private var micJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile private var isMuted = false

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0L, TimeUnit.MILLISECONDS)   // no timeout for long-lived WebSocket
        .build()

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Store a weak reference to the host activity.
     * Must be called before [start].
     */
    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /**
     * Starts a new ElevenLabs ConvAI session via WebSocket.
     *
     * @param agentId           Public ElevenLabs agent ID.
     * @param conversationToken Optional signed token from your server; when provided [agentId] is ignored.
     */
    fun start(agentId: String, conversationToken: String? = null) {
        if (_status.value == "connected" || _status.value == "connecting") return

        _status.value = "connecting"
        _userTranscript.value = ""
        _aiResponse.value = ""
        _error.value = null
        isMuted = false

        val urlStr = if (conversationToken != null)
            "$BASE_URL?conversation_token=$conversationToken"
        else
            "$BASE_URL?agent_id=$agentId"

        val request = Request.Builder().url(urlStr).build()

        websocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _status.value = "connected"
                startMicCapture(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _error.value = "Connection failed: ${t.message ?: "unknown"}"
                resetState()
                cleanupAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                resetState()
                cleanupAudio()
            }
        })
    }

    /** Ends the current session, stops audio, and resets all state. */
    fun stop() {
        micJob?.cancel()
        micJob = null
        websocket?.close(1000, "User ended session")
        websocket = null
        resetState()
        cleanupAudio()
    }

    /** Toggle microphone mute; audio sending pauses while muted. */
    fun toggleMute() {
        isMuted = !isMuted
        Log.d(TAG, "Mute toggled: isMuted=$isMuted")
    }

    /** Clears any displayed error. */
    fun clearError() { _error.value = null }

    // ── WebSocket message handling ─────────────────────────────────────────────

    private fun handleServerMessage(webSocket: WebSocket, text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            when (val type = json["type"]?.jsonPrimitive?.content) {

                "conversation_initiation_metadata" -> {
                    Log.d(TAG, "Session initiated")
                    // Override TTS output to raw PCM 16 kHz so we can play with AudioTrack directly
                    val initData = buildJsonObject {
                        put("type", "conversation_initiation_client_data")
                        putJsonObject("conversation_config_override") {
                            putJsonObject("tts") {
                                put("encoding", "pcm_16000")
                            }
                        }
                    }
                    webSocket.send(initData.toString())
                    _conversationMode.value = "listening"
                }

                "audio" -> {
                    _conversationMode.value = "speaking"
                    val b64 = json["audio_event"]
                        ?.jsonObject?.get("audio_base_64")?.jsonPrimitive?.content
                    if (!b64.isNullOrBlank()) {
                        val pcmBytes = Base64.decode(b64, Base64.NO_WRAP)
                        sessionScope.launch { playPcmChunk(pcmBytes) }
                    }
                }

                "agent_response" -> {
                    val resp = json["agent_response_event"]
                        ?.jsonObject?.get("agent_response")?.jsonPrimitive?.content
                    if (!resp.isNullOrBlank()) _aiResponse.value = resp
                }

                "user_transcript" -> {
                    _conversationMode.value = "listening"
                    val transcript = json["user_transcription_event"]
                        ?.jsonObject?.get("user_transcript")?.jsonPrimitive?.content
                    if (!transcript.isNullOrBlank()) _userTranscript.value = transcript
                }

                "interruption" -> {
                    // Agent was interrupted by user — flush the playback buffer immediately
                    try { audioTrack?.flush() } catch (_: Exception) {}
                    _conversationMode.value = "listening"
                }

                "ping" -> {
                    // ElevenLabs heartbeat — must respond with pong
                    val pingEvent = json["ping_event"]?.jsonObject
                    val eventId = pingEvent?.get("event_id")?.jsonPrimitive?.content
                    val pong = buildJsonObject {
                        put("type", "pong")
                        if (eventId != null) {
                            runCatching { put("event_id", eventId.toLong()) }
                        }
                    }
                    webSocket.send(pong.toString())
                }

                else -> Log.v(TAG, "Unhandled message type: $type")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle server message: ${e.message}", e)
        }
    }

    // ── Microphone capture ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startMicCapture(webSocket: WebSocket) {
        micJob?.cancel()
        micJob = sessionScope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )

            // Hardware AEC removes the speaker's audio from the mic signal
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(recorder.audioSessionId)?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(recorder.audioSessionId)?.enabled = true
            }

            // Bytes per 100 ms chunk: sampleRate * bytesPerSample * channels * seconds
            val chunkBytes = INPUT_SAMPLE_RATE * 2 * 1 * CHUNK_MS / 1000

            recorder.startRecording()
            try {
                val buf = ByteArray(chunkBytes)
                while (isActive && _status.value == "connected") {
                    val read = recorder.read(buf, 0, chunkBytes)
                    if (read > 0 && !isMuted) {
                        val b64 = Base64.encodeToString(buf, 0, read, Base64.NO_WRAP)
                        val msg = buildJsonObject { put("user_audio_chunk", b64) }
                        webSocket.send(msg.toString())
                    }
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
    }

    // ── Audio playback ─────────────────────────────────────────────────────────

    @Synchronized
    private fun playPcmChunk(pcm: ByteArray) {
        if (audioTrack == null || audioTrack!!.state == AudioTrack.STATE_UNINITIALIZED) {
            val minBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack!!.play()
        }
        try { audioTrack!!.write(pcm, 0, pcm.size) } catch (e: Exception) {
            Log.w(TAG, "AudioTrack write failed: ${e.message}")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun resetState() {
        _status.value = "disconnected"
        _conversationMode.value = "listening"
    }

    private fun cleanupAudio() {
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }
}

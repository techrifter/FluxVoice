package com.techrifter.fluxvoice.stt.deepgram

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.techrifter.fluxvoice.core.state.SttState
import com.techrifter.fluxvoice.core.stt.SttProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DeepgramSttProvider"
private const val SAMPLE_RATE = 16_000
private const val MAX_RECONNECTS = 2
private const val PRE_WARM_MAX_AGE_MS = 25_000L

/**
 * Real-time streaming STT provider backed by Deepgram's Nova-3 model.
 *
 * Streams raw PCM audio from the device microphone over a WebSocket to Deepgram,
 * receiving live partial transcripts and automatic sentence-level endpointing.
 *
 * Features:
 * - Socket pre-warming via [preConnect] for near-zero connection latency
 * - Hardware AEC + NoiseSuppressor on the AudioRecord session
 * - Transparent reconnect on transient drops (up to [MAX_RECONNECTS] times)
 * - Session ID guards prevent stale callbacks from corrupting active sessions
 *
 * ```kotlin
 * val controller = rememberFluxVoice {
 *     sttProvider = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_API_KEY)
 * }
 * ```
 *
 * @param apiKey  Deepgram API key. Get one at console.deepgram.com.
 * @param model   Deepgram model. Defaults to `nova-3` (best accuracy + speed).
 */
public class DeepgramSttProvider(
    private val apiKey: String,
    private val model: String = "nova-3",
    private val endpointingMs: Int = 500
) : SttProvider {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    @Volatile private var isRecording     = false
    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var lastPartialText = ""
    @Volatile private var finalEmitted    = false
    @Volatile private var sessionId       = 0L
    @Volatile private var reconnectAttempts = 0
    private val accumulatedFinalText = StringBuilder()

    private val okClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val deepgramUrl = "wss://api.deepgram.com/v1/listen" +
        "?model=$model" +
        "&language=en" +
        "&encoding=linear16" +
        "&sample_rate=$SAMPLE_RATE" +
        "&channels=1" +
        "&interim_results=true" +
        "&smart_format=true" +
        "&endpointing=$endpointingMs" +
        "&utterance_end_ms=1500"

    // ── Pre-warm ──────────────────────────────────────────────────────────────

    private inner class DelegatingListener : WebSocketListener() {
        @Volatile var delegate: WebSocketListener? = null
        private val openResult = CompletableDeferred<Unit>()
        val openedAtMs: Long get() = _openedAtMs
        @Volatile private var _openedAtMs = Long.MAX_VALUE

        override fun onOpen(ws: WebSocket, r: Response) {
            _openedAtMs = System.currentTimeMillis(); openResult.complete(Unit)
            delegate?.onOpen(ws, r)
        }
        override fun onMessage(ws: WebSocket, text: String) = delegate?.onMessage(ws, text) ?: Unit
        override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
            openResult.completeExceptionally(t)
            if (delegate == null) { preWarmSocket.compareAndSet(ws, null); preWarmListener.set(null) }
            delegate?.onFailure(ws, t, r)
        }
        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (delegate == null) { preWarmSocket.compareAndSet(ws, null); preWarmListener.set(null) }
            delegate?.onClosed(ws, code, reason)
        }
        suspend fun awaitOpen() = openResult.await()
    }

    private val preWarmSocket   = AtomicReference<WebSocket?>(null)
    private val preWarmListener = AtomicReference<DelegatingListener?>(null)
    private var preConnectJob: Job? = null

    override fun preConnect() {
        preConnectJob?.cancel()
        preWarmSocket.getAndSet(null)?.cancel()
        preWarmListener.set(null)
        preConnectJob = scope.launch {
            try {
                if (apiKey.isBlank()) return@launch
                val delegating = DelegatingListener()
                val ws = okClient.newWebSocket(
                    Request.Builder().url(deepgramUrl).header("Authorization", "Token ${apiKey.trim()}").build(),
                    delegating
                )
                delegating.awaitOpen()
                preWarmSocket.set(ws)
                preWarmListener.set(delegating)
                Log.d(TAG, "Pre-warm socket ready")
            } catch (_: Exception) { /* fall back to fresh connect in startListening */ }
        }
    }

    // ── SttProvider ───────────────────────────────────────────────────────────

    override fun startListening() {
        reconnectAttempts = 0
        preConnectJob?.cancel()
        isRecording = false
        sessionId++
        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
        activeSocket?.cancel()
        activeSocket = null
        recordingJob?.cancel()
        recordingJob = scope.launch { openAndStream() }
    }

    override fun stopListening() {
        isRecording = false
        if (!finalEmitted) {
            val pending = synchronized(accumulatedFinalText) {
                if (lastPartialText.isNotBlank()) lastPartialText else accumulatedFinalText.toString().trim()
            }
            if (pending.isNotEmpty()) {
                finalEmitted = true
                _state.value = SttState.FinalResult(pending)
                synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
            }
        }
        runCatching { activeSocket?.send("""{"type":"CloseStream"}""") }
    }

    override fun destroy() {
        isRecording = false
        sessionId++
        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
        preConnectJob?.cancel()
        preWarmSocket.getAndSet(null)?.cancel()
        preWarmListener.set(null)
        recordingJob?.cancel()
        runCatching { activeSocket?.close(1000, "Destroyed") }
        activeSocket = null
        _state.value = SttState.Idle
    }

    // ── Internal session ──────────────────────────────────────────────────────

    private suspend fun openAndStream() {
        val myId = sessionId
        lastPartialText = ""
        finalEmitted    = false
        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }

        if (apiKey.isBlank()) {
            _state.value = SttState.Error(
                "Speech recognition isn't configured. Add your Deepgram API key."
            )
            return
        }

        val connected = CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                connected.complete(Unit)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (sessionId != myId) return
                handleMessage(text, myId)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                if (!connected.isCompleted) connected.completeExceptionally(t)
                if (sessionId != myId) return
                isRecording = false
                if (!finalEmitted && lastPartialText.isEmpty()) {
                    Log.w(TAG, "Pre-warm socket failed (${t.message}), retrying fresh")
                    scope.launch { openAndStream() }
                    return
                }
                if (_state.value !is SttState.FinalResult) {
                    _state.value = SttState.Error(
                        "Couldn't connect to speech service. Check your internet connection."
                    )
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (sessionId != myId) return
                isRecording = false
                activeSocket = null
                val wasListening = _state.value.let { it is SttState.Listening || it is SttState.PartialResult }
                if (wasListening && !finalEmitted) {
                    if (reconnectAttempts < MAX_RECONNECTS) {
                        reconnectAttempts++
                        Log.w(TAG, "Session dropped — reconnecting ($reconnectAttempts/$MAX_RECONNECTS)")
                        scope.launch { openAndStream() }
                        return
                    }
                    reconnectAttempts = 0
                    _state.value = SttState.Error("Connection lost. Tap the mic to try again.")
                    return
                }
                reconnectAttempts = 0
                if (wasListening) _state.value = SttState.Idle
            }
        }

        // Attempt to reuse pre-warmed socket
        val preSocket   = preWarmSocket.getAndSet(null)
        val preDelegate = preWarmListener.getAndSet(null)
        val preAge      = preDelegate?.let { System.currentTimeMillis() - it.openedAtMs } ?: Long.MAX_VALUE

        val ws: WebSocket = if (preSocket != null && preDelegate != null && preAge < PRE_WARM_MAX_AGE_MS) {
            preDelegate.delegate = listener
            connected.complete(Unit)
            preSocket
        } else {
            preSocket?.cancel()
            okClient.newWebSocket(
                Request.Builder().url(deepgramUrl).header("Authorization", "Token ${apiKey.trim()}").build(),
                listener
            )
        }

        activeSocket = ws

        try {
            connected.await()
        } catch (e: Exception) {
            if (sessionId == myId) {
                _state.value = SttState.Error("Couldn't connect to speech service.")
            }
            ws.cancel()
            return
        }

        streamMic(ws, myId)
    }

    @SuppressLint("MissingPermission")
    private suspend fun streamMic(ws: WebSocket, myId: Long) {
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 4096)

        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2
            )
        } catch (e: Exception) {
            _state.value = SttState.Error("Failed to open microphone: ${e.message}")
            return
        }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            _state.value = SttState.Error("Microphone not available.")
            return
        }

        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        if (AcousticEchoCanceler.isAvailable()) aec = runCatching { AcousticEchoCanceler.create(ar.audioSessionId)?.also { it.enabled = true } }.getOrNull()
        if (NoiseSuppressor.isAvailable())       ns  = runCatching { NoiseSuppressor.create(ar.audioSessionId)?.also { it.enabled = true } }.getOrNull()

        try {
            ar.startRecording()
        } catch (e: Exception) {
            ar.release()
            aec?.release()
            ns?.release()
            _state.value = SttState.Error("Failed to start audio recording: ${e.message}")
            return
        }

        isRecording = true
        if (sessionId == myId) _state.value = SttState.Listening

        val buf = ByteArray(bufSize)
        try {
            while (isRecording && sessionId == myId) {
                val read = ar.read(buf, 0, buf.size)
                if (read <= 0) continue
                try {
                    ws.send(buf.copyOf(read).toByteString())
                } catch (e: Exception) {
                    isRecording = false
                    break
                }
            }
        } finally {
            runCatching { ar.stop() }
            runCatching { ar.release() }
            aec?.release()
            ns?.release()
        }

        runCatching { ws.send("""{"type":"CloseStream"}""") }
    }

    internal fun handleMessage(json: String, myId: Long = sessionId) {
        try {
            val obj  = Json.parseToJsonElement(json).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return
            if (sessionId != myId) return

            if (type == "UtteranceEnd") {
                if (!finalEmitted) {
                    val pending = synchronized(accumulatedFinalText) {
                        if (lastPartialText.isNotBlank()) lastPartialText else accumulatedFinalText.toString().trim()
                    }
                    if (pending.isNotEmpty()) {
                        finalEmitted = true
                        _state.value = SttState.FinalResult(pending)
                        isRecording  = false
                        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
                    }
                }
                return
            }
            if (type != "Results") return

            val transcript = obj["channel"]?.jsonObject
                ?.get("alternatives")?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("transcript")?.jsonPrimitive?.content ?: return

            val isFinal     = obj["is_final"]?.jsonPrimitive?.boolean    ?: false
            val speechFinal = obj["speech_final"]?.jsonPrimitive?.boolean ?: false

            val trimmed = transcript.trim()

            val currentFullTranscript = synchronized(accumulatedFinalText) {
                if (isFinal && trimmed.isNotEmpty()) {
                    if (accumulatedFinalText.isNotEmpty()) {
                        accumulatedFinalText.append(" ")
                    }
                    accumulatedFinalText.append(trimmed)
                }

                buildString {
                    if (accumulatedFinalText.isNotEmpty()) {
                        append(accumulatedFinalText.toString())
                    }
                    if (!isFinal && trimmed.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(trimmed)
                    }
                }.trim()
            }

            if (currentFullTranscript.isNotBlank()) {
                lastPartialText = currentFullTranscript
            }

            when {
                speechFinal -> {
                    val text = if (currentFullTranscript.isNotBlank()) currentFullTranscript else lastPartialText
                    if (text.isNotEmpty() && !finalEmitted) {
                        reconnectAttempts = 0
                        finalEmitted = true
                        _state.value = SttState.FinalResult(text)
                        isRecording  = false
                        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
                    }
                }
                else -> {
                    if (currentFullTranscript.isNotBlank()) {
                        _state.value = SttState.PartialResult(currentFullTranscript)
                    }
                }
            }
        } catch (_: Exception) { /* ignore malformed frames */ }
    }

    internal fun setSessionIdForTesting(id: Long) {
        sessionId = id
    }

    internal fun resetForTesting() {
        finalEmitted = false
        lastPartialText = ""
        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
        _state.value = SttState.Idle
    }
}

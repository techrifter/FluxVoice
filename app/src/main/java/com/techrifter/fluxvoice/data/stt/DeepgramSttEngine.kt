package com.techrifter.fluxvoice.data.stt

import android.annotation.SuppressLint
import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.techrifter.fluxvoice.data.audio.SharedAudioCapture
import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
import com.techrifter.fluxvoice.data.security.KeyVault
import com.techrifter.fluxvoice.domain.stt.SttEngine
import com.techrifter.fluxvoice.domain.stt.SttState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
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
import javax.inject.Inject
import javax.inject.Singleton

/** Max transparent reconnects before surfacing an error to the user. */
private const val MAX_RECONNECTS = 2

/** Discard pre-warm sockets older than this — Deepgram closes idle connections ~30 s. */
private const val PRE_WARM_MAX_AGE_MS = 25_000L

private const val DEEPGRAM_URL = "wss://api.deepgram.com/v1/listen" +
    "?model=nova-3" +
    "&language=en" +
    "&encoding=linear16" +
    "&sample_rate=${SharedAudioCapture.SAMPLE_RATE}" +
    "&channels=1" +
    "&interim_results=true" +
    "&smart_format=true" +
    "&endpointing=500" +
    "&utterance_end_ms=1500"

/**
 * Real-time streaming STT backed by the Deepgram Nova-3 model.
 *
 * Flow:
 *  1. [startListening] — opens a WebSocket to Deepgram and starts streaming raw PCM chunks.
 *  2. Deepgram sends interim transcripts → [SttState.PartialResult] on every frame.
 *  3. After [endpointing] ms of silence Deepgram sets speech_final=true → [SttState.FinalResult].
 *  4. [stopListening] — sends `CloseStream` so Deepgram finalises any in-flight transcript.
 *
 * Requires DEEPGRAM_API_KEY to be set in app settings.
 */
@Singleton
class DeepgramSttEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val keyVault: KeyVault,
    /** Shared single-microphone capture session — eliminates the double-AudioRecord problem. */
    private val sharedCapture: SharedAudioCapture
) : SttEngine {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    @Volatile private var isRecording   = false
    @Volatile private var activeSocket: WebSocket? = null

    /** Last non-blank transcript received from Deepgram (interim or is_final). */
    @Volatile private var lastPartialText: String = ""

    /** Accumulated text from finalized chunks (is_final: true) during the current session. */
    private val accumulatedFinalText = StringBuilder()

    /**
     * True once a [SttState.FinalResult] has been emitted for the current session.
     * Prevents a second emission if both automatic silence-detection AND manual stop
     * fire in quick succession.
     */
    @Volatile private var finalEmitted: Boolean = false

    /**
     * Monotonically increasing session counter.
     * Each [openSessionAndStream] call increments this, and WebSocket callbacks
     * capture the value at creation time. Any callback from a stale session is
     * silently ignored, preventing leaked connections from corrupting active sessions.
     */
    @Volatile private var sessionId: Long = 0L

    /**
     * Counts automatic reconnect attempts triggered by an unexpected [onClosed] while
     * LISTENING.  Reset on every user-initiated [startListening] call and on successful
     * speech so transient drops are invisible to the user, but after [MAX_RECONNECTS]
     * failed attempts we surface a human-readable error and stop looping.
     */
    @Volatile private var reconnectAttempts = 0

    // OkHttp client reused across sessions; no overall read timeout for streaming.
    private val okClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    // ── Pre-warm state ────────────────────────────────────────────────────────
    // A pre-warmed WebSocket is opened during the AI's THINKING/SPEAKING phase so
    // the next startListening() call skips the TLS handshake entirely (~300 ms saved).

    /** Delegating listener that wraps a pre-warmed socket; its [delegate] is swapped
     *  in when the socket is claimed by [openSessionAndStream]. */
    private inner class DelegatingListener : WebSocketListener() {
        @Volatile var delegate: WebSocketListener? = null
        private val openResult = CompletableDeferred<Unit>()

        /** Wall-clock time the handshake completed — used to reject stale pre-warm sockets. */
        val openedAtMs: Long get() = _openedAtMs
        @Volatile private var _openedAtMs = Long.MAX_VALUE

        override fun onOpen(webSocket: WebSocket, response: Response) {
            _openedAtMs = System.currentTimeMillis()
            openResult.complete(Unit)
            delegate?.onOpen(webSocket, response)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            delegate?.onMessage(webSocket, text)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            openResult.completeExceptionally(t)
            if (delegate == null) {
                preWarmedSocketRef.compareAndSet(webSocket, null)
                preWarmedListenerRef.set(null)
            }
            delegate?.onFailure(webSocket, t, response)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // If closed before being claimed, clear the pre-warm slot
            if (delegate == null) {
                preWarmedSocketRef.compareAndSet(webSocket, null)
                preWarmedListenerRef.set(null)
            }
            delegate?.onClosed(webSocket, code, reason)
        }

        /** Suspend until the WebSocket handshake succeeds (or throws on failure). */
        suspend fun awaitOpen() = openResult.await()
    }

    private val preWarmedSocketRef  = AtomicReference<WebSocket?>(null)
    private val preWarmedListenerRef = AtomicReference<DelegatingListener?>(null)
    private var preConnectJob: Job? = null

    // ── SttEngine ─────────────────────────────────────────────────────────────

    override fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = SttState.Error("Microphone permission required")
            return
        }
        // User-initiated tap always resets the reconnect counter so a fresh session
        // gets the full budget of transparent reconnect attempts.
        reconnectAttempts = 0
        // Cancel any in-flight pre-connect (openSessionAndStream will use the pre-warmed
        // socket if it already completed, or connect fresh if it hasn't yet).
        preConnectJob?.cancel()
        // Stop the old recording loop immediately so two AudioRecord sessions never overlap.
        isRecording = false
        // Invalidate the old session ID BEFORE cancelling the socket so that OkHttp's
        // onFailure("broken pipe") callback — which fires synchronously on cancel() —
        // sees a stale sessionId and discards the error instead of surfacing it.
        sessionId++
        // Force-close the old WebSocket without waiting for graceful close.
        // Its onClosed/onFailure callbacks will see a stale sessionId and do nothing.
        activeSocket?.cancel()
        activeSocket = null
        recordingJob?.cancel()
        recordingJob = scope.launch { openSessionAndStream() }
    }

    /**
     * Internal reconnect triggered by an unexpected [onClosed] while LISTENING.
     * Identical to the [startListening] machinery but intentionally does NOT reset
     * [reconnectAttempts] — callers manage the counter before calling here.
     */
    private fun reconnectAfterDrop() {
        preConnectJob?.cancel()
        isRecording = false
        sessionId++
        activeSocket?.cancel()
        activeSocket = null
        recordingJob?.cancel()
        recordingJob = scope.launch { openSessionAndStream() }
    }

    /**
     * Pre-warm the Deepgram WebSocket connection in the background.
     *
     * Call this as soon as it's known that [startListening] will be needed soon
     * (e.g. when a [SttState.FinalResult] is received and the AI pipeline starts).
     * The next [startListening]/[openSessionAndStream] call will reuse the already-
     * open socket, skipping the ~300 ms TLS handshake entirely.
     */
    override fun preConnect() {
        // Always cancel any previous pre-warm and open a FRESH socket.
        //
        // Keeping a pre-warm from a previous preConnect() call (e.g. from the
        // FinalResult handler) risks reusing a stale socket that the server closed
        // after the AI's long TTS response (~20 s idle = server timeout possible).
        // By always refreshing here, the socket used by startListening() is at most
        // a few hundred milliseconds old — well within any server keep-alive window.
        preConnectJob?.cancel()
        val staleSocket = preWarmedSocketRef.getAndSet(null)
        preWarmedListenerRef.set(null)
        staleSocket?.cancel()   // discard the old (possibly stale) socket

        preConnectJob = scope.launch {
            try {
                val apiKey = keyVault.getDeepgramKey().ifBlank {
                    settingsDataStore.settings.first().deepgramApiKey
                }
                if (apiKey.isBlank()) return@launch
                val request = Request.Builder()
                    .url(DEEPGRAM_URL)
                    .header("Authorization", "Token $apiKey")
                    .build()
                val delegatingListener = DelegatingListener()
                val ws = okClient.newWebSocket(request, delegatingListener)
                delegatingListener.awaitOpen()         // suspends until WSS handshake done
                preWarmedSocketRef.set(ws)
                preWarmedListenerRef.set(delegatingListener)
            } catch (_: Exception) {
                // Pre-connect failed — startListening() will fall back to a fresh connect.
            }
        }
    }

    override fun stopListening() {
        isRecording = false
        // If we already have speech buffered, emit it as FinalResult immediately
        // so the AI pipeline triggers even if Deepgram never sends speech_final=true.
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
        // Tell Deepgram to flush any remaining audio.
        // runCatching: socket may already be closed (broken pipe / OkHttp race).
        runCatching { activeSocket?.send("""{"type":"CloseStream"}""") }
    }

    override fun destroy() {
        isRecording = false
        sessionId++           // invalidate all pending callbacks
        preConnectJob?.cancel()
        preWarmedSocketRef.getAndSet(null)?.cancel()
        preWarmedListenerRef.set(null)
        recordingJob?.cancel()
        runCatching { activeSocket?.close(1000, "Engine destroyed") }
        activeSocket = null
        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
        _state.value = SttState.Idle
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private suspend fun openSessionAndStream() {
        // Session ID was already incremented by startListening() before the old socket was
        // cancelled, so old callbacks are already invalidated.  Just capture the current value.
        val myId = sessionId

        // Reset per-session state
        synchronized(accumulatedFinalText) {
            accumulatedFinalText.clear()
        }
        lastPartialText = ""
        finalEmitted    = false

        val apiKey = keyVault.getDeepgramKey().ifBlank {
            settingsDataStore.settings.first().deepgramApiKey
        }
        if (apiKey.isBlank()) {
            _state.value = SttState.Error(
                "Speech recognition isn't configured. Add your Deepgram API key in Settings."
            )
            return
        }

        val connected = CompletableDeferred<Unit>()

        val sessionListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (sessionId != myId) return
                handleMessage(text, myId)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connected.isCompleted) connected.completeExceptionally(t)
                if (sessionId != myId) return
                isRecording = false
                // If no audio has been exchanged yet, the pre-warm socket was likely
                // stale (server idle-timeout after a long TTS response). Retry silently.
                if (!finalEmitted && lastPartialText.isEmpty() &&
                    _state.value.let { it is SttState.Listening || it is SttState.Idle }
                ) {
                    Log.w(TAG, "Pre-warm socket failed early (${t.message}), retrying fresh")
                    scope.launch { openSessionAndStream() }
                    return
                }
                if (_state.value !is SttState.FinalResult) {
                    _state.value = SttState.Error(
                        "Couldn't connect to speech service. Check your internet connection."
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (sessionId != myId) return
                isRecording = false
                activeSocket = null

                val wasListening = _state.value.let {
                    it is SttState.Listening || it is SttState.PartialResult
                }

                // If the session closed while we were actively listening and no result was
                // produced, this is an unexpected drop (network hiccup / server timeout).
                // Reconnect transparently up to MAX_RECONNECTS times before giving up.
                if (wasListening && !finalEmitted) {
                    if (reconnectAttempts < MAX_RECONNECTS) {
                        reconnectAttempts++
                        Log.w(TAG, "Session dropped while listening — reconnecting ($reconnectAttempts/$MAX_RECONNECTS)")
                        reconnectAfterDrop()
                        return
                    }
                    reconnectAttempts = 0
                    _state.value = SttState.Error(
                        "Connection lost. Tap the mic to try again."
                    )
                    return
                }

                reconnectAttempts = 0
                if (wasListening) _state.value = SttState.Idle
            }
        }

        // ── Try to reuse the pre-warmed socket ────────────────────────────────
        // Atomically claim both the socket and its delegating listener so no
        // other coroutine can pick them up simultaneously.
        // Discard sockets older than PRE_WARM_MAX_AGE_MS — Deepgram's server
        // closes idle connections around 30 s; claiming a dead socket causes a
        // silent failure that only surfaces on the first audio send attempt.
        val preSocket   = preWarmedSocketRef.getAndSet(null)
        val preDelegate = preWarmedListenerRef.getAndSet(null)
        val preAge      = preDelegate?.let { System.currentTimeMillis() - it.openedAtMs }
                          ?: Long.MAX_VALUE

        val ws: WebSocket = if (preSocket != null && preDelegate != null
                                && preAge < PRE_WARM_MAX_AGE_MS) {
            // Socket is fresh and connected — wire the session listener and signal ready.
            preDelegate.delegate = sessionListener
            connected.complete(Unit)
            preSocket
        } else {
            // Pre-warm wasn't ready or is too old; open a fresh connection.
            preSocket?.cancel()
            val request = Request.Builder()
                .url(DEEPGRAM_URL)
                .header("Authorization", "Token $apiKey")
                .build()
            okClient.newWebSocket(request, sessionListener)
        }

        activeSocket = ws

        try {
            connected.await()
        } catch (e: Exception) {
            if (sessionId == myId) {
                _state.value = SttState.Error(
                    "Couldn't connect to speech service. Check your internet connection."
                )
            }
            ws.cancel()
            return
        }

        streamMicrophone(ws, myId)
    }

    /**
     * Subscribe to [SharedAudioCapture] and forward PCM chunks to the Deepgram
     * WebSocket until [isRecording] is cleared by [stopListening] or the session
     * is superseded by a newer [startListening] call.
     *
     * Using the shared capture means only one [android.media.AudioRecord] session
     * with hardware AEC is ever open, even when the VAD is also listening.
     *
     * **Broken-pipe safety**: `ws.send()` is guarded with try-catch because the
     * SharedAudioCapture callback runs on the IO capture thread independently of the
     * `isRecording` volatile flag.  There is a narrow race window where:
     *   1. The `isRecording` check passes (still true).
     *   2. `onFailure` / `startListening` closes the WebSocket on another thread.
     *   3. `ws.send()` throws IOException("broken pipe").
     * Catching the exception here stops the stream cleanly without crashing.
     */
    @SuppressLint("MissingPermission") // permission checked in startListening()
    private suspend fun streamMicrophone(ws: WebSocket, myId: Long) {
        isRecording = true
        if (sessionId == myId) _state.value = SttState.Listening

        val listenerTag = "deepgram-$myId"
        val done        = CompletableDeferred<Unit>()

        sharedCapture.addListener(listenerTag) { bytes, count ->
            // Exit condition: stopListening() cleared isRecording, or a newer session started.
            if (!isRecording || sessionId != myId) {
                sharedCapture.removeListener(listenerTag)
                done.complete(Unit)
                return@addListener
            }
            // Forward raw PCM S16LE bytes to Deepgram.
            // Wrap in try-catch: the WebSocket may have been closed between the
            // isRecording guard above and this send (broken-pipe race).
            try {
                ws.send(bytes.copyOf(count).toByteString())
            } catch (e: Exception) {
                // Socket closed unexpectedly — treat as end of this recording session.
                isRecording = false
                sharedCapture.removeListener(listenerTag)
                done.complete(Unit)
            }
        }

        try {
            done.await()  // suspends until isRecording=false or session replaced
        } finally {
            // Safety: ensure listener is removed even on coroutine cancellation.
            sharedCapture.removeListener(listenerTag)
        }

        // Flush any in-flight transcript. Guard against already-closed socket.
        runCatching { ws.send("""{"type":"CloseStream"}""") }
    }

    // ── Message handling ──────────────────────────────────────────────────────

    private fun handleMessage(json: String, myId: Long) {
        try {
            val obj  = Json.parseToJsonElement(json).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return

            // Guard — session may have been replaced between onMessage and here
            if (sessionId != myId) return

            // UtteranceEnd is sent by Deepgram as a hard endpoint fallback;
            // treat it the same as speech_final when we have buffered speech.
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

            // Only handle transcription results beyond this point
            if (type != "Results") return

            val transcript = obj["channel"]
                ?.jsonObject?.get("alternatives")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("transcript")
                ?.jsonPrimitive?.content
                ?: return

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
                // speech_final=true means Deepgram has detected end-of-utterance (endpointing).
                // Emit FinalResult immediately regardless of is_final — Deepgram sometimes
                // sends speech_final=true with is_final=false when it still has audio in-flight,
                // which previously caused the session to stay in Listening forever.
                speechFinal -> {
                    val text = if (currentFullTranscript.isNotBlank()) currentFullTranscript else lastPartialText
                    if (text.isNotEmpty() && !finalEmitted) {
                        reconnectAttempts = 0  // successful speech — reset reconnect budget
                        finalEmitted = true
                        _state.value = SttState.FinalResult(text)
                        isRecording = false
                        synchronized(accumulatedFinalText) { accumulatedFinalText.clear() }
                    }
                }
                // Partial update (phrase boundary or live interim)
                else -> {
                    if (currentFullTranscript.isNotBlank()) {
                        _state.value = SttState.PartialResult(currentFullTranscript)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed frames (keep-alives, metadata, etc.)
        }
    }
}


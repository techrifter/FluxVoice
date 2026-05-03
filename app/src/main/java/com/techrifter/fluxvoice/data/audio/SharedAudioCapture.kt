package com.techrifter.fluxvoice.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SharedAudioCapture"

/**
 * A single [AudioRecord] session shared by every component that needs raw PCM audio.
 *
 * **Why this exists**
 * Previously [VoiceActivityDetector] and [DeepgramSttEngine] each opened their own
 * [AudioRecord] instance simultaneously.  Running two concurrent [AudioRecord] sessions
 * with [MediaRecorder.AudioSource.VOICE_COMMUNICATION] is fragile:
 *  - Many OEM kernels allow only one session on the echo-cancelled source at a time.
 *  - Both devices fight for the hardware AEC reference signal.
 *  - Double the battery usage (two ADC + DSP pipelines open in parallel).
 *
 * **Design**
 *  - One [AudioRecord] opened with hardware AEC + NoiseSuppressor.
 *  - Consumers register a named listener ([addListener]) which receives raw PCM
 *    [ByteArray] chunks as they arrive from the microphone.
 *  - The capture loop **starts automatically** when the first listener is added
 *    and **stops automatically** when the last listener is removed.
 *  - Thread-safe: [addListener]/[removeListener] are synchronized; the capture
 *    loop snapshots the listener map before dispatching each chunk so iteration
 *    is always safe even if a listener removes itself mid-callback.
 */
@Singleton
class SharedAudioCapture @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING       = AudioFormat.ENCODING_PCM_16BIT
    }

    /** Called on the IO thread for each PCM chunk. [count] is the number of valid bytes. */
    fun interface PcmListener {
        fun onPcmChunk(bytes: ByteArray, count: Int)
    }

    private val lock      = Any()
    private val listeners = ConcurrentHashMap<String, PcmListener>()

    /**
     * Keep-alive reference count.  While this is > 0 the capture loop will keep
     * running even if all named listeners are removed (e.g. between conversation
     * turns when VAD is stopped but Deepgram hasn't re-registered yet).
     *
     * Use [keepAlive]/[releaseKeepAlive] from [VoiceViewModel] to bracket the
     * lifetime of an active voice session.
     */
    private val keepAliveCount = AtomicInteger(0)

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job?       = null
    private var recorder:  AudioRecord? = null
    private var aec:       AcousticEchoCanceler? = null
    private var ns:        NoiseSuppressor?       = null

    /** True while the underlying [AudioRecord] is actively recording. */
    @Volatile var isCapturing: Boolean = false
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register [listener] under [tag].  Starts the capture loop on the first add.
     * Duplicate [tag]s silently replace the previous listener.
     */
    fun addListener(tag: String, listener: PcmListener) {
        synchronized(lock) {
            listeners[tag] = listener
            if (!isCapturing) startCapture()
        }
    }

    /**
     * Unregister the listener identified by [tag].  Stops the capture loop when
     * no listeners remain **and** there are no active keep-alive references.
     * While [keepAlive] is held the capture loop keeps running even with zero
     * named listeners, eliminating the AudioRecord stop/restart between turns.
     */
    fun removeListener(tag: String) {
        synchronized(lock) {
            listeners.remove(tag)
            if (listeners.isEmpty() && keepAliveCount.get() == 0) stopCapture()
        }
    }

    /**
     * Prevent the capture loop from stopping when all named listeners are removed.
     * Each [keepAlive] call must be paired with exactly one [releaseKeepAlive] call.
     *
     * Typical usage: call [keepAlive] when a STANDARD-mode voice session starts
     * (e.g. [VoiceViewModel.init] or first mic tap) and [releaseKeepAlive] in
     * [VoiceViewModel.onCleared] / [VoiceViewModel.clearConversation].
     */
    fun keepAlive() {
        synchronized(lock) {
            keepAliveCount.incrementAndGet()
            if (!isCapturing) startCapture()
        }
    }

    /**
     * Release a keep-alive reference acquired via [keepAlive].
     * If the count drops to zero and there are no remaining named listeners, the
     * capture loop is stopped.
     */
    fun releaseKeepAlive() {
        synchronized(lock) {
            val remaining = keepAliveCount.decrementAndGet()
            if (remaining <= 0 && listeners.isEmpty()) stopCapture()
        }
    }

    /** Release all resources immediately (call from ViewModel.onCleared or Application teardown). */
    fun shutdown() {
        synchronized(lock) {
            listeners.clear()
            keepAliveCount.set(0)
            stopCapture()
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // permission checked in startListening() before reaching here
    private fun startCapture() {
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        val bufSize = maxOf(minBuf, 4096)

        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufSize * 2   // double buffer reduces risk of overrun
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord: ${e.message}")
            return
        }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord did not initialize")
            ar.release()
            return
        }

        // Apply audio effects once on the shared session so both VAD and STT
        // benefit from the same hardware echo-cancellation reference signal.
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(ar.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(ar.audioSessionId)?.also { it.enabled = true }
        }

        recorder   = ar
        isCapturing = true
        ar.startRecording()
        Log.d(TAG, "Capture started (bufSize=$bufSize, aec=${aec != null}, ns=${ns != null})")

        captureJob = scope.launch {
            val buf = ByteArray(bufSize)
            var errorStreak  = 0
            var shouldRestart = false
            try {
                while (isActive) {
                    val read = ar.read(buf, 0, buf.size)
                    if (read <= 0) {
                        // AudioRecord can return -1 (ERROR) / -2 (BAD_VALUE) / -3 (INVALID_OPERATION)
                        // transiently due to audio focus changes or OEM driver quirks.
                        // A sustained streak signals the session has silently died.
                        if (++errorStreak >= 10) {
                            Log.e(TAG, "AudioRecord persistent failure ($errorStreak errors), restarting")
                            shouldRestart = true
                            break
                        }
                        continue
                    }
                    errorStreak = 0
                    // Snapshot under lock so listeners that self-remove during onPcmChunk
                    // don't cause ConcurrentModificationException.
                    val snapshot = synchronized(lock) { listeners.values.toList() }
                    snapshot.forEach { cb -> cb.onPcmChunk(buf, read) }
                }
            } finally {
                runCatching { ar.stop() }
                runCatching { ar.release() }
                aec?.release(); aec = null
                ns?.release();  ns  = null
                if (recorder === ar) recorder = null
                isCapturing = false
                Log.d(TAG, "Capture stopped (shouldRestart=$shouldRestart)")
                // If the AudioRecord died with active consumers, immediately re-open it.
                if (shouldRestart) {
                    synchronized(lock) {
                        if (listeners.isNotEmpty() || keepAliveCount.get() > 0) {
                            Log.d(TAG, "Restarting AudioRecord after persistent failure")
                            startCapture()
                        }
                    }
                }
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        // recorder.stop() / release() handled in the coroutine's finally block
    }
}



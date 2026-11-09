package com.techrifter.fluxvoice.android.audio

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

private const val TAG = "FluxVoiceAudioCapture"

/**
 * Shared [AudioRecord] session for FluxVoice's internal audio consumers (VAD barge-in).
 *
 * One instance is created per [FluxVoiceEngine] and held alive across conversation
 * turns to eliminate the AudioRecord stop/restart latency between turns.
 *
 * Consumers register a named [PcmListener] via [addListener]; the capture loop starts
 * automatically on the first add and stops when all listeners are removed and no
 * keep-alive is held.
 */
internal class FluxVoiceAudioCapture {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING       = AudioFormat.ENCODING_PCM_16BIT
    }

    fun interface PcmListener {
        fun onPcmChunk(bytes: ByteArray, count: Int)
    }

    private val lock           = Any()
    private val listeners      = ConcurrentHashMap<String, PcmListener>()
    private val keepAliveCount = AtomicInteger(0)

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job?        = null
    private var recorder:   AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns:  NoiseSuppressor?      = null

    @Volatile var isCapturing: Boolean = false
        private set

    fun addListener(tag: String, listener: PcmListener) {
        synchronized(lock) {
            listeners[tag] = listener
            if (!isCapturing) startCapture()
        }
    }

    fun removeListener(tag: String) {
        synchronized(lock) {
            listeners.remove(tag)
            if (listeners.isEmpty() && keepAliveCount.get() == 0) stopCapture()
        }
    }

    /**
     * Prevent the capture loop from stopping between turns even when all named
     * listeners have been removed. Each [keepAlive] must be paired with [releaseKeepAlive].
     */
    fun keepAlive() {
        synchronized(lock) {
            keepAliveCount.incrementAndGet()
            if (!isCapturing) startCapture()
        }
    }

    fun releaseKeepAlive() {
        synchronized(lock) {
            val remaining = keepAliveCount.decrementAndGet()
            if (remaining <= 0 && listeners.isEmpty()) stopCapture()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            listeners.clear()
            keepAliveCount.set(0)
            stopCapture()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        val bufSize = maxOf(minBuf, 4096)

        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufSize * 2
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

        if (AcousticEchoCanceler.isAvailable()) {
            aec = runCatching { AcousticEchoCanceler.create(ar.audioSessionId)?.also { it.enabled = true } }.getOrNull()
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = runCatching { NoiseSuppressor.create(ar.audioSessionId)?.also { it.enabled = true } }.getOrNull()
        }

        recorder    = ar
        isCapturing = true
        try {
            ar.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}")
            ar.release()
            aec?.release()
            ns?.release()
            isCapturing = false
            return
        }
        Log.d(TAG, "Capture started (bufSize=$bufSize, aec=${aec != null}, ns=${ns != null})")

        captureJob = scope.launch {
            val buf = ByteArray(bufSize)
            var errorStreak  = 0
            var shouldRestart = false
            try {
                while (isActive) {
                    val read = ar.read(buf, 0, buf.size)
                    if (read <= 0) {
                        if (++errorStreak >= 10) {
                            Log.e(TAG, "AudioRecord persistent failure ($errorStreak errors), restarting")
                            shouldRestart = true
                            break
                        }
                        continue
                    }
                    errorStreak = 0
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
    }
}

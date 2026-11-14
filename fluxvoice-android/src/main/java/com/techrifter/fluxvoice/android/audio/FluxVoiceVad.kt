package com.techrifter.fluxvoice.android.audio

import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate

private const val TAG = "FluxVoiceVad"

/**
 * Barge-in detector backed by the native WebRTC GMM VAD.
 *
 * Only active while TTS is playing (SPEAKING state). Since VAD and STT are mutually
 * exclusive, a separate [FluxVoiceAudioCapture] instance handles VAD's mic needs.
 *
 * Sensitivity → WebRTC aggressiveness mapping (vadSensitivity range 200–3000):
 *   ≤ 600  → AGGRESSIVE      (high sensitivity, fastest trigger)
 *   ≤ 1500 → VERY_AGGRESSIVE (default, good for quiet rooms)
 *   > 1500 → VERY_AGGRESSIVE (low sensitivity, best for noisy environments, longer confirm)
 */
internal class FluxVoiceVad(private val audioCapture: FluxVoiceAudioCapture) {

    companion object {
        private const val LISTENER_TAG  = "fluxvoice-vad"
        private const val FRAME_SAMPLES = 160       // 10 ms at 16 kHz
        private const val FRAME_BYTES   = FRAME_SAMPLES * 2  // PCM S16LE

        private const val SPEECH_MS_HIGH   = 200   // ≤ 600  sensitivity (200ms)
        private const val SPEECH_MS_MEDIUM = 250   // ≤ 1500 sensitivity (250ms default)
        private const val SPEECH_MS_LOW    = 350   // > 1500 sensitivity (350ms strict/noisy)
    }

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile private var vad: VadWebRTC? = null

    private val frameBuffer = ByteArray(FRAME_BYTES)
    private var frameBufPos = 0

    /**
     * Arm the VAD for barge-in detection.
     *
     * @param threshold     vadSensitivity (200–3000). Lower = more sensitive.
     * @param onVoiceDetected  Invoked once on the IO thread when speech is confirmed.
     *                         The detector self-stops before calling back.
     */
    fun start(threshold: Int = 1_000, onVoiceDetected: () -> Unit) {
        stop()

        val (mode, speechMs) = when {
            threshold <= 600  -> Pair(Mode.AGGRESSIVE,      SPEECH_MS_HIGH)
            threshold <= 1500 -> Pair(Mode.VERY_AGGRESSIVE, SPEECH_MS_MEDIUM)
            else              -> Pair(Mode.VERY_AGGRESSIVE, SPEECH_MS_LOW)
        }

        vad = VadWebRTC(
            sampleRate        = SampleRate.SAMPLE_RATE_16K,
            frameSize         = FrameSize.FRAME_SIZE_160,
            mode              = mode,
            speechDurationMs  = speechMs,
            silenceDurationMs = 0
        )

        frameBufPos = 0
        isRunning   = true
        Log.d(TAG, "VAD armed (mode=$mode, confirmMs=$speechMs)")

        audioCapture.addListener(LISTENER_TAG) { bytes, count ->
            if (!isRunning) return@addListener
            processChunk(bytes, count, onVoiceDetected)
        }
    }

    fun stop() {
        isRunning   = false
        frameBufPos = 0
        audioCapture.removeListener(LISTENER_TAG)
        vad?.close()
        vad = null
    }

    private fun processChunk(bytes: ByteArray, count: Int, onVoiceDetected: () -> Unit) {
        var srcOffset = 0
        while (srcOffset < count && isRunning) {
            val toCopy = minOf(FRAME_BYTES - frameBufPos, count - srcOffset)
            System.arraycopy(bytes, srcOffset, frameBuffer, frameBufPos, toCopy)
            frameBufPos += toCopy
            srcOffset   += toCopy

            if (frameBufPos >= FRAME_BYTES) {
                analyzeFrame(onVoiceDetected)
                frameBufPos = 0
            }
        }
    }

    private fun analyzeFrame(onVoiceDetected: () -> Unit) {
        if (!isRunning) return
        val currentVad = vad ?: return

        val samples = ShortArray(FRAME_SAMPLES) { i ->
            val lo = frameBuffer[i * 2].toInt() and 0xFF
            val hi = frameBuffer[i * 2 + 1].toInt()
            ((hi shl 8) or lo).toShort()
        }

        val detected = try {
            currentVad.isSpeech(samples)
        } catch (e: Exception) {
            Log.w(TAG, "isSpeech skipped (VAD closed concurrently)")
            return
        }

        if (detected) {
            Log.d(TAG, "Barge-in detected")
            stop()
            onVoiceDetected()
        }
    }
}

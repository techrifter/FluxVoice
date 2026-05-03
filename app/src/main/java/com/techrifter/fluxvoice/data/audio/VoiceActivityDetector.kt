package com.techrifter.fluxvoice.data.audio

import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VAD"

/**
 * Barge-in detector backed by the native WebRTC VAD (GMM-based).
 *
 * The underlying algorithm — from Google's production WebRTC stack — analyses
 * six frequency sub-bands using Gaussian Mixture Models.  It distinguishes
 * actual speech from broadband noise (fans), low-frequency rumble (traffic,
 * HVAC), and random impulses far more reliably than energy- or ZCR-only
 * approaches.
 *
 * **Sensitivity → WebRTC aggressiveness mode mapping** (controlled by
 * Settings → Barge-in sensitivity slider, range 200–3000):
 *
 *   ≤ 600  (High)   → [Mode.NORMAL]          — permissive, fastest trigger
 *   ≤ 1500 (Medium) → [Mode.AGGRESSIVE]       — default, good for quiet rooms
 *   > 1500 (Low)    → [Mode.VERY_AGGRESSIVE]  — strictest, best for noisy environments
 *
 * The library's built-in [SPEECH_CONFIRM_MS]-ms hysteresis (50 ms) requires
 * several consecutive "speech" frames before the detector fires, suppressing
 * short impulse false positives without adding perceptible barge-in lag.
 *
 * TTS leakage: hardware AEC applied in [SharedAudioCapture] cancels the
 * device's own speaker signal before PCM reaches this detector.  The existing
 * 300 ms post-barge-in delay in [VoiceViewModel] covers residual echo decay.
 */
@Singleton
class VoiceActivityDetector @Inject constructor(
    private val sharedCapture: SharedAudioCapture
) {

    companion object {
        private const val LISTENER_TAG = "vad"

        /** 10 ms frame — native WebRTC VAD's finest granularity at 16 kHz. */
        private const val FRAME_SAMPLES = 160
        private const val FRAME_BYTES   = FRAME_SAMPLES * 2  // PCM S16LE

        // Consecutive speech required per sensitivity tier before barge-in fires.
        // Fan / HVAC noise rarely sustains 150 ms of consecutive VERY_AGGRESSIVE
        // positives — this is the primary fan-false-trigger defence.
        private const val SPEECH_MS_HIGH   = 100  // High  (≤ 600)  — AGGRESSIVE mode
        private const val SPEECH_MS_MEDIUM = 150  // Medium (≤ 1500) — VERY_AGGRESSIVE
        private const val SPEECH_MS_LOW    = 300  // Low   (> 1500)  — VERY_AGGRESSIVE
    }

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile private var vad: VadWebRTC? = null

    private val frameBuffer = ByteArray(FRAME_BYTES)
    private var frameBufPos = 0

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Arm the detector.
     *
     * @param threshold  vadSensitivity value (200–3000). Lower = more sensitive.
     * @param onVoiceDetected  Invoked once on the IO thread when speech is
     *   confirmed. The detector stops itself before calling back.
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
        Log.d(TAG, "VAD armed (mode=$mode, speechDurationMs=${speechMs}ms)")

        sharedCapture.addListener(LISTENER_TAG) { bytes, count ->
            if (!isRunning) return@addListener
            processChunk(bytes, count, onVoiceDetected)
        }
    }

    /** Disarm the detector and release the native WebRTC handle. */
    fun stop() {
        isRunning   = false
        frameBufPos = 0
        sharedCapture.removeListener(LISTENER_TAG)
        vad?.close()
        vad = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun processChunk(
        bytes: ByteArray,
        count: Int,
        onVoiceDetected: () -> Unit
    ) {
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
            // VAD was closed by a concurrent stop() call — this frame is a no-op.
            Log.w(TAG, "isSpeech skipped (VAD closed concurrently)")
            return
        }

        if (detected) {
            Log.d(TAG, "BARGE-IN detected")
            stop()
            onVoiceDetected()
        }
    }
}

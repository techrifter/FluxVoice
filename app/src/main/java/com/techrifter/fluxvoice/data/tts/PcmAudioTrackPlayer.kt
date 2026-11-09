package com.techrifter.fluxvoice.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Reusable, high-performance static/streaming AudioTrack player for TTS engines.
 * Handles PCM S16LE buffer queueing, RIFF WAV header unpacking, and clean playback cancellation on barge-in.
 */
class PcmAudioTrackPlayer(private val tag: String = "PcmAudioTrackPlayer") {

    @Volatile
    private var activeTrack: AudioTrack? = null

    /**
     * Plays raw 16-bit PCM audio buffer on AudioTrack and suspends until playback finishes.
     */
    suspend fun playPcm(
        pcm: ByteArray,
        sampleRate: Int = 24000,
        numChannels: Int = 1
    ) {
        if (pcm.isEmpty()) return

        // Unpack RIFF WAV header if present (44 bytes: 'RIFF....WAVE')
        val rawPcm = if (pcm.size > 44 &&
            pcm[0] == 'R'.code.toByte() && pcm[1] == 'I'.code.toByte() &&
            pcm[2] == 'F'.code.toByte() && pcm[3] == 'F'.code.toByte()
        ) {
            pcm.copyOfRange(44, pcm.size)
        } else {
            pcm
        }

        val channelConfig = if (numChannels == 2)
            AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(rawPcm.size.coerceAtLeast(minBuf))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeTrack = track
        track.write(rawPcm, 0, rawPcm.size)
        track.play()

        val durationMs = rawPcm.size.toLong() * 1000L / (sampleRate.toLong() * numChannels * 2)
        try {
            delay(durationMs + 180L) // 180ms buffer guard
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
            if (activeTrack === track) activeTrack = null
        }
    }

    /**
     * Plays WAV audio buffer by extracting the PCM payload from RIFF data chunk.
     */
    suspend fun playWav(wavBytes: ByteArray, defaultSampleRate: Int = 24000) {
        val dataOffset = findDataChunkOffset(wavBytes)
        if (dataOffset < 0 || dataOffset >= wavBytes.size) {
            // Assume raw PCM fallback if header not found
            playPcm(wavBytes, defaultSampleRate, 1)
            return
        }

        val numChannels = readShortLE(wavBytes, 22).coerceIn(1, 2)
        val sampleRate = readIntLE(wavBytes, 24).let { if (it > 0) it else defaultSampleRate }
        val pcm = wavBytes.copyOfRange(dataOffset, wavBytes.size)
        playPcm(pcm, sampleRate, numChannels)
    }

    fun stop() {
        runCatching {
            activeTrack?.stop()
            activeTrack?.release()
        }
        activeTrack = null
    }

    private fun findDataChunkOffset(wav: ByteArray): Int {
        for (i in 12 until wav.size - 8) {
            if (wav[i] == 'd'.code.toByte() &&
                wav[i + 1] == 'a'.code.toByte() &&
                wav[i + 2] == 't'.code.toByte() &&
                wav[i + 3] == 'a'.code.toByte()
            ) {
                return i + 8
            }
        }
        return -1
    }

    private fun readShortLE(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 1
        return (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readIntLE(buf: ByteArray, offset: Int): Int {
        if (offset + 3 >= buf.size) return 0
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }
}


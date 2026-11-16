package com.techrifter.fluxvoice.data.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.techrifter.fluxvoice.domain.tts.TtsEngine
import com.techrifter.fluxvoice.domain.tts.TtsState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android built-in TTS engine.
 *
 * **Sentence-level streaming for low latency:**
 * Call [speak] for each sentence as it arrives from the LLM token stream.
 * Sentences are internally queued via [TextToSpeech.QUEUE_ADD] and played
 * back-to-back — [state] only transitions to [TtsState.Idle] when ALL queued
 * utterances have finished, preventing false "done" signals between sentences.
 *
 * This design achieves <500ms TTS onset latency because the first sentence
 * starts playing while the LLM is still generating the rest of the response.
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    @Volatile private var isInitialized = false

    private data class PendingSpeak(val text: String, val utteranceId: String)
    private val pendingQueue = ConcurrentLinkedQueue<PendingSpeak>()

    @Volatile private var pendingLanguage: String? = null
    @Volatile private var pendingSpeechRate: Float? = null
    @Volatile private var pendingPitch: Float? = null

    /**
     * Tracks the number of utterances currently queued/playing.
     * State → Idle only when this counter reaches zero.
     */
    private val pendingUtterances = AtomicInteger(0)

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _state.value = TtsState.Speaking
        }
        override fun onDone(utteranceId: String) {
            if (pendingUtterances.decrementAndGet() <= 0) {
                pendingUtterances.set(0)
                _state.value = TtsState.Idle
            }
        }
        @Deprecated("Deprecated in API 21")
        override fun onError(utteranceId: String) {
            pendingUtterances.decrementAndGet()
            _state.value = TtsState.Error("TTS playback error")
        }
        override fun onError(utteranceId: String, errorCode: Int) {
            pendingUtterances.decrementAndGet()
            _state.value = TtsState.Error("TTS error (code $errorCode)")
        }
    }

    init {
        // TextToSpeech must be initialised on the main thread
        Handler(Looper.getMainLooper()).post {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    tts?.setOnUtteranceProgressListener(utteranceListener)
                    
                    // Apply any pending configuration
                    pendingLanguage?.let { tts?.language = Locale.forLanguageTag(it) }
                    pendingSpeechRate?.let { tts?.setSpeechRate(it) }
                    pendingPitch?.let { tts?.setPitch(it) }

                    // Drain any queued speak requests
                    while (!pendingQueue.isEmpty()) {
                        val req = pendingQueue.poll() ?: break
                        pendingUtterances.incrementAndGet()
                        _state.value = TtsState.Speaking
                        tts?.speak(req.text, TextToSpeech.QUEUE_ADD, null, req.utteranceId)
                    }
                } else {
                    pendingQueue.clear()
                    _state.value = TtsState.Error("TTS initialisation failed (status $status)")
                }
            }
        }
    }

    // ── TtsEngine ─────────────────────────────────────────────────────────────

    override fun speak(text: String, utteranceId: String) {
        if (text.isBlank()) return
        if (!isInitialized) {
            _state.value = TtsState.Speaking
            pendingQueue.add(PendingSpeak(text, utteranceId))
            return
        }
        pendingUtterances.incrementAndGet()
        _state.value = TtsState.Speaking
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun stop() {
        pendingQueue.clear()
        pendingUtterances.set(0)
        tts?.stop()
        _state.value = TtsState.Idle
    }

    override fun setSpeechRate(rate: Float) {
        pendingSpeechRate = rate
        if (isInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    override fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    override fun setLanguage(languageCode: String) {
        pendingLanguage = languageCode
        if (isInitialized) {
            tts?.language = Locale.forLanguageTag(languageCode)
        }
    }

    override fun shutdown() {
        pendingQueue.clear()
        pendingUtterances.set(0)
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TtsState.Idle
    }
}


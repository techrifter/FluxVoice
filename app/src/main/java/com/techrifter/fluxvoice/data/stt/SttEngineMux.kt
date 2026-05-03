package com.techrifter.fluxvoice.data.stt

import com.techrifter.fluxvoice.data.local.datastore.SettingsDataStore
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.stt.SttEngine
import com.techrifter.fluxvoice.domain.stt.SttState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A multiplexing [SttEngine] that delegates to either [WhisperSttEngine] or
 * [DeepgramSttEngine] based on [AppSettings.sttProvider].
 *
 * When the user changes the STT provider in Settings, the active engine switches
 * automatically via [flatMapLatest] — the new engine's state flow is collected
 * and any previous subscription is cancelled.
 */
@Singleton
class SttEngineMux @Inject constructor(
    private val whisper:  WhisperSttEngine,
    private val deepgram: DeepgramSttEngine,
    settingsDataStore: SettingsDataStore
) : SttEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Currently selected provider — hot StateFlow. */
    private val provider: StateFlow<SttProvider> = settingsDataStore.settings
        .map { it.sttProvider }
        .stateIn(scope, SharingStarted.Eagerly, SttProvider.DEEPGRAM)

    /**
     * Forwards from the active engine using [flatMapLatest]:
     * switching provider immediately cancels the old subscription.
     */
    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    init {
        scope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            provider.flatMapLatest { p ->
                when (p) {
                    SttProvider.WHISPER   -> whisper.state
                    SttProvider.DEEPGRAM  -> deepgram.state
                }
            }.collect { _state.value = it }
        }
    }

    // ── Delegate to the active engine ─────────────────────────────────────────

    override fun startListening() = activeEngine().startListening()
    override fun stopListening()  = activeEngine().stopListening()
    override fun preConnect()     = activeEngine().preConnect()

    override fun destroy() {
        whisper.destroy()
        deepgram.destroy()
    }

    private fun activeEngine(): SttEngine = when (provider.value) {
        SttProvider.WHISPER  -> whisper
        SttProvider.DEEPGRAM -> deepgram
    }
}




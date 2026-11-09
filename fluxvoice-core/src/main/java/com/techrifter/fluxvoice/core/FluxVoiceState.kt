package com.techrifter.fluxvoice.core

import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.core.state.VoiceState

/**
 * Snapshot of the complete voice conversation state.
 * Collect [FluxVoiceController.state] to drive any custom UI.
 */
public data class FluxVoiceState(
    /** Current phase of the conversation pipeline. */
    val voiceState: VoiceState = VoiceState.IDLE,

    /** Live partial transcript shown while the user is speaking. */
    val partialTranscript: String = "",

    /** Confirmed final transcript (what the user said this turn). */
    val transcript: String = "",

    /** AI response text accumulated from the token stream. */
    val aiResponse: String = "",

    /** Non-null when an error should be surfaced to the user. */
    val errorMessage: String? = null,

    /** Performance telemetry and token accounting for the active/latest turn. */
    val metrics: TurnMetrics? = null
)

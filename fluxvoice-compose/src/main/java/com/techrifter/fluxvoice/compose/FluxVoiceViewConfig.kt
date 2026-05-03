package com.techrifter.fluxvoice.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual configuration for [FluxVoiceView].
 *
 * All fields have sensible defaults — start with `FluxVoiceViewConfig()` and tweak as needed.
 *
 * ```kotlin
 * FluxVoiceView(
 *     controller = controller,
 *     config = FluxVoiceViewConfig(
 *         size           = 200.dp,
 *         showTranscript = true,
 *         showBrandName  = false,
 *         colors         = FluxVoiceColors(listening = Color(0xFF00FF88))
 *     )
 * )
 * ```
 */
@Immutable
public data class FluxVoiceViewConfig(
    /** Diameter of the FluxVoice tap target. */
    val size: Dp = 240.dp,

    /** Show live user transcript and AI response bubbles. */
    val showTranscript: Boolean = true,

    /** Show the "FluxVoice" brand wordmark above the FluxVoice UI. */
    val showBrandName: Boolean = true,

    /** Show the state label ("Listening", "Thinking…") below the FluxVoice UI. */
    val showStateLabel: Boolean = true,

    /** Show the subtle hint label ("tap to speak", "tap to interrupt") below the state. */
    val showHintLabel: Boolean = true,

    /** Color palette per voice state. */
    val colors: FluxVoiceColors = FluxVoiceColors()
)

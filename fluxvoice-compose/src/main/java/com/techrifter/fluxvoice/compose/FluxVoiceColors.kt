package com.techrifter.fluxvoice.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color palette for each [com.techrifter.fluxvoice.core.state.VoiceState] phase.
 *
 * Pass a custom [FluxVoiceColors] to [FluxVoiceViewConfig] to match your app's brand.
 *
 * ```kotlin
 * FluxVoiceView(
 *     controller = controller,
 *     config = FluxVoiceViewConfig(
 *         colors = FluxVoiceColors(
 *             idle      = Color(0xFF64748B),
 *             listening = Color(0xFF3B82F6),
 *             thinking  = Color(0xFF8B5CF6),
 *             speaking  = Color(0xFF10B981)
 *         )
 *     )
 * )
 * ```
 */
@Immutable
public data class FluxVoiceColors(
    val idle:         Color = Color(0xFF64748B),  // slate-500
    val listening:    Color = Color(0xFF3B82F6),  // blue-500
    val thinking:     Color = Color(0xFF8B5CF6),  // violet-500
    val speaking:     Color = Color(0xFF10B981),  // emerald-500
    val interrupting: Color = Color(0xFFEF4444)   // red-500
)

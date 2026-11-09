package com.techrifter.fluxvoice.ui.screen.voice.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrifter.fluxvoice.ui.screen.voice.VoiceState
import com.techrifter.fluxvoice.ui.theme.FluxAmber
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.FluxFuchsia
import com.techrifter.fluxvoice.ui.theme.FluxRose
import com.techrifter.fluxvoice.ui.theme.FluxSunsetOrange
import com.techrifter.fluxvoice.ui.theme.FluxViolet
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Flagship Studio Vector Visualizer & Harmonic Phase Workbench.
 *
 * Designed with Teenage Engineering & pro audio console aesthetic,
 * replacing the traditional orb with a high-precision acoustic instrument:
 *  - Dual Vector Beams:
 *    • Electric Cyan Beam: High-frequency vocal input harmonics (STT / Mic).
 *    • Solar Sunset Amber Beam: Synthesis prosody waveform (TTS / Voice Output).
 *  - Cognitive Lissajous Loop:
 *    • Radiant Fuchsia & Royal Violet phase trajectory during THINKING / reasoning.
 *  - Precision Studio Graticule:
 *    • Sub-decibel horizontal vector scale markings and zero-crossing timebase.
 *  - Corner Telemetry Deck:
 *    • Live I/O sample rate indicators, VAD status, and DSP state telemetry.
 */
@Composable
fun LivingVoiceOrb(
    voiceState: VoiceState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vector_visualizer")

    // High-speed acoustic timebase clocks
    val phaseFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (voiceState) {
                    VoiceState.SPEAKING     -> 950
                    VoiceState.LISTENING    -> 1100
                    VoiceState.THINKING     -> 1400
                    VoiceState.INTERRUPTING -> 500
                    VoiceState.IDLE         -> 3200
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseFast"
    )

    val phaseSlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseSlow"
    )

    // Lissajous 3D rotation clock (for THINKING phase-space trajectory)
    val phaseLissajous by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseLissajous"
    )

    // Vector scanline sweep
    val scanlineSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanlineSweep"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressDamp by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(100),
        label = "pressDamp"
    )

    // Active channel border glow
    val borderGlow by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE         -> HairlineBorder
            VoiceState.LISTENING    -> FluxAzure.copy(alpha = 0.40f)
            VoiceState.THINKING     -> FluxViolet.copy(alpha = 0.45f)
            VoiceState.SPEAKING     -> FluxSunsetOrange.copy(alpha = 0.42f)
            VoiceState.INTERRUPTING -> FluxRose.copy(alpha = 0.40f)
        },
        animationSpec = tween(300),
        label = "borderGlow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(162.dp)
            .scale(pressDamp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onTap() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, borderGlow)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── 1. Vector Waveform Visualizer Canvas ──────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                // A. Graticule Reticle Lines (Hardware Grid)
                val gridAlpha = 0.07f
                val gridColor = Color(0xFF94A3B8).copy(alpha = gridAlpha)

                // Horizontal reference graticules (+6dB, 0dB, -6dB)
                val dbOffsets = listOf(-height * 0.28f, -height * 0.14f, 0f, height * 0.14f, height * 0.28f)
                for (offset in dbOffsets) {
                    val y = centerY + offset
                    val isCenter = offset == 0f
                    drawLine(
                        color = if (isCenter) Color(0xFF94A3B8).copy(alpha = 0.18f) else gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = (if (isCenter) 1.0.dp else 0.8.dp).toPx()
                    )
                }

                // Vertical timebase graticules
                val divisionCount = 8
                val divStep = width / divisionCount
                for (i in 1 until divisionCount) {
                    val x = i * divStep
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // Center zero-crossing tick marks
                val tickStep = 24.dp.toPx()
                var tx = tickStep
                while (tx < width) {
                    drawLine(
                        color = Color(0xFF94A3B8).copy(alpha = 0.22f),
                        start = Offset(tx, centerY - 3.dp.toPx()),
                        end = Offset(tx, centerY + 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    tx += tickStep
                }

                // B. Vertical Scanline Effect
                val scanX = scanlineSweep * width
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startX = scanX - 16.dp.toPx(),
                        endX = scanX + 16.dp.toPx()
                    ),
                    start = Offset(scanX, 0f),
                    end = Offset(scanX, height),
                    strokeWidth = 32.dp.toPx()
                )

                // C. Vector Waveform Calculations
                val sampleCount = 140
                val isListening = voiceState == VoiceState.LISTENING
                val isSpeaking = voiceState == VoiceState.SPEAKING
                val isThinking = voiceState == VoiceState.THINKING
                val isInterrupting = voiceState == VoiceState.INTERRUPTING

                // ── Beam 1: Input Vocal Harmonics (Electric Cyan / Azure) ─────
                val inputPath = Path()
                val inputGain = when {
                    isListening    -> 34.dp.toPx()
                    isThinking     -> 12.dp.toPx()
                    isSpeaking     -> 3.dp.toPx()
                    isInterrupting -> 8.dp.toPx()
                    else           -> 5.dp.toPx() // Idle ambient mic presence
                }

                for (i in 0..sampleCount) {
                    val u = i.toFloat() / sampleCount
                    val x = u * width

                    // Speech formant envelope (natural speech harmonics peak around mid-frequencies)
                    val formant1 = sin(2 * PI.toFloat() * 2.8f * u + phaseFast) * (inputGain * 0.65f)
                    val formant2 = cos(2 * PI.toFloat() * 6.4f * u - phaseFast * 1.5f) * (inputGain * 0.25f)
                    val formant3 = sin(2 * PI.toFloat() * 1.2f * u + phaseSlow) * (inputGain * 0.15f)
                    val window = sin(u * PI.toFloat()) // zero out at screen edges

                    val y = centerY + (formant1 + formant2 + formant3) * window

                    if (i == 0) inputPath.moveTo(x, y) else inputPath.lineTo(x, y)
                }

                // Phosphor bloom pass
                drawPath(
                    path = inputPath,
                    color = FluxAzure.copy(alpha = if (isListening) 0.25f else 0.10f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Core beam pass
                drawPath(
                    path = inputPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            FluxAzure.copy(alpha = 0.40f),
                            Color(0xFF00D2FF),
                            Color.White,
                            Color(0xFF00D2FF),
                            FluxAzure.copy(alpha = 0.40f)
                        ),
                        startX = 0f,
                        endX = width
                    ),
                    style = Stroke(
                        width = (if (isListening) 2.2.dp else 1.5.dp).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // ── Beam 2: Output Speech Synthesis (Solar Sunset Amber) ─────
                val outputPath = Path()
                val outputGain = when {
                    isSpeaking     -> 36.dp.toPx()
                    isThinking     -> 12.dp.toPx()
                    isListening    -> 3.dp.toPx()
                    isInterrupting -> 6.dp.toPx()
                    else           -> 5.dp.toPx()
                }

                for (i in 0..sampleCount) {
                    val u = i.toFloat() / sampleCount
                    val x = u * width

                    // Counter-phase synthesis waveform with higher voice prosody resonance
                    val voiceHarmonic1 = sin(2 * PI.toFloat() * 2.2f * u - phaseFast * 1.1f) * (outputGain * 0.60f)
                    val voiceHarmonic2 = cos(2 * PI.toFloat() * 4.8f * u + phaseFast * 1.3f) * (outputGain * 0.30f)
                    val voiceHarmonic3 = sin(2 * PI.toFloat() * 8.0f * u) * (outputGain * 0.12f)
                    val window = sin(u * PI.toFloat())

                    val y = centerY + (voiceHarmonic1 + voiceHarmonic2 + voiceHarmonic3) * window

                    if (i == 0) outputPath.moveTo(x, y) else outputPath.lineTo(x, y)
                }

                // Phosphor bloom pass
                drawPath(
                    path = outputPath,
                    color = FluxSunsetOrange.copy(alpha = if (isSpeaking) 0.28f else 0.10f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Core beam pass
                drawPath(
                    path = outputPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            FluxSunsetOrange.copy(alpha = 0.40f),
                            Color(0xFFFFA500),
                            Color.White,
                            Color(0xFFFFA500),
                            FluxSunsetOrange.copy(alpha = 0.40f)
                        ),
                        startX = 0f,
                        endX = width
                    ),
                    style = Stroke(
                        width = (if (isSpeaking) 2.2.dp else 1.5.dp).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // ── Beam 3: Cognitive Lissajous Loop (THINKING / Reasoning State) ──
                if (isThinking) {
                    val lissajousPath = Path()
                    val lissajousPoints = 80
                    val radiusX = width * 0.22f
                    val radiusY = height * 0.28f

                    for (k in 0..lissajousPoints) {
                        val t = (k.toFloat() / lissajousPoints) * 2 * PI.toFloat()
                        val lx = width / 2f + sin(3 * t + phaseLissajous) * radiusX
                        val ly = centerY + cos(4 * t - phaseLissajous * 1.2f) * radiusY

                        if (k == 0) lissajousPath.moveTo(lx, ly) else lissajousPath.lineTo(lx, ly)
                    }
                    lissajousPath.close()

                    // Bloom pass
                    drawPath(
                        path = lissajousPath,
                        color = FluxFuchsia.copy(alpha = 0.30f),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Sharp stroke
                    drawPath(
                        path = lissajousPath,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.White,
                                FluxViolet,
                                FluxFuchsia,
                                FluxRose,
                                Color.White
                            ),
                            center = Offset(width / 2f, centerY)
                        ),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // ── 2. Studio Telemetry Overlays (Four Corners) ────────────────────
            // Top-Left: Channel 1 Input Spec (Cyan)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (voiceState == VoiceState.LISTENING) FluxAzure else Color(0xFF64748B))
                )
                Text(
                    text = "STT · 16kHz PCM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = if (voiceState == VoiceState.LISTENING) FluxAzure else TextSlateMuted
                )
            }

            // Top-Right: Channel 2 Output Spec (Amber)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "TTS · 24kHz RES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = if (voiceState == VoiceState.SPEAKING) FluxSunsetOrange else TextSlateMuted
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (voiceState == VoiceState.SPEAKING) FluxSunsetOrange else Color(0xFF64748B))
                )
            }

            // Bottom-Left: Hardware AEC / VAD Status
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "BARGE-IN AEC: ACTIVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = TextSlateDark
                )
            }

            // Bottom-Right: State Telemetry Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp),
                shape = RoundedCornerShape(4.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = when (voiceState) {
                        VoiceState.IDLE         -> "DSP: STANDBY"
                        VoiceState.LISTENING    -> "DSP: RX STREAM"
                        VoiceState.THINKING     -> "DSP: COGNITIVE"
                        VoiceState.SPEAKING     -> "DSP: TX SYNTH"
                        VoiceState.INTERRUPTING -> "DSP: BARGE-IN"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = when (voiceState) {
                        VoiceState.IDLE         -> TextSlateMuted
                        VoiceState.LISTENING    -> FluxAzure
                        VoiceState.THINKING     -> FluxViolet
                        VoiceState.SPEAKING     -> FluxSunsetOrange
                        VoiceState.INTERRUPTING -> FluxRose
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

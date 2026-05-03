package com.techrifter.fluxvoice.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.core.FluxVoiceController
import com.techrifter.fluxvoice.core.state.VoiceState

/**
 * Drop-in voice UI component — animated FluxVoice UI, live transcript, and state labels.
 *
 * Handles RECORD_AUDIO permission automatically before the first tap.
 *
 * ```kotlin
 * // 3-line voice feature — the whole screen:
 * FluxVoiceView(controller = rememberFluxVoice { ... })
 *
 * // Embedded in an existing screen with custom sizing:
 * FluxVoiceView(
 *     controller = controller,
 *     config = FluxVoiceViewConfig(size = 180.dp, showBrandName = false),
 *     modifier = Modifier.padding(bottom = 48.dp)
 * )
 * ```
 *
 * @param controller  Engine instance — create with [rememberFluxVoice].
 * @param config      Visual configuration (size, colors, optional sections).
 * @param modifier    Modifier for the root [Column] — use for positioning within a parent.
 */
@Composable
public fun FluxVoiceView(
    controller: FluxVoiceController,
    config: FluxVoiceViewConfig = FluxVoiceViewConfig(),
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.tap()
    }

    val stateColor by animateColorAsState(
        targetValue = when (state.voiceState) {
            VoiceState.IDLE         -> config.colors.idle
            VoiceState.LISTENING    -> config.colors.listening
            VoiceState.THINKING     -> config.colors.thinking
            VoiceState.SPEAKING     -> config.colors.speaking
            VoiceState.INTERRUPTING -> config.colors.interrupting
        },
        animationSpec = tween(600),
        label = "fluxVoiceStateColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Brand wordmark ────────────────────────────────────────────────────
        if (config.showBrandName) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Light)) {
                        append("Flux")
                    }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(
                                listOf(config.colors.listening, config.colors.thinking)
                            )
                        )
                    ) {
                        append("Voice")
                    }
                },
                fontSize = 28.sp,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Transcript bubbles ────────────────────────────────────────────────
        if (config.showTranscript) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = state.aiResponse.isNotEmpty(),
                    enter   = fadeIn(tween(400)) + slideInVertically { -it / 4 },
                    exit    = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
                ) {
                    AiResponseBubble(text = state.aiResponse)
                }

                val transcript = state.partialTranscript.ifEmpty { state.transcript }
                AnimatedVisibility(
                    visible = transcript.isNotEmpty(),
                    enter   = fadeIn(tween(300)) + slideInVertically { it / 4 },
                    exit    = fadeOut(tween(250))
                ) {
                    UserTranscriptBubble(
                        text      = transcript,
                        isPartial = state.partialTranscript.isNotEmpty()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── FluxVoice ─────────────────────────────────────────────────────────
        FluxVoiceTapTarget(
            voiceState = state.voiceState,
            stateColor = stateColor,
            size       = config.size,
            onTap = {
                when {
                    state.voiceState == VoiceState.IDLE &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED ->
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else -> controller.tap()
                }
            }
        )

        // ── State label ───────────────────────────────────────────────────────
        if (config.showStateLabel) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = when (state.voiceState) {
                    VoiceState.IDLE         -> "Ready"
                    VoiceState.LISTENING    -> "Listening"
                    VoiceState.THINKING     -> "Thinking…"
                    VoiceState.SPEAKING     -> "Speaking"
                    VoiceState.INTERRUPTING -> "Stopping…"
                },
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color     = if (state.voiceState == VoiceState.IDLE)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                else stateColor,
                textAlign = TextAlign.Center
            )
        }

        if (config.showHintLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (state.voiceState) {
                    VoiceState.IDLE      -> "tap to speak"
                    VoiceState.LISTENING -> "tap to stop"
                    VoiceState.SPEAKING  -> "tap to interrupt"
                    else                 -> ""
                },
                style       = MaterialTheme.typography.bodySmall,
                color       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                textAlign   = TextAlign.Center,
                letterSpacing = 0.6.sp
            )
        }

        // ── Error banner ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter   = fadeIn(tween(300)) + slideInVertically { it },
            exit    = fadeOut(tween(250)) + slideOutVertically { it }
        ) {
            state.errorMessage?.let { msg ->
                FluxVoiceErrorBanner(message = msg, onDismiss = controller::dismissError)
            }
        }
    }
}

// ── Internal composables ──────────────────────────────────────────────────────

@Composable
internal fun FluxVoiceTapTarget(
    voiceState: VoiceState,
    stateColor: Color,
    size: Dp,
    onTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(pressScale)
            .pointerInput(voiceState) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        FluxVoiceCanvas(
            voiceState = voiceState,
            stateColor = stateColor,
            modifier   = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun FluxVoiceCanvas(
    voiceState: VoiceState,
    stateColor: Color,
    modifier: Modifier = Modifier
) {
    val isActive = voiceState != VoiceState.IDLE
    val transition = rememberInfiniteTransition(label = "fluxVoice")

    val pulse1 by transition.animateFloat(
        initialValue = 0.90f,
        targetValue  = when (voiceState) {
            VoiceState.IDLE         -> 1.02f
            VoiceState.LISTENING    -> 1.18f
            VoiceState.THINKING     -> 1.10f
            VoiceState.SPEAKING     -> 1.16f
            VoiceState.INTERRUPTING -> 1.06f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.IDLE         -> 2600
                    VoiceState.LISTENING    -> 550
                    VoiceState.THINKING     -> 480
                    VoiceState.SPEAKING     -> 780
                    VoiceState.INTERRUPTING -> 380
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    val pulse2 by transition.animateFloat(
        initialValue = 0.96f,
        targetValue  = when (voiceState) {
            VoiceState.IDLE         -> 1.08f
            VoiceState.LISTENING    -> 1.28f
            VoiceState.THINKING     -> 1.18f
            VoiceState.SPEAKING     -> 1.22f
            VoiceState.INTERRUPTING -> 1.12f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.IDLE         -> 3400
                    VoiceState.LISTENING    -> 820
                    VoiceState.THINKING     -> 660
                    VoiceState.SPEAKING     -> 1100
                    VoiceState.INTERRUPTING -> 560
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    val arcRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.THINKING     -> 1600
                    VoiceState.SPEAKING     -> 2200
                    VoiceState.INTERRUPTING -> 1000
                    else                    -> 2000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotation"
    )

    val arcAlpha by animateFloatAsState(
        targetValue   = if (isActive) 0.75f else 0f,
        animationSpec = tween(500),
        label         = "arcAlpha"
    )

    Canvas(modifier = modifier) {
        val center     = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2f * 0.38f

        // Outermost ambient halo
        drawCircle(
            color  = stateColor.copy(alpha = 0.055f),
            radius = baseRadius * pulse2 * 1.90f,
            center = center
        )
        // Mid glow ring
        drawCircle(
            color  = stateColor.copy(alpha = 0.10f),
            radius = baseRadius * pulse2 * 1.46f,
            center = center
        )
        // Inner glow (active only)
        if (isActive) {
            drawCircle(
                color  = stateColor.copy(alpha = 0.20f),
                radius = baseRadius * pulse1 * 1.22f,
                center = center
            )
        }
        // Spinning arc rings
        if (arcAlpha > 0.01f) {
            val arcRadius = baseRadius * 1.38f
            rotate(degrees = arcRotation, pivot = center) {
                drawArc(
                    color      = stateColor.copy(alpha = arcAlpha * 0.80f),
                    startAngle = -90f,
                    sweepAngle = 115f,
                    useCenter  = false,
                    style      = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft    = Offset(center.x - arcRadius, center.y - arcRadius),
                    size       = Size(arcRadius * 2, arcRadius * 2)
                )
                drawArc(
                    color      = stateColor.copy(alpha = arcAlpha * 0.32f),
                    startAngle = 110f,
                    sweepAngle = 55f,
                    useCenter  = false,
                    style      = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft    = Offset(center.x - arcRadius, center.y - arcRadius),
                    size       = Size(arcRadius * 2, arcRadius * 2)
                )
            }
        }
        // Core FluxVoice sphere — radial gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    stateColor.copy(alpha = 1.0f),
                    stateColor.copy(alpha = 0.78f),
                    stateColor.copy(alpha = 0.50f)
                ),
                center = center,
                radius = baseRadius * pulse1
            ),
            radius = baseRadius * pulse1,
            center = center
        )
        // Specular highlight — glass lens effect
        val hlRadius = baseRadius * pulse1 * 0.27f
        val hlOffset = baseRadius * pulse1 * 0.30f
        val hlCenter = Offset(center.x - hlOffset, center.y - hlOffset)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isActive) 0.38f else 0.22f),
                    Color.Transparent
                ),
                center = hlCenter,
                radius = hlRadius
            ),
            radius = hlRadius,
            center = hlCenter
        )
    }
}

@Composable
internal fun AiResponseBubble(text: String) {
    Surface(
        shape         = RoundedCornerShape(20.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        modifier      = Modifier.fillMaxWidth()
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun UserTranscriptBubble(text: String, isPartial: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            color         = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier      = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text     = text,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal
                ),
                color    = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
internal fun FluxVoiceErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f))
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text     = message,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector  = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint         = MaterialTheme.colorScheme.onErrorContainer,
                modifier     = Modifier.size(16.dp)
            )
        }
    }
}

package com.techrifter.fluxvoice.ui.screen.voice

import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.VoiceMode

private val modeGradients = mapOf(
    AppMode.FAST     to listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
    AppMode.THINKING to listOf(Color(0xFF8B5CF6), Color(0xFFDB2777)),
    AppMode.CUSTOM   to listOf(Color(0xFF059669), Color(0xFF0EA5E9))
)

@Composable
fun VoiceScreen(
    onSettingsClick: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(context) {
        (context as? Activity)?.let(viewModel::attachActivity)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val errorColor     = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val stateColor by animateColorAsState(
        targetValue = when (uiState.voiceState) {
            VoiceState.IDLE         -> surfaceVariant
            VoiceState.LISTENING    -> primaryColor
            VoiceState.THINKING     -> tertiaryColor
            VoiceState.SPEAKING     -> secondaryColor
            VoiceState.INTERRUPTING -> errorColor
        },
        animationSpec = tween(600),
        label = "stateColor"
    )

    val modeColors = modeGradients[uiState.appMode]!!

    val wordmarkTransition = rememberInfiniteTransition(label = "wordmark")
    val shimmerPhase by wordmarkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "shimmerPhase"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            lerp(modeColors[0], modeColors[1], shimmerPhase),
            lerp(modeColors[1], modeColors[0], shimmerPhase)
        )
    )

    val showEmptyState = uiState.voiceState == VoiceState.IDLE &&
        uiState.userTranscript.isEmpty() && uiState.aiResponse.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF080C12) else MaterialTheme.colorScheme.surface)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.40f to stateColor.copy(alpha = 0.09f),
                        0.68f to stateColor.copy(alpha = 0.07f),
                        1.0f to Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings icon — dot badge pulses when setup is pending
                Box {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (showEmptyState) 0.90f else 0.65f
                            )
                        )
                    }
                    if (showEmptyState) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 10.dp, top = 10.dp)
                                .size(8.dp)
                                .background(primaryColor, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                ModeBadge(
                    appMode = uiState.appMode,
                    onModeSelect = viewModel::updateAppMode
                )

                Spacer(Modifier.weight(1f))

                if (uiState.userTranscript.isNotEmpty() || uiState.aiResponse.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearConversation) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // ── Brand wordmark ───────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    ) { append("Flux") }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            brush = shimmerBrush
                        )
                    ) { append("Voice") }
                },
                fontSize = 30.sp,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── Conversation / empty-state area ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                // Empty state — shown when idle with no conversation history
                Box(Modifier.align(Alignment.Center)) {
                    this@Column.AnimatedVisibility(
                        visible = showEmptyState,
                        enter = fadeIn(tween(500)),
                        exit  = fadeOut(tween(250))
                    ) {
                        EmptyStateHints(onSettingsClick = onSettingsClick)
                    }
                }

                // Live conversation — bottom-anchored, fades in as content arrives
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = uiState.aiResponse.isNotEmpty(),
                        enter = fadeIn(tween(400)) + slideInVertically { -it / 4 },
                        exit  = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
                    ) {
                        AiResponseBubble(text = uiState.aiResponse)
                    }

                    val transcript = uiState.partialTranscript.ifEmpty { uiState.userTranscript }
                    AnimatedVisibility(
                        visible = transcript.isNotEmpty(),
                        enter = fadeIn(tween(300)) + slideInVertically { it / 4 },
                        exit  = fadeOut(tween(250))
                    ) {
                        UserTranscriptBubble(
                            text = transcript,
                            isPartial = uiState.partialTranscript.isNotEmpty()
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── FluxVoice tap target — the only tap target ────────────────────
            FluxVoiceTapTarget(
                voiceState = uiState.voiceState,
                stateColor = stateColor,
                onTap = {
                    when {
                        uiState.voiceState == VoiceState.IDLE &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED ->
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> viewModel.onMicTap()
                    }
                }
            )

            // ── State label ──────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))

            Text(
                text = when (uiState.voiceState) {
                    VoiceState.IDLE         ->
                        if (uiState.voiceMode == VoiceMode.ELEVENLABS) "ElevenLabs ready"
                        else "Ready"
                    VoiceState.LISTENING    -> "Listening to you"
                    VoiceState.THINKING     -> "Thinking..."
                    VoiceState.SPEAKING     -> "Speaking"
                    VoiceState.INTERRUPTING -> "Stopping..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (uiState.voiceState == VoiceState.IDLE)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                else
                    stateColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (uiState.voiceState) {
                    VoiceState.IDLE         -> "tap to speak"
                    VoiceState.LISTENING    -> "tap to stop"
                    VoiceState.SPEAKING     -> "tap to interrupt"
                    else                    -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                textAlign = TextAlign.Center,
                letterSpacing = 0.6.sp
            )

            // ── Error banner ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(tween(300)) + slideInVertically { it },
                exit  = fadeOut(tween(250)) + slideOutVertically { it }
            ) {
                uiState.errorMessage?.let { msg ->
                    ErrorBanner(message = msg, onDismiss = viewModel::dismissError)
                }
            }

            Spacer(Modifier.height(52.dp))
        }
    }
}

// ── FluxVoice tap target ──────────────────────────────────────────────

@Composable
private fun FluxVoiceTapTarget(
    voiceState: VoiceState,
    stateColor: Color,
    onTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    Box(
        modifier = Modifier
            .size(280.dp)
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
        FluxVoice(
            voiceState = voiceState,
            stateColor = stateColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Mode badge ─────────────────────────────────────────────────────────────────

@Composable
private fun ModeBadge(appMode: AppMode, onModeSelect: (AppMode) -> Unit) {
    val colors = modeGradients[appMode]!!
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.12f) }))
                .border(1.dp, colors[0].copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(appMode.emoji, fontSize = 13.sp)
                Text(
                    appMode.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors[0],
                    fontWeight = FontWeight.SemiBold
                )
                Text("▾", fontSize = 11.sp, color = colors[0].copy(alpha = 0.7f))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppMode.entries.forEachIndexed { index, mode ->
                val modeColors = modeGradients[mode]!!
                val isActive = mode == appMode
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, fontSize = 14.sp)
                            Column {
                                Text(
                                    mode.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) modeColors[0]
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    mode.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (mode != appMode) onModeSelect(mode)
                    }
                )
                if (index < AppMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateHints(onSettingsClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Capability pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeaturePill("⚡ Instant", Modifier.weight(1f))
            FeaturePill("🎙 Natural", Modifier.weight(1f))
            FeaturePill("🧠 Smart", Modifier.weight(1f))
        }

        // Settings nudge — tappable card pointing users toward configuration
        Surface(
            onClick = onSettingsClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Configure your AI",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Choose provider, voice & model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeaturePill(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}

// ── Conversation bubbles ───────────────────────────────────────────────────────

@Composable
private fun AiResponseBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UserTranscriptBubble(text: String, isPartial: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

// ── Error banner ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f))
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── FluxVoice visual (Canvas with bloom, arcs, specular) ─────────────────────

@Composable
private fun FluxVoice(
    voiceState: VoiceState,
    stateColor: Color,
    modifier: Modifier = Modifier
) {
    val isActive = voiceState != VoiceState.IDLE
    val infiniteTransition = rememberInfiniteTransition(label = "fluxVoice")

    // Core pulse — drives the main FluxVoice body
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = when (voiceState) {
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

    // Outer halo pulse — offset timing for organic feel
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = when (voiceState) {
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

    // Spinning arc — continuously rotates in active states
    val arcRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
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

    // Arc fades in/out when transitioning between idle and active
    val arcAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.75f else 0f,
        animationSpec = tween(500),
        label = "arcAlpha"
    )

    Canvas(modifier = modifier) {
        val center     = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2f * 0.38f

        // ── Layer 1: Outermost ambient halo ───────────────────────────────────
        drawCircle(
            color  = stateColor.copy(alpha = 0.055f),
            radius = baseRadius * pulse2 * 1.90f,
            center = center
        )

        // ── Layer 2: Mid glow ring ────────────────────────────────────────────
        drawCircle(
            color  = stateColor.copy(alpha = 0.10f),
            radius = baseRadius * pulse2 * 1.46f,
            center = center
        )

        // ── Layer 3: Inner glow (active only) ────────────────────────────────
        if (isActive) {
            drawCircle(
                color  = stateColor.copy(alpha = 0.20f),
                radius = baseRadius * pulse1 * 1.22f,
                center = center
            )
        }

        // ── Spinning arc rings ────────────────────────────────────────────────
        if (arcAlpha > 0.01f) {
            val arcRadius = baseRadius * 1.38f
            rotate(degrees = arcRotation, pivot = center) {
                // Primary arc (leading)
                drawArc(
                    color      = stateColor.copy(alpha = arcAlpha * 0.80f),
                    startAngle = -90f,
                    sweepAngle = 115f,
                    useCenter  = false,
                    style      = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft    = Offset(center.x - arcRadius, center.y - arcRadius),
                    size       = Size(arcRadius * 2, arcRadius * 2)
                )
                // Counter arc (trailing, shorter and fainter)
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

        // ── Core FluxVoice sphere — radial gradient ───────────────────────────
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

        // ── Specular highlight — glass lens effect ────────────────────────────
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

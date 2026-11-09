package com.techrifter.fluxvoice.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.core.FluxVoiceController
import com.techrifter.fluxvoice.core.state.VoiceState

private val modeGradients = mapOf(
    FluxVoiceMode.FAST     to listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
    FluxVoiceMode.THINKING to listOf(Color(0xFF8B5CF6), Color(0xFFDB2777)),
    FluxVoiceMode.CUSTOM   to listOf(Color(0xFF059669), Color(0xFF0EA5E9)),
)

/**
 * Full-screen FluxVoice UI — the complete voice experience out of the box.
 *
 * Background colour comes from [MaterialTheme.colorScheme.background] — wrap in
 * [FluxVoiceTheme] (or any dark [MaterialTheme]) for the intended dark appearance.
 *
 * ```kotlin
 * var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }
 * val controller = rememberFluxVoice(mode) {
 *     systemPrompt = mode.defaultPrompt
 *     sttProvider  = DeepgramSttProvider(apiKey = "…")
 *     llmProvider  = OpenAiCompatibleLlmProvider(apiKey = "…")
 *     ttsProvider  = CartesiaTtsProvider(apiKey = "…")
 * }
 * FluxVoiceScreen(
 *     controller      = controller,
 *     initialMode     = mode,
 *     onModeChange    = { mode = it },
 *     onSettingsClick = { navController.navigate("settings") },
 * )
 * ```
 *
 * @param controller                  Engine instance from [rememberFluxVoice].
 * @param modifier                    Applied to the root [Box].
 * @param initialMode                 Starting mode badge. Default [FluxVoiceMode.FAST].
 * @param onSettingsClick             When provided, a ⚙ icon appears in the header.
 * @param onModeChange                Called when the user switches mode via the badge.
 * @param autoShowSettingsForCustomMode When `true` (default), the ⚙ icon auto-appears
 *                                    whenever [FluxVoiceMode.CUSTOM] is active, even if
 *                                    [onSettingsClick] is null — signalling that the mode
 *                                    needs configuration. Set to `false` to suppress this.
 * @param showConfigureCard           Show the "Configure your AI" card in the empty state.
 *                                    Never shown in [FluxVoiceMode.CUSTOM] regardless of
 *                                    this flag (the header settings icon handles that mode).
 */
@Composable
public fun FluxVoiceScreen(
    controller: FluxVoiceController,
    modifier: Modifier = Modifier,
    initialMode: FluxVoiceMode = FluxVoiceMode.FAST,
    onSettingsClick: (() -> Unit)? = null,
    onModeChange: ((FluxVoiceMode) -> Unit)? = null,
    autoShowSettingsForCustomMode: Boolean = true,
    showConfigureCard: Boolean = true,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var currentMode by remember { mutableStateOf(initialMode) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) controller.tap() }

    val primaryColor   = MaterialTheme.colorScheme.primary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor     = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val stateColor by animateColorAsState(
        targetValue = when (state.voiceState) {
            VoiceState.IDLE         -> surfaceVariant
            VoiceState.LISTENING    -> primaryColor
            VoiceState.THINKING     -> tertiaryColor
            VoiceState.SPEAKING     -> secondaryColor
            VoiceState.INTERRUPTING -> errorColor
        },
        animationSpec = tween(600),
        label = "stateColor"
    )

    val modeColors = modeGradients[currentMode]!!

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerPhase by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "shimmerPhase"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            lerp(modeColors[0], modeColors[1], shimmerPhase),
            lerp(modeColors[1], modeColors[0], shimmerPhase),
        )
    )

    val hasConversation = state.transcript.isNotEmpty() || state.aiResponse.isNotEmpty()
    val showEmptyState  = state.voiceState == VoiceState.IDLE && !hasConversation

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.40f to stateColor.copy(alpha = 0.09f),
                        0.68f to stateColor.copy(alpha = 0.07f),
                        1.00f to Color.Transparent,
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

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showSettings = onSettingsClick != null ||
                    (currentMode == FluxVoiceMode.CUSTOM && autoShowSettingsForCustomMode)
                if (showSettings) {
                    Box {
                        IconButton(
                            onClick = { onSettingsClick?.invoke() },
                            enabled = onSettingsClick != null
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = when {
                                    onSettingsClick == null ->
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                                    showEmptyState -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                }
                            )
                        }
                        // Dot badge: nudge developer to wire up settings in CUSTOM mode
                        val showDot = showEmptyState || (currentMode == FluxVoiceMode.CUSTOM && onSettingsClick == null)
                        if (showDot) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 10.dp, top = 10.dp)
                                    .size(8.dp)
                                    .background(
                                        if (onSettingsClick == null) errorColor else primaryColor,
                                        CircleShape
                                    )
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }

                Spacer(Modifier.weight(1f))

                FluxModeBadge(
                    mode = currentMode,
                    onModeSelect = { mode ->
                        currentMode = mode
                        onModeChange?.invoke(mode)
                    }
                )

                Spacer(Modifier.weight(1f))

                if (hasConversation) {
                    IconButton(onClick = controller::clear) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Clear conversation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // ── Shimmer wordmark ─────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )) { append("Flux") }
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        brush = shimmerBrush
                    )) { append("Voice") }
                },
                fontSize = 30.sp,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── Conversation / empty-state area ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Box(Modifier.align(Alignment.Center)) {
                    this@Column.AnimatedVisibility(
                        visible = showEmptyState,
                        enter   = fadeIn(tween(500)),
                        exit    = fadeOut(tween(250))
                    ) {
                        FluxEmptyState(
                            mode             = currentMode,
                            onSettingsClick  = onSettingsClick,
                            showConfigureCard = showConfigureCard,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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

                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Orb ──────────────────────────────────────────────────────────
            FluxVoiceTapTarget(
                voiceState = state.voiceState,
                stateColor = stateColor,
                size       = 280.dp,
                onTap      = {
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

            // ── State label ──────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Text(
                text = when (state.voiceState) {
                    VoiceState.IDLE         -> "Ready"
                    VoiceState.LISTENING    -> "Listening to you"
                    VoiceState.THINKING     -> "Thinking…"
                    VoiceState.SPEAKING     -> "Speaking"
                    VoiceState.INTERRUPTING -> "Stopping…"
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color      = if (state.voiceState == VoiceState.IDLE)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                else stateColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = when (state.voiceState) {
                    VoiceState.IDLE      -> "tap to speak"
                    VoiceState.LISTENING -> "tap to stop"
                    VoiceState.SPEAKING  -> "tap to interrupt"
                    else                 -> ""
                },
                style         = MaterialTheme.typography.bodySmall,
                color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                textAlign     = TextAlign.Center,
                letterSpacing = 0.6.sp
            )

            // ── Error banner ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter   = fadeIn(tween(300)) + slideInVertically { it },
                exit    = fadeOut(tween(250)) + slideOutVertically { it }
            ) {
                state.errorMessage?.let { msg ->
                    FluxVoiceErrorBanner(message = msg, onDismiss = controller::dismissError)
                }
            }

            Spacer(Modifier.height(52.dp))
        }
    }
}

// ── Mode badge ────────────────────────────────────────────────────────────────

@Composable
private fun FluxModeBadge(
    mode: FluxVoiceMode,
    onModeSelect: (FluxVoiceMode) -> Unit,
) {
    val colors = modeGradients[mode]!!
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
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(mode.emoji, fontSize = 13.sp)
                Text(
                    text       = mode.displayName,
                    style      = MaterialTheme.typography.labelMedium,
                    color      = colors[0],
                    fontWeight = FontWeight.SemiBold
                )
                Text("▾", fontSize = 11.sp, color = colors[0].copy(alpha = 0.7f))
            }
        }

        DropdownMenu(
            expanded          = expanded,
            onDismissRequest  = { expanded = false }
        ) {
            FluxVoiceMode.entries.forEachIndexed { index, m ->
                val mColors  = modeGradients[m]!!
                val isActive = m == mode
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(m.emoji, fontSize = 14.sp)
                            Column {
                                Text(
                                    text       = m.displayName,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (isActive) mColors[0]
                                                 else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text  = m.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (m != mode) onModeSelect(m)
                    }
                )
                if (index < FluxVoiceMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun FluxEmptyState(
    mode: FluxVoiceMode,
    onSettingsClick: (() -> Unit)?,
    showConfigureCard: Boolean,
) {
    // Card is suppressed in CUSTOM mode — the header settings icon already signals
    // that this mode needs configuration, so the card would be redundant.
    val cardVisible = showConfigureCard
        && onSettingsClick != null
        && mode != FluxVoiceMode.CUSTOM

    Column(
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FluxFeaturePill("⚡ Instant", Modifier.weight(1f))
            FluxFeaturePill("🎙 Natural", Modifier.weight(1f))
            FluxFeaturePill("🧠 Smart",   Modifier.weight(1f))
        }

        if (cardVisible) {
            Surface(
                onClick  = onSettingsClick,
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border   = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Configure your AI",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Choose provider, voice & model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        text  = "›",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FluxFeaturePill(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}

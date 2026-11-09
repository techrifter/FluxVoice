package com.techrifter.fluxvoice.ui.screen.voice

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.domain.model.MessageRole
import com.techrifter.fluxvoice.domain.model.VoiceScenario
import com.techrifter.fluxvoice.domain.pipeline.PipelineReadiness
import com.techrifter.fluxvoice.ui.screen.voice.components.BenchmarkHud
import com.techrifter.fluxvoice.ui.screen.voice.components.LivingVoiceOrb
import com.techrifter.fluxvoice.ui.screen.voice.components.PipelineSelectorSheet
import com.techrifter.fluxvoice.ui.screen.voice.components.QuickGuideSheet
import com.techrifter.fluxvoice.ui.theme.FluxAmber
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.FluxFullSpectrum
import com.techrifter.fluxvoice.ui.theme.FluxGradient
import com.techrifter.fluxvoice.ui.theme.FluxRose
import com.techrifter.fluxvoice.ui.theme.FluxSunsetOrange
import com.techrifter.fluxvoice.ui.theme.FluxViolet
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.HairlineFlame
import com.techrifter.fluxvoice.ui.theme.HairlineHighlight
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianFloat
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.ObsidianVoid
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onSettingsClick: () -> Unit,
    onStudioClick: (pipelineId: String?, isNew: Boolean) -> Unit = { _, _ -> },
    onKeyVaultClick: () -> Unit = {},
    onBenchmarksClick: () -> Unit = {},
    onCatalogClick: () -> Unit = {},
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPipelines by viewModel.allPipelines.collectAsStateWithLifecycle()
    var showPipelineSheet by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("flux_voice_ui_prefs", Context.MODE_PRIVATE) }
    var isQuickStartDismissed by remember {
        mutableStateOf(prefs.getBoolean("quick_start_dismissed", false))
    }

    LaunchedEffect(context) {
        (context as? Activity)?.let(viewModel::attachActivity)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshReadiness()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    val isMissingCreds = uiState.pipelineReadiness is PipelineReadiness.MissingCredentials
    val hasConversation = uiState.messages.isNotEmpty() ||
            uiState.partialTranscript.isNotEmpty() ||
            uiState.userTranscript.isNotEmpty() ||
            uiState.aiResponse.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. Top Brandmark & Studio Action Header ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Official FluxVoice Wordmark with Fuchsia-Rose-Orange Gradient
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Flux",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPureWhite,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Voice",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(brush = FluxGradient),
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Text(
                        text = "REAL-TIME VOICE AI PLAYGROUND",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        color = TextSlateMuted
                    )
                    if (uiState.anonymousUsername.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianCard)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.anonymousUsername,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluxCyan
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Studio Actions Deck
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { showGuideSheet = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "User Guide", tint = FluxCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onStudioClick(uiState.activePipeline?.id, false) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Studio", tint = TextSlateMedium, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onBenchmarksClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Benchmarks", tint = TextSlateMedium, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSlateMedium, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── 2. Active Pipeline Architecture (STT → LLM → TTS) ────────────
            val active = uiState.activePipeline
            val sttName = when (active?.sttProviderId) {
                "deepgram" -> "Nova-3"
                "groq_whisper" -> "Whisper Turbo"
                "whisper" -> "Whisper"
                "custom" -> "BYOK STT"
                else -> active?.sttProviderId?.uppercase() ?: "NOVA-3"
            }
            val llmName = active?.llmModelId?.substringAfterLast('/')?.take(20)?.uppercase() ?: "GPT-OSS-120B"
            val ttsName = when (active?.ttsProviderId) {
                "cartesia" -> "Sonic 3.6"
                "elevenlabs" -> "ElevenLabs"
                "openai" -> "OpenAI"
                "deepgram_aura" -> "Aura"
                "android" -> "Android TTS"
                "custom" -> "BYOK TTS"
                else -> active?.ttsProviderId?.uppercase() ?: "CARTESIA"
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StackStageChip(label = "STT", value = sttName, color = FluxAzure)
                    Text("→", fontSize = 12.sp, color = TextSlateDark)
                    StackStageChip(label = "LLM", value = llmName, color = FluxEmerald)
                    Text("→", fontSize = 12.sp, color = TextSlateDark)
                    StackStageChip(label = "TTS", value = ttsName, color = FluxViolet)
                }
            }

            // ── 3. Alert / Transient Error Banner ────────────────────────────
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                uiState.errorMessage?.let { msg ->
                    val isSettingsError = msg.contains("Settings", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clickable(enabled = isSettingsError) { onSettingsClick() },
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, HairlineFlame)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSlateMedium),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSettingsError) {
                                Text(
                                    text = "Settings ↗",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluxAmber
                                )
                            }
                            IconButton(onClick = viewModel::dismissError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextSlateMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── 4. Center Stage: Flux Spectral Core & Equalizer Wings ─────────
            Spacer(Modifier.weight(0.3f))

            LivingVoiceOrb(
                voiceState = uiState.voiceState,
                onTap = {
                    when {
                        uiState.voiceState == VoiceState.IDLE &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> viewModel.onMicTap()
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            if (isMissingCreds && !isQuickStartDismissed && uiState.voiceState == VoiceState.IDLE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineFlame),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(FluxAmber, CircleShape)
                                )
                                Text(
                                    text = "QUICK START • READY IN 2 CLICKS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FluxAmber,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    isQuickStartDismissed = true
                                    prefs.edit().putBoolean("quick_start_dismissed", true).apply()
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss Quick Start",
                                    tint = TextSlateMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Text(
                            text = "To start speaking, add your free key (Google Gemini or Groq take 30s) or explore with offline system voice.",
                            fontSize = 12.sp,
                            color = TextSlateMedium,
                            lineHeight = 16.5.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onKeyVaultClick,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FluxAzure),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = ObsidianVoid)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Free Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ObsidianVoid)
                            }
                            OutlinedButton(
                                onClick = { showGuideSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, HairlineBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluxCyan)
                                Spacer(Modifier.width(6.dp))
                                Text("User Guide", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPureWhite)
                            }
                        }
                    }
                }
            } else {
                // Pure, Responsive Voice State Typography
                Text(
                    text = when (uiState.voiceState) {
                        VoiceState.IDLE         -> "Ready to talk • Tap to begin"
                        VoiceState.LISTENING    -> "Listening to your voice…"
                        VoiceState.THINKING     -> "Reasoning & generating stream…"
                        VoiceState.SPEAKING     -> "Speaking • Tap to interrupt"
                        VoiceState.INTERRUPTING -> "Stopping audio instantaneously…"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (uiState.voiceState) {
                        VoiceState.IDLE         -> TextSlateMedium
                        VoiceState.LISTENING    -> FluxAzure
                        VoiceState.THINKING     -> FluxViolet
                        VoiceState.SPEAKING     -> TextPureWhite
                        VoiceState.INTERRUPTING -> FluxRose
                    },
                    letterSpacing = 0.2.sp
                )

                Spacer(Modifier.height(6.dp))

                // Telemetry Micro-Pill
                BenchmarkHud(
                    metrics = uiState.lastMetrics,
                    isExpanded = uiState.isBenchmarkHudExpanded,
                    onToggleExpand = viewModel::toggleBenchmarkHud
                )
            }

            Spacer(Modifier.weight(0.2f))

            // ── 5. Playground Experiments OR Live Dialogue Deck ──────────────
            if (hasConversation) {
                // Conversation Stream View
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .weight(1.2f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    val scrollState = rememberScrollState()

                    // Automatically scroll to bottom as new messages arrive or live tokens stream in
                    LaunchedEffect(
                        uiState.messages.size,
                        uiState.aiResponse.length,
                        uiState.partialTranscript.length
                    ) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CONVERSATION STREAM",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.0.sp,
                                    color = TextSlateMuted
                                )
                                if (uiState.messages.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianCard,
                                        border = BorderStroke(0.5.dp, HairlineHighlight)
                                    ) {
                                        Text(
                                            text = "${uiState.messages.size} msgs",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FluxCyan,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = viewModel::clearConversation,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear Conversation",
                                    tint = TextSlateMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. All historical turns in chronological order
                            uiState.messages.forEach { message ->
                                when (message.role) {
                                    MessageRole.USER -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ObsidianCard,
                                            border = BorderStroke(1.dp, HairlineBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "YOU",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FluxAzure
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = message.content,
                                                    fontSize = 13.5.sp,
                                                    color = TextPureWhite,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                    MessageRole.ASSISTANT -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ObsidianCard,
                                            border = BorderStroke(1.dp, HairlineHighlight),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "FLUX AI",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = FluxCyan
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                            val clip = ClipData.newPlainText("AI Response", message.content)
                                                            clipboard?.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TextSlateMuted, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = message.content,
                                                    fontSize = 13.5.sp,
                                                    color = TextPureWhite,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                    MessageRole.SYSTEM -> {}
                                }
                            }

                            // 2. In-progress Live User Transcription (only while actively listening or thinking)
                            val isUserTranscriptAlreadyInMessages = uiState.userTranscript.isNotBlank() && uiState.messages.any {
                                it.role == MessageRole.USER && it.content.trim().equals(uiState.userTranscript.trim(), ignoreCase = true)
                            }

                            val liveUserText = when {
                                uiState.voiceState == VoiceState.IDLE -> ""
                                uiState.partialTranscript.isNotBlank() -> uiState.partialTranscript
                                uiState.userTranscript.isNotBlank() && !isUserTranscriptAlreadyInMessages -> uiState.userTranscript
                                else -> ""
                            }

                            if (liveUserText.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, HairlineHighlight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (uiState.voiceState == VoiceState.LISTENING) "YOU (LISTENING…)" else "YOU",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FluxAzure
                                            )
                                            if (uiState.voiceState == VoiceState.LISTENING) {
                                                Box(Modifier.size(5.dp).background(FluxAzure, CircleShape))
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = liveUserText,
                                            fontSize = 13.5.sp,
                                            color = TextPureWhite,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }

                            // 3. In-progress Live AI Streaming (while generating / speaking)
                            val isAiResponseAlreadyInMessages = uiState.aiResponse.isNotBlank() && uiState.messages.any {
                                it.role == MessageRole.ASSISTANT && it.content.trim().equals(uiState.aiResponse.trim(), ignoreCase = true)
                            }

                            if (uiState.aiResponse.isNotBlank() && !isAiResponseAlreadyInMessages) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, HairlineHighlight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "FLUX AI",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FluxCyan
                                                )
                                                if (uiState.voiceState == VoiceState.SPEAKING || uiState.voiceState == VoiceState.THINKING) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Box(Modifier.size(4.dp).background(FluxViolet, CircleShape))
                                                        Box(Modifier.size(4.dp).background(FluxRose, CircleShape))
                                                        Box(Modifier.size(4.dp).background(FluxAzure, CircleShape))
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    val clip = ClipData.newPlainText("AI Response", uiState.aiResponse)
                                                    clipboard?.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TextSlateMuted, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = uiState.aiResponse,
                                            fontSize = 13.5.sp,
                                            color = TextPureWhite,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Interactive Dynamic Scenario Starters
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Brush.radialGradient(listOf(FluxAzure, FluxViolet)), CircleShape)
                            )
                            Text(
                                text = "TRENDING & ADAPTIVE SCENARIOS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp,
                                color = TextSlateMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (uiState.isShufflingScenarios) "Generating…" else "Shuffle",
                                fontSize = 10.sp,
                                color = if (uiState.isShufflingScenarios) FluxCyan else TextSlateDark
                            )
                            IconButton(
                                onClick = viewModel::shuffleScenarios,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Shuffle Trending Scenarios",
                                    tint = if (uiState.isShufflingScenarios) FluxCyan else TextSlateMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.scenarios.forEach { scenario ->
                            ExperimentCard(
                                scenario = scenario,
                                onClick = { viewModel.triggerPrompt(scenario.prompt) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 6. Ergonomic Playground Bottom Deck ──────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                shape = RoundedCornerShape(28.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Active Pipeline Runtime Drawer Trigger
                    val pipelineName = uiState.activePipeline?.name ?: "Ultra-Fast Cloud"
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showPipelineSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pipelineName.take(16).uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp,
                                color = TextPureWhite
                            )
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Switch Pipeline",
                                tint = TextSlateMedium,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Center: Floating Radiant Multi-Gradient Voice Trigger
                    val isListening = uiState.voiceState == VoiceState.LISTENING
                    val isSpeaking = uiState.voiceState == VoiceState.SPEAKING
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening || isSpeaking) FluxFullSpectrum else Brush.linearGradient(listOf(ObsidianCard, ObsidianFloat))
                            )
                            .padding(2.5.dp)
                            .clip(CircleShape)
                            .background(if (isListening) FluxAzure else if (isSpeaking) ObsidianCard else ObsidianFloat)
                            .clickable {
                                when {
                                    uiState.voiceState == VoiceState.IDLE &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    else -> viewModel.onMicTap()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (uiState.voiceState) {
                                VoiceState.SPEAKING     -> Icons.Rounded.Stop
                                else                    -> Icons.Rounded.Mic
                            },
                            contentDescription = "Voice Control",
                            tint = if (isListening) ObsidianVoid else if (isSpeaking) FluxRose else TextPureWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Right: Hardware Vault Keystore Capsule
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onKeyVaultClick() },
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, if (isMissingCreds) HairlineFlame else HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isMissingCreds) FluxAmber else FluxEmerald, CircleShape)
                            )
                            Text(
                                text = if (isMissingCreds) "SETUP" else "VAULT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                color = if (isMissingCreds) FluxAmber else TextSlateMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // Pipeline Switcher Modal Sheet
        if (showPipelineSheet) {
            PipelineSelectorSheet(
                pipelines = allPipelines,
                selectedPipelineId = uiState.activePipeline?.id ?: "preset_ultra_fast",
                onSelectPipeline = viewModel::selectPipeline,
                onAddNewPipeline = { onStudioClick(null, true) },
                onOpenStudio = { onStudioClick(uiState.activePipeline?.id, false) },
                onEditPipeline = { pipeline -> onStudioClick(pipeline.id, false) },
                onDeletePipeline = viewModel::deletePipeline,
                onDismiss = {
                    showPipelineSheet = false
                    viewModel.refreshReadiness()
                }
            )
        }

        // Quick Guide & Cheat Sheet Modal
        if (showGuideSheet) {
            QuickGuideSheet(
                onDismiss = { showGuideSheet = false },
                onGotIt = {
                    showGuideSheet = false
                    isQuickStartDismissed = true
                    prefs.edit().putBoolean("quick_start_dismissed", true).apply()
                },
                onOpenKeyVault = onKeyVaultClick,
                onOpenStudio = { onStudioClick(uiState.activePipeline?.id, false) },
                onOpenCatalog = onCatalogClick,
                allPipelines = allPipelines,
                selectedPipelineId = uiState.activePipeline?.id ?: "preset_ultra_fast",
                onSelectPipeline = { pipeline ->
                    viewModel.selectPipeline(pipeline)
                    viewModel.refreshReadiness()
                }
            )
        }
    }
}

@Composable
private fun StackStageChip(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPureWhite
        )
    }
}

@Composable
private fun ExperimentCard(
    scenario: VoiceScenario,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(90),
        label = "experimentCardScale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isPressed) ObsidianFloat else ObsidianCard,
        border = BorderStroke(1.dp, if (isPressed) HairlineHighlight else HairlineBorder),
        modifier = Modifier
            .width(170.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(scenario.icon, fontSize = 13.sp)
                Text(
                    text = scenario.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = TextPureWhite,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = scenario.subtitle,
                fontSize = 9.5.sp,
                color = TextSlateMuted,
                maxLines = 1
            )
        }
    }
}

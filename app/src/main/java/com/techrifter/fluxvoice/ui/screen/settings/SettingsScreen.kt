package com.techrifter.fluxvoice.ui.screen.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.data.remote.provider.openrouter.OpenRouterModel
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.TtsProvider
import com.techrifter.fluxvoice.domain.model.VoiceMode
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.FluxSunsetOrange
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.HairlineHighlight
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianFloat
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.ObsidianVoid
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onKeyVaultClick: () -> Unit = {},
    onBenchmarksClick: () -> Unit = {},
    onCatalogClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val currentModels by viewModel.currentProviderModels.collectAsStateWithLifecycle()
    var showIdentityDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSavedMessage()
        }
    }

    Scaffold(
        containerColor = ObsidianVoid,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextPureWhite
                    )
                },
                actions = {
                    if (settings.anonymousUsername.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, HairlineHighlight),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showIdentityDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = "Edit Username",
                                    tint = FluxCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = settings.anonymousUsername,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPureWhite
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianVoid
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Key Vault Banner (BYOK) ───────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onKeyVaultClick() },
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = TextPureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Key Vault (BYOK)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPureWhite
                        )
                        Text(
                            "Hardware Keystore encrypted keys for Groq, OpenRouter, Deepgram, Cartesia",
                            fontSize = 12.sp,
                            color = TextSlateMedium
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Open",
                        tint = TextSlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Model Catalog & Benchmarks Navigation Tiles ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCatalogClick() },
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, tint = TextPureWhite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Model Catalog", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPureWhite)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBenchmarksClick() },
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = TextPureWhite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Benchmarks", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPureWhite)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Mode selector ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Choose your experience",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppMode.entries.forEach { mode ->
                        ModeCard(
                            mode = mode,
                            isSelected = mode == settings.appMode,
                            onClick = { viewModel.updateAppMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Mode-specific content with fade transition ─────────────────────
            AnimatedContent(
                targetState = settings.appMode,
                transitionSpec = {
                    fadeIn(tween(350)) togetherWith fadeOut(tween(180))
                },
                label = "mode_content"
            ) { mode ->
                when (mode) {
                    AppMode.FAST     -> FastModeContent(settings, viewModel, onBack)
                    AppMode.THINKING -> ThinkingModeContent(settings, viewModel, onBack)
                    AppMode.CUSTOM   -> CustomModeContent(settings, viewModel, currentModels, onCatalogClick, onBack)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showIdentityDialog) {
        ChangeIdentityDialog(
            currentUsername = settings.anonymousUsername,
            onDismiss = { showIdentityDialog = false },
            onConfirm = { newUsername ->
                viewModel.updateUsername(newUsername)
            },
            onRollRandom = { viewModel.getRandomUsername() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeCard(
    mode: AppMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modeAccent = when (mode) {
        AppMode.FAST     -> FluxSunsetOrange
        AppMode.THINKING -> FluxAzure
        AppMode.CUSTOM   -> FluxEmerald
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ObsidianSurface else ObsidianCard,
        border = BorderStroke(1.dp, if (isSelected) modeAccent.copy(alpha = 0.55f) else HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = mode.displayName.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = if (isSelected) modeAccent else TextSlateMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = mode.tagline,
                fontSize = 11.sp,
                color = TextSlateMuted,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ⚡ Fast mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FastModeContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Zero setup. Plug in your keys and start talking: Groq's ultra-fast inference + Deepgram deliver the lowest latency possible.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PresetInfoCard(
            items = listOf(
                PresetItem("Groq · GPT OSS 120B", "AI Model"),
                PresetItem("Deepgram Streaming", "Real-time STT"),
                PresetItem("6-turn memory", "Context"),
                PresetItem("Hardware AEC Barge-in", "Smart UX")
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Required to start")

            ApiKeyField(
                label = "Groq API Key",
                value = settings.groqApiKey,
                onValueChange = viewModel::updateGroqApiKey,
                placeholder = "gsk_…",
                isSet = settings.groqApiKey.isNotBlank(),
                helperText = "Free at console.groq.com → Create API key"
            )

            ApiKeyField(
                label = "Deepgram API Key",
                value = settings.deepgramApiKey,
                onValueChange = viewModel::updateDeepgramApiKey,
                placeholder = "Token …",
                isSet = settings.deepgramApiKey.isNotBlank(),
                helperText = "Free at console.deepgram.com → Create API key"
            )
        }

        ReadinessIndicator(
            isReady = settings.groqApiKey.isNotBlank() && settings.deepgramApiKey.isNotBlank()
        )

        CartesiaVoiceSection(settings, viewModel)

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { viewModel.applyFastPresetAndActivate(onSuccess = onBack) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FluxSunsetOrange,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save & Run Fast Mode", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🧠 Thinking mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThinkingModeContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Built for depth. Gemini 2.5 Flash delivers thoughtful, nuanced responses with high speed and generous 1M token free-tier quota.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PresetInfoCard(
            items = listOf(
                PresetItem("Google Gemini 2.5 Flash", "AI Model"),
                PresetItem("Deepgram Streaming", "Real-time STT"),
                PresetItem("20-turn memory", "Context"),
                PresetItem("Temperature 0.3", "Precision")
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Required to start")

            ApiKeyField(
                label = "Gemini API Key",
                value = settings.geminiApiKey,
                onValueChange = viewModel::updateGeminiApiKey,
                placeholder = "AIza…",
                isSet = settings.geminiApiKey.isNotBlank(),
                helperText = "Free at aistudio.google.com → Get API key"
            )

            ApiKeyField(
                label = "Deepgram API Key",
                value = settings.deepgramApiKey,
                onValueChange = viewModel::updateDeepgramApiKey,
                placeholder = "Token …",
                isSet = settings.deepgramApiKey.isNotBlank(),
                helperText = "Free at console.deepgram.com → Create API key"
            )
        }

        ReadinessIndicator(
            isReady = settings.geminiApiKey.isNotBlank() && settings.deepgramApiKey.isNotBlank()
        )

        CartesiaVoiceSection(settings, viewModel)

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { viewModel.applyThinkingPresetAndActivate(onSuccess = onBack) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FluxAzure,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save & Run Thinking Mode", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🔌 Custom mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun customFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = FluxEmerald.copy(alpha = 0.16f),
    selectedLabelColor = FluxEmerald,
    containerColor = ObsidianCard,
    labelColor = TextSlateMedium
)

private fun customFilterChipBorder(selected: Boolean) = BorderStroke(
    1.dp,
    if (selected) FluxEmerald.copy(alpha = 0.70f) else HairlineBorder
)

@Composable
private fun customSliderColors() = SliderDefaults.colors(
    thumbColor = FluxEmerald,
    activeTrackColor = FluxEmerald,
    inactiveTrackColor = ObsidianFloat
)

@Composable
private fun customSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = FluxEmerald,
    checkedTrackColor = FluxEmerald.copy(alpha = 0.35f),
    checkedBorderColor = FluxEmerald
)

@Composable
private fun CustomModeContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    currentModels: List<OpenRouterModel>,
    onCatalogClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Full control over every parameter. Mix and match providers, tune models, and wire up any pipeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Voice Pipeline ────────────────────────────────────────────────────
        SettingsSection("Voice Pipeline") {
            VoiceModeDropdown(
                selectedMode = settings.voiceMode,
                onModeSelected = { viewModel.updateVoiceMode(it.name) }
            )
            if (settings.voiceMode == VoiceMode.ELEVENLABS) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = FluxCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "All-in-One Voice Agent",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPureWhite
                            )
                        }
                        Text(
                            "ElevenLabs handles STT, LLM, and TTS end-to-end. Individual provider settings are bypassed.",
                            fontSize = 12.sp,
                            color = TextSlateMedium,
                            lineHeight = 17.sp
                        )
                    }
                }

                val uriHandler = LocalUriHandler.current
                ApiKeyField(
                    label = "ElevenLabs Agent ID",
                    value = settings.elevenLabsAgentId,
                    onValueChange = viewModel::updateElevenLabsAgentId,
                    placeholder = "your_agent_id_here",
                    isSet = settings.elevenLabsAgentId.isNotBlank(),
                    isPassword = false,
                    helperText = "Get or create your Agent at: "
                )
                Text(
                    text = "https://elevenlabs.io/app/conversational-ai ↗",
                    style = MaterialTheme.typography.labelSmall,
                    color = FluxCyan,
                    modifier = Modifier
                        .clickable {
                            uriHandler.openUri("https://elevenlabs.io/app/conversational-ai")
                        }
                        .padding(start = 4.dp, top = 2.dp)
                )

                val elevenLabsKey = settings.elevenLabsApiKey.ifBlank { viewModel.getElevenLabsApiKey() }
                ApiKeyField(
                    label = "ElevenLabs API Key (Optional)",
                    value = elevenLabsKey,
                    onValueChange = viewModel::updateElevenLabsApiKey,
                    placeholder = "sk_… (only required if Agent is Private)",
                    isSet = elevenLabsKey.isNotBlank(),
                    helperText = "Only needed if your ElevenLabs Agent security is set to Private"
                )
            }
            if (settings.voiceMode == VoiceMode.STANDARD) {
                SttProviderDropdown(
                    selectedProvider = settings.sttProvider,
                    onProviderSelected = { viewModel.updateSttProvider(it.name) }
                )
                when (settings.sttProvider) {
                    SttProvider.DEEPGRAM -> {
                        ApiKeyField(
                            label = "Deepgram API Key",
                            value = settings.deepgramApiKey,
                            onValueChange = viewModel::updateDeepgramApiKey,
                            placeholder = "Token …",
                            isSet = settings.deepgramApiKey.isNotBlank(),
                            helperText = "Powers Nova-3 streaming WebSocket (<150ms). Get at console.deepgram.com"
                        )
                    }
                    SttProvider.GROQ_WHISPER -> {
                        ApiKeyField(
                            label = "Groq API Key (Whisper Turbo)",
                            value = settings.groqApiKey,
                            onValueChange = viewModel::updateGroqApiKey,
                            placeholder = "gsk_…",
                            isSet = settings.groqApiKey.isNotBlank(),
                            helperText = "Powers Whisper Large v3 Turbo on Groq LPUs (<300ms). Get at console.groq.com. Completely separate from OpenAI."
                        )
                    }
                    SttProvider.WHISPER -> {
                        ApiKeyField(
                            label = "OpenAI API Key (Whisper-1)",
                            value = settings.openAiApiKey,
                            onValueChange = viewModel::updateOpenAiApiKey,
                            placeholder = "sk-…",
                            isSet = settings.openAiApiKey.isNotBlank(),
                            helperText = "Powers official OpenAI Whisper-1 cloud transcription. Get at platform.openai.com."
                        )
                    }
                    SttProvider.CUSTOM -> {
                        ApiKeyField(
                            label = "Custom STT Endpoint URL",
                            value = settings.localLlmUrl,
                            onValueChange = viewModel::updateLocalLlmUrl,
                            placeholder = "https://api.together.xyz/v1/audio/transcriptions",
                            isSet = settings.localLlmUrl.isNotBlank(),
                            helperText = "OpenAI-compatible transcription endpoint. Configure full parameters in Pipeline Studio."
                        )
                    }
                }
            }
        }

        if (settings.voiceMode == VoiceMode.STANDARD) {
            // ── AI Provider ───────────────────────────────────────────────────────
            SettingsSection("AI Provider") {
            ProviderDropdown(
                selectedProvider = settings.providerType,
                onProviderSelected = { viewModel.updateProvider(it.name) }
            )
        }

        // ── Credentials ───────────────────────────────────────────────────────
        SettingsSection("Credentials") {
            when (settings.providerType) {
                ProviderType.GEMINI -> ApiKeyField(
                    label = "Gemini API Key",
                    value = settings.geminiApiKey,
                    onValueChange = viewModel::updateGeminiApiKey,
                    placeholder = "AIza…",
                    isSet = settings.geminiApiKey.isNotBlank()
                )
                ProviderType.GROQ -> ApiKeyField(
                    label = "Groq API Key",
                    value = settings.groqApiKey,
                    onValueChange = viewModel::updateGroqApiKey,
                    placeholder = "gsk_…",
                    isSet = settings.groqApiKey.isNotBlank()
                )
                ProviderType.OPENAI_COMPAT -> {
                    ApiKeyField(
                        label = "OpenAI API Key",
                        value = settings.openAiApiKey,
                        onValueChange = viewModel::updateOpenAiApiKey,
                        placeholder = "sk-…",
                        isSet = settings.openAiApiKey.isNotBlank()
                    )
                    SettingsTextField(
                        label = "Base URL (optional)",
                        value = settings.localLlmUrl,
                        onValueChange = viewModel::updateLocalLlmUrl,
                        placeholder = "https://api.openai.com"
                    )
                }
                ProviderType.LOCAL -> Column {
                    SettingsTextField(
                        label = "Ollama URL",
                        value = settings.localLlmUrl,
                        onValueChange = viewModel::updateLocalLlmUrl,
                        placeholder = "http://192.168.1.X:11434"
                    )
                    Text(
                        text = "To connect to an Ollama server running on your computer, both your phone and computer must be on the same Wi-Fi network. Enter your computer's local IP address (e.g., 192.168.1.100).",
                        color = TextSlateMedium,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                    )
                }
                ProviderType.OPENROUTER -> {
                    ApiKeyField(
                        label = "OpenRouter API Key",
                        value = settings.openRouterApiKey.ifBlank { viewModel.getOpenRouterApiKey() },
                        onValueChange = viewModel::updateOpenRouterApiKey,
                        placeholder = "sk-or-v1-…",
                        isSet = settings.openRouterApiKey.isNotBlank() || viewModel.getOpenRouterApiKey().isNotBlank(),
                        helperText = "Get your universal gateway key at openrouter.ai/keys"
                    )
                }
            }
        }

        // ── Model ─────────────────────────────────────────────────────────────
        SettingsSection("Model") {
            val dynamicQuickPicks = remember(currentModels) {
                val priority = currentModels.filter { it.isPopular || it.isFreeTier }
                val list = if (priority.isNotEmpty()) priority else currentModels
                list.take(8).map { model ->
                    val shortName = when {
                        model.name.length > 22 -> model.name.take(20) + "…"
                        else -> model.name
                    }
                    Triple(shortName, model.id, model.isFreeTier)
                }
            }

            if (dynamicQuickPicks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Popular Models (${currentModels.size} available)",
                        style = MaterialTheme.typography.labelSmall,
                        color = FluxEmerald
                    )
                    IconButton(
                        onClick = { viewModel.refreshCurrentProviderModels() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh models",
                            tint = FluxEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((name, modelId, isFree) in dynamicQuickPicks) {
                        val isSelected = settings.modelId.equals(modelId, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateModelId(modelId) },
                            colors = customFilterChipColors(),
                            border = customFilterChipBorder(isSelected),
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isFree) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(FluxEmerald, CircleShape)
                                        )
                                    }
                                    Text(name, fontSize = 11.sp)
                                }
                            }
                        )
                    }
                }
            }

            SettingsTextField(
                label = "Model ID",
                value = settings.modelId,
                onValueChange = viewModel::updateModelId,
                placeholder = settings.providerType.defaultModel
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (settings.providerType == ProviderType.OPENROUTER) {
                        "Tap a live model or enter custom slug"
                    } else {
                        "Tap a live model or enter custom ID"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Catalog ↗",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FluxEmerald,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCatalogClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // ── Generation ────────────────────────────────────────────────────────
        SettingsSection("Generation") {
            Text(
                "Temperature: ${"%.2f".format(settings.temperature)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = settings.temperature,
                onValueChange = { viewModel.updateTemperature((it * 100).roundToInt() / 100f) },
                valueRange = 0f..1f,
                steps = 9,
                colors = customSliderColors()
            )
            Text(
                "Higher = more creative. Keep below 0.5 for factual replies, 0.7 for natural conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsTextField(
                label = "System Prompt",
                value = settings.systemPrompt,
                onValueChange = viewModel::updateSystemPrompt,
                placeholder = "You are a helpful voice assistant…",
                minLines = 3,
                maxLines = 6
            )
        }

        // ── Voice Conversation ────────────────────────────────────────────────
        SettingsSection("Voice Conversation") {
            Text(
                "Context window: ${settings.maxContextTurns} turns",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = settings.maxContextTurns.toFloat(),
                onValueChange = { viewModel.updateMaxContextTurns(it.roundToInt()) },
                valueRange = 1f..20f,
                steps = 18,
                colors = customSliderColors()
            )
        }

        // ── Voice Output (TTS) ────────────────────────────────────────────────
        SettingsSection("Voice Output (TTS)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable text-to-speech", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Multi-engine voice playback (Cartesia, ElevenLabs, OpenAI, Deepgram, Android)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.ttsEnabled,
                    onCheckedChange = viewModel::updateTtsEnabled,
                    colors = customSwitchColors()
                )
            }

            if (settings.ttsEnabled) {
                TtsProviderDropdown(
                    selectedProvider = settings.ttsProvider,
                    onProviderSelected = { viewModel.updateTtsProvider(it.name) }
                )

                when (settings.ttsProvider) {
                    TtsProvider.CARTESIA -> {
                        ApiKeyField(
                            label = "Cartesia API Key",
                            value = settings.cartesiaApiKey,
                            onValueChange = viewModel::updateCartesiaApiKey,
                            placeholder = "Enter Cartesia API key",
                            isSet = settings.cartesiaApiKey.isNotBlank(),
                            helperText = "Get at cartesia.ai"
                        )
                        SettingsTextField(
                            label = "Cartesia Voice ID",
                            value = settings.cartesiaVoiceId,
                            onValueChange = viewModel::updateCartesiaVoiceId,
                            placeholder = "638efaaa-4d0c-442e-b701-3fae16aad012"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Sameer (Problem Solver)" to "638efaaa-4d0c-442e-b701-3fae16aad012",
                                "Gareth (Conversational)" to "3a01612e-07da-4a85-bba8-ce10481df477",
                                "Harlow (Warm Support)" to "6c756032-11c3-4be8-a120-74f9673fb700",
                                "Maren (Resourceful Guide)" to "b2e12ebd-7bed-4d48-9243-0aa7acef4140"
                            ).forEach { (label, id) ->
                                val isSelected = settings.cartesiaVoiceId == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCartesiaVoiceId(id) },
                                    colors = customFilterChipColors(),
                                    border = customFilterChipBorder(isSelected),
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    TtsProvider.ELEVENLABS -> {
                        val elevenLabsKey = settings.elevenLabsApiKey.ifBlank { viewModel.getElevenLabsApiKey() }
                        ApiKeyField(
                            label = "ElevenLabs API Key",
                            value = elevenLabsKey,
                            onValueChange = viewModel::updateElevenLabsApiKey,
                            placeholder = "sk_…",
                            isSet = elevenLabsKey.isNotBlank(),
                            helperText = "Get at elevenlabs.io"
                        )
                        SettingsTextField(
                            label = "ElevenLabs Voice ID",
                            value = settings.cartesiaVoiceId,
                            onValueChange = viewModel::updateCartesiaVoiceId,
                            placeholder = "JBFqnCBsd6RMkjVDRZzb (George)"
                        )
                        val ttsUriHandler = LocalUriHandler.current
                        Text(
                            text = "Browse voices at: https://elevenlabs.io/app/voice-library ↗",
                            style = MaterialTheme.typography.labelSmall,
                            color = FluxCyan,
                            modifier = Modifier
                                .clickable {
                                    ttsUriHandler.openUri("https://elevenlabs.io/app/voice-library")
                                }
                                .padding(start = 4.dp, top = 2.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "George" to "JBFqnCBsd6RMkjVDRZzb",
                                "Sarah" to "EXAVITQu4vr4xnSDxMaL",
                                "Charlie" to "IKne3meq5aSn9XLyUdCD",
                                "Laura" to "FGY2WhTYpPnrIDTdsKH5"
                            ).forEach { (label, id) ->
                                val isSelected = settings.cartesiaVoiceId == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCartesiaVoiceId(id) },
                                    colors = customFilterChipColors(),
                                    border = customFilterChipBorder(isSelected),
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    TtsProvider.OPENAI -> {
                        ApiKeyField(
                            label = "OpenAI API Key (Speech)",
                            value = settings.openAiApiKey,
                            onValueChange = viewModel::updateOpenAiApiKey,
                            placeholder = "sk-…",
                            isSet = settings.openAiApiKey.isNotBlank(),
                            helperText = "Reused from Credentials section or update here"
                        )
                        SettingsTextField(
                            label = "OpenAI Voice Model",
                            value = settings.cartesiaVoiceId,
                            onValueChange = viewModel::updateCartesiaVoiceId,
                            placeholder = "alloy"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("alloy", "echo", "shimmer", "fable", "onyx", "nova").forEach { voice ->
                                val isSelected = settings.cartesiaVoiceId == voice
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCartesiaVoiceId(voice) },
                                    colors = customFilterChipColors(),
                                    border = customFilterChipBorder(isSelected),
                                    label = { Text(voice, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    TtsProvider.DEEPGRAM_AURA -> {
                        ApiKeyField(
                            label = "Deepgram API Key (Aura)",
                            value = settings.deepgramApiKey,
                            onValueChange = viewModel::updateDeepgramApiKey,
                            placeholder = "Token …",
                            isSet = settings.deepgramApiKey.isNotBlank(),
                            helperText = "Reused from STT section or update here"
                        )
                        SettingsTextField(
                            label = "Aura Voice Model",
                            value = settings.cartesiaVoiceId,
                            onValueChange = viewModel::updateCartesiaVoiceId,
                            placeholder = "aura-asteria-en"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("aura-asteria-en", "aura-luna-en", "aura-stella-en", "aura-helios-en").forEach { model ->
                                val isSelected = settings.cartesiaVoiceId == model
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateCartesiaVoiceId(model) },
                                    colors = customFilterChipColors(),
                                    border = customFilterChipBorder(isSelected),
                                    label = { Text(model, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    TtsProvider.ANDROID -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Using 100% on-device offline Android TextToSpeech engine. Zero API keys, zero network latency.",
                                fontSize = 12.sp,
                                color = TextSlateMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    TtsProvider.CUSTOM -> {
                        SettingsTextField(
                            label = "Custom Speech Endpoint (/v1/audio/speech)",
                            value = settings.localLlmUrl,
                            onValueChange = viewModel::updateLocalLlmUrl,
                            placeholder = "https://api.your-tts.com/v1"
                        )
                        SettingsTextField(
                            label = "Voice / Speaker ID",
                            value = settings.cartesiaVoiceId,
                            onValueChange = viewModel::updateCartesiaVoiceId,
                            placeholder = "default"
                        )
                    }
                }

                Text(
                    "Speech rate: ${"%.1f".format(settings.ttsSpeechRate)}×",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.ttsSpeechRate,
                    onValueChange = { viewModel.updateTtsSpeechRate((it * 10).roundToInt() / 10f) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    enabled = settings.ttsEnabled,
                    colors = customSliderColors()
                )
                Text(
                    "Pitch: ${"%.1f".format(settings.ttsPitch)}×",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.ttsPitch,
                    onValueChange = { viewModel.updateTtsPitch((it * 10).roundToInt() / 10f) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    enabled = settings.ttsEnabled,
                    colors = customSliderColors()
                )
            }
        }
        }

        // ── Conversational UX ─────────────────────────────────────────────────
        SettingsSection("Conversational UX") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto barge-in (VAD)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Interrupt the AI just by speaking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.vadEnabled,
                    onCheckedChange = viewModel::updateVadEnabled,
                    colors = customSwitchColors()
                )
            }
            if (settings.vadEnabled) {
                Text(
                    "Barge-in sensitivity: ${
                        when {
                            settings.vadSensitivity <= 600  -> "High"
                            settings.vadSensitivity <= 1500 -> "Medium"
                            else                            -> "Low"
                        }
                    }",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.vadSensitivity.toFloat(),
                    onValueChange = { viewModel.updateVadSensitivity(it.roundToInt()) },
                    valueRange = 200f..3000f,
                    steps = 27,
                    colors = customSliderColors()
                )
                Text(
                    "Lower = more sensitive. Raise if noise triggers early interruption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Thinking backchannels", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Speak a brief cue while the AI thinks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.backchannelEnabled,
                    onCheckedChange = viewModel::updateBackchannelEnabled,
                    colors = customSwitchColors()
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { viewModel.saveCustomPipelineAndActivate(onSuccess = onBack) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FluxEmerald,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("Save & Run Custom Pipeline", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.Black)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

private data class PresetItem(val label: String, val tag: String)

@Composable
private fun PresetInfoCard(
    items: List<PresetItem>
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "PRE-CONFIGURED RUNTIME STACK",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = FluxCyan,
                letterSpacing = 1.0.sp
            )
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = HairlineBorder)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPureWhite
                    )
                    Text(
                        item.tag,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessIndicator(isReady: Boolean) {
    val readyGreen   = Color(0xFF16A34A)
    val pendingColor = MaterialTheme.colorScheme.primary
    val bgColor      = if (isReady) readyGreen.copy(alpha = 0.10f) else pendingColor.copy(alpha = 0.08f)
    val fgColor      = if (isReady) readyGreen else pendingColor
    val icon         = if (isReady) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked
    val message      = if (isReady) "Ready: tap the mic and start talking!" else "Add your API keys above to get started"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.labelLarge, color = fgColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CartesiaVoiceSection(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Voice (Cartesia TTS)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
        }
        Text(
            "Leave blank to fall back to Android TTS.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ApiKeyField(
            label = "Cartesia API Key",
            value = settings.cartesiaApiKey,
            onValueChange = viewModel::updateCartesiaApiKey,
            placeholder = "Enter Cartesia API key",
            isSet = settings.cartesiaApiKey.isNotBlank(),
            helperText = "Get at cartesia.ai"
        )
        ApiKeyField(
            label = "Voice ID",
            value = settings.cartesiaVoiceId,
            onValueChange = viewModel::updateCartesiaVoiceId,
            placeholder = "638efaaa-4d0c-442e-b701-3fae16aad012",
            isSet = settings.cartesiaVoiceId.isNotBlank(),
            isPassword = false
        )
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isSet: Boolean,
    isPassword: Boolean = true,
    helperText: String? = null
) {
    var localValue by remember { mutableStateOf(value) }
    var isFocused  by remember { mutableStateOf(false) }

    LaunchedEffect(value) { if (!isFocused) localValue = value }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = localValue,
            onValueChange = { localValue = it; onValueChange(it) },
            label = { Text(label) },
            placeholder = {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            },
            trailingIcon = {
                if (isSet)
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                else
                    Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs ->
                    if (!fs.isFocused && isFocused) onValueChange(localValue)
                    isFocused = fs.isFocused
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
        )
        if (helperText != null) {
            Text(
                helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = FluxCyan,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                title.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.0.sp,
                color = FluxCyan,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = HairlineBorder
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dropdown composables (unchanged logic, kept private)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    var localValue by remember { mutableStateOf(value) }
    var isFocused  by remember { mutableStateOf(false) }

    LaunchedEffect(value) { if (!isFocused) localValue = value }

    OutlinedTextField(
        value = localValue,
        onValueChange = { localValue = it; onValueChange(it) },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { fs ->
                if (!fs.isFocused && isFocused) onValueChange(localValue)
                isFocused = fs.isFocused
            },
        singleLine = maxLines == 1,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceModeDropdown(
    selectedMode: VoiceMode,
    onModeSelected: (VoiceMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VoiceMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = { onModeSelected(mode); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SttProviderDropdown(
    selectedProvider: SttProvider,
    onProviderSelected: (SttProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("STT Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SttProvider.entries.sortedBy { it.displayName }.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = { onProviderSelected(provider); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: ProviderType,
    onProviderSelected: (ProviderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProviderType.entries.sortedBy { it.displayName }.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = { onProviderSelected(provider); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsProviderDropdown(
    selectedProvider: TtsProvider,
    onProviderSelected: (TtsProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("TTS Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TtsProvider.entries.sortedBy { it.displayName }.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = { onProviderSelected(provider); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ChangeIdentityDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onRollRandom: () -> String
) {
    var usernameInput by remember { mutableStateOf(currentUsername) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        titleContentColor = TextPureWhite,
        textContentColor = TextSlateMedium,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ObsidianVoid),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = FluxCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    "Change Identity",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPureWhite
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Update your playground username. Your API keys, custom pipelines, and benchmark history will remain completely intact.",
                    fontSize = 13.sp,
                    color = TextSlateMedium,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = {
                        usernameInput = it
                        if (error != null) error = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val random = onRollRandom()
                                usernameInput = random
                                error = null
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Casino,
                                contentDescription = "Roll Random Username",
                                tint = FluxCyan
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedBorderColor = FluxCyan,
                        unfocusedBorderColor = HairlineBorder,
                        focusedLabelColor = FluxCyan,
                        unfocusedLabelColor = TextSlateMuted,
                        cursorColor = FluxCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = usernameInput.trim()
                    if (trimmed.length < 3) {
                        error = "Username must be at least 3 characters"
                        return@Button
                    }
                    onConfirm(trimmed)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FluxCyan,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Identity", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSlateMedium)
            }
        }
    )
}

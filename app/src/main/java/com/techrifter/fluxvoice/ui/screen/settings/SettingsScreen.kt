package com.techrifter.fluxvoice.ui.screen.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.domain.model.AppMode
import com.techrifter.fluxvoice.domain.model.AppSettings
import com.techrifter.fluxvoice.domain.model.ProviderType
import com.techrifter.fluxvoice.domain.model.SttProvider
import com.techrifter.fluxvoice.domain.model.VoiceMode
import kotlin.math.roundToInt

// ── Gradient palettes per mode ────────────────────────────────────────────────
private val modeGradients = mapOf(
    AppMode.FAST     to listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
    AppMode.THINKING to listOf(Color(0xFF8B5CF6), Color(0xFFDB2777)),
    AppMode.CUSTOM   to listOf(Color(0xFF059669), Color(0xFF0EA5E9))
)

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                    AppMode.FAST     -> FastModeContent(settings, viewModel)
                    AppMode.THINKING -> ThinkingModeContent(settings, viewModel)
                    AppMode.CUSTOM   -> CustomModeContent(settings, viewModel)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
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
    val gradient = modeGradients[mode]!!
    val elevation by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "card_elev")
    val scale     by animateFloatAsState(if (isSelected) 1.04f else 1f, label = "card_scale")

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected)
                    Modifier.background(Brush.linearGradient(gradient))
                else
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(mode.emoji, fontSize = 22.sp)
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = mode.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ⚡ Fast mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FastModeContent(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Zero setup. Plug in your keys and start talking — Groq's ultra-fast inference + Deepgram deliver the lowest latency possible.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PresetInfoCard(
            gradientColors = modeGradients[AppMode.FAST]!!,
            items = listOf(
                PresetItem("⚡", "Groq · Llama 3.3 70B", "AI Model"),
                PresetItem("🎙", "Deepgram Streaming", "Real-time STT"),
                PresetItem("↩️", "6-turn memory", "Context"),
                PresetItem("🎯", "VAD + Backchannel", "Smart UX"),
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🧠 Thinking mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThinkingModeContent(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Built for depth. Gemini 2.5 Pro's reasoning capabilities deliver thoughtful, nuanced responses with full conversational context.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PresetInfoCard(
            gradientColors = modeGradients[AppMode.THINKING]!!,
            items = listOf(
                PresetItem("🧠", "Google Gemini 2.5 Pro", "AI Model"),
                PresetItem("🎙", "Deepgram Streaming", "Real-time STT"),
                PresetItem("↩️", "20-turn memory", "Context"),
                PresetItem("🎯", "Temperature 0.3", "Precision"),
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🔌 Custom mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomModeContent(settings: AppSettings, viewModel: SettingsViewModel) {
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
                ApiKeyField(
                    label = "ElevenLabs Agent ID",
                    value = settings.elevenLabsAgentId,
                    onValueChange = viewModel::updateElevenLabsAgentId,
                    placeholder = "your_agent_id_here",
                    isSet = settings.elevenLabsAgentId.isNotBlank(),
                    isPassword = false
                )
            }
            if (settings.voiceMode == VoiceMode.STANDARD) {
                SttProviderDropdown(
                    selectedProvider = settings.sttProvider,
                    onProviderSelected = { viewModel.updateSttProvider(it.name) }
                )
                if (settings.sttProvider == SttProvider.DEEPGRAM) {
                    ApiKeyField(
                        label = "Deepgram API Key",
                        value = settings.deepgramApiKey,
                        onValueChange = viewModel::updateDeepgramApiKey,
                        placeholder = "Token …",
                        isSet = settings.deepgramApiKey.isNotBlank()
                    )
                } else {
                    Text(
                        "Whisper uses the OpenAI API key from the Credentials section.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

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
                ProviderType.LOCAL -> SettingsTextField(
                    label = "Ollama URL",
                    value = settings.localLlmUrl,
                    onValueChange = viewModel::updateLocalLlmUrl,
                    placeholder = "http://localhost:11434"
                )
            }
        }

        // ── Model ─────────────────────────────────────────────────────────────
        SettingsSection("Model") {
            SettingsTextField(
                label = "Model ID",
                value = settings.modelId,
                onValueChange = viewModel::updateModelId,
                placeholder = settings.providerType.defaultModel
            )
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
                steps = 9
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
                steps = 18
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
                }
                Switch(
                    checked = settings.ttsEnabled,
                    onCheckedChange = viewModel::updateTtsEnabled
                )
            }
            ApiKeyField(
                label = "Cartesia API Key",
                value = settings.cartesiaApiKey,
                onValueChange = viewModel::updateCartesiaApiKey,
                placeholder = "sk-ant-…",
                isSet = settings.cartesiaApiKey.isNotBlank(),
                helperText = "Optional · Leave blank to use Android TTS"
            )
            SettingsTextField(
                label = "Cartesia Voice ID",
                value = settings.cartesiaVoiceId,
                onValueChange = viewModel::updateCartesiaVoiceId,
                placeholder = "638efaaa-4d0c-442e-b701-3fae16aad012"
            )
            if (settings.cartesiaApiKey.isBlank()) {
                Text(
                    "Speech rate: ${"%.1f".format(settings.ttsSpeechRate)}×",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.ttsSpeechRate,
                    onValueChange = { viewModel.updateTtsSpeechRate((it * 10).roundToInt() / 10f) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    enabled = settings.ttsEnabled
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
                    enabled = settings.ttsEnabled
                )
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
                    onCheckedChange = viewModel::updateVadEnabled
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
                    steps = 27
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
                    onCheckedChange = viewModel::updateBackchannelEnabled
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

private data class PresetItem(val emoji: String, val label: String, val tag: String)

@Composable
private fun PresetInfoCard(
    gradientColors: List<Color>,
    items: List<PresetItem>
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        gradientColors[0].copy(alpha = 0.10f),
                        gradientColors[1].copy(alpha = 0.14f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pre-configured for you",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.emoji, fontSize = 16.sp)
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                item.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
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
    val message      = if (isReady) "Ready — tap the mic and start talking!" else "Add your API keys above to get started"

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
            Text("🎧", fontSize = 16.sp)
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
            placeholder = "sk-ant-…",
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
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
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
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
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
            SttProvider.entries.forEach { provider ->
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
            ProviderType.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = { onProviderSelected(provider); expanded = false }
                )
            }
        }
    }
}

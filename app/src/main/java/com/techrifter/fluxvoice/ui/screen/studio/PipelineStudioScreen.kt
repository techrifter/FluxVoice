package com.techrifter.fluxvoice.ui.screen.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.ui.theme.AccentBlue
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
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineStudioScreen(
    onBack: () -> Unit,
    initialModelId: String? = null,
    initialProvider: String? = null,
    initialCategory: String? = null,
    pipelineId: String? = null,
    isNew: Boolean = false,
    viewModel: PipelineStudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentModels by viewModel.currentProviderModels.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    var showModelCatalogSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(pipelineId, isNew) {
        viewModel.initialize(pipelineId, isNew)
    }

    LaunchedEffect(initialModelId, initialProvider, initialCategory) {
        if (!initialModelId.isNullOrBlank()) {
            viewModel.applyCatalogModel(
                modelId = initialModelId,
                provider = initialProvider ?: "",
                categoryName = initialCategory ?: ""
            )
            Toast.makeText(context, "Loaded $initialModelId into Studio", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Custom Stack?",
                    color = TextPureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${uiState.name}'? This action cannot be undone.",
                    color = TextSlateMedium,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deletePipeline {
                            Toast.makeText(context, "Pipeline deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = TextPureWhite, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = ObsidianVoid,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isNew) "NEW ARCHITECTURE" else "ARCHITECTURE & WORKBENCH",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isNew) "Create Custom Stack" else "Pipeline Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPureWhite
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPureWhite)
                    }
                },
                actions = {
                    if (!isNew && !uiState.isPreset) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Custom Stack", tint = Color(0xFFEF4444))
                        }
                    }
                    IconButton(onClick = viewModel::generateSdkCode) {
                        Icon(Icons.Rounded.Code, contentDescription = "Export SDK", tint = TextSlateMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianVoid)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::generateSdkCode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export SDK", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.saveAndActivate {
                                Toast.makeText(context, "Pipeline activated in Playground", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FluxCyan,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save & Run", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Signal Architecture Flow Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StudioFlowBlock(tag = "INPUT", value = uiState.sttProviderId.uppercase())
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextSlateDark, modifier = Modifier.size(14.dp))
                    StudioFlowBlock(tag = "BRAIN", value = uiState.llmModelId.substringAfterLast('/').uppercase())
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextSlateDark, modifier = Modifier.size(14.dp))
                    StudioFlowBlock(tag = "OUTPUT", value = uiState.ttsProviderId.uppercase())
                }
            }

            // Section 1: Pipeline Meta
            StudioCard(title = "PIPELINE IDENTIFIER") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Pipeline Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Personal Custom Stack", fontSize = 12.sp, color = TextSlateDark) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = studioFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Tailored conversational voice stack with custom STT, LLM, and TTS providers", fontSize = 12.sp, color = TextSlateDark) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = studioFieldColors()
                )
            }

            // Section 2: Speech-to-Text
            StudioCard(title = "01 / SPEECH RECOGNITION (STT)") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentedPill(label = "Deepgram Nova-3", selected = uiState.sttProviderId == "deepgram") {
                        viewModel.updateSttProvider("deepgram")
                    }
                    SegmentedPill(label = "Groq Whisper Turbo", selected = uiState.sttProviderId == "groq_whisper") {
                        viewModel.updateSttProvider("groq_whisper")
                    }
                    SegmentedPill(label = "OpenAI Whisper", selected = uiState.sttProviderId == "whisper") {
                        viewModel.updateSttProvider("whisper")
                    }
                    SegmentedPill(label = "Custom BYOK", selected = uiState.sttProviderId == "custom") {
                        viewModel.updateSttProvider("custom")
                    }
                }

                if (uiState.sttProviderId == "custom") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.customSttEndpointUrl,
                        onValueChange = viewModel::updateCustomSttEndpointUrl,
                        label = { Text("Custom Transcription Endpoint URL", fontSize = 12.sp) },
                        placeholder = { Text("https://api.together.xyz/v1/audio/transcriptions", fontSize = 12.sp, color = TextSlateDark) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = studioFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.customSttApiKey,
                        onValueChange = viewModel::updateCustomSttApiKey,
                        label = { Text("Custom STT API Key (Optional for local)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = studioFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.sttModelId,
                        onValueChange = viewModel::updateSttModel,
                        label = { Text("Transcription Model Identifier", fontSize = 12.sp) },
                        placeholder = { Text("whisper-1", fontSize = 12.sp, color = TextSlateDark) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = studioFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Endpointing Silence Window", fontSize = 12.sp, color = TextSlateMedium)
                    Text("${uiState.sttEndpointingMs}ms", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPureWhite)
                }
                Slider(
                    value = uiState.sttEndpointingMs.toFloat(),
                    onValueChange = { viewModel.updateSttEndpointing(it.toInt()) },
                    valueRange = 200f..1500f,
                    steps = 12,
                    colors = SliderDefaults.colors(thumbColor = TextPureWhite, activeTrackColor = TextPureWhite, inactiveTrackColor = ObsidianVoid)
                )
            }

            // Section 3: Large Language Model
            StudioCard(title = "02 / INTELLIGENCE & REASONING (LLM)") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentedPill(label = "Google Gemini", selected = uiState.llmProviderId == "gemini") {
                        viewModel.updateLlmProvider("gemini")
                    }
                    SegmentedPill(label = "Groq Cloud", selected = uiState.llmProviderId == "groq") {
                        viewModel.updateLlmProvider("groq")
                    }
                    SegmentedPill(label = "OpenAI", selected = uiState.llmProviderId == "openai") {
                        viewModel.updateLlmProvider("openai")
                    }
                    SegmentedPill(label = "OpenRouter", selected = uiState.llmProviderId == "openrouter") {
                        viewModel.updateLlmProvider("openrouter")
                    }
                    SegmentedPill(label = "Custom BYOK", selected = uiState.llmProviderId == "custom") {
                        viewModel.updateLlmProvider("custom")
                    }
                }

                if (uiState.llmProviderId == "custom") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.customEndpointUrl,
                        onValueChange = viewModel::updateCustomEndpointUrl,
                        label = { Text("OpenAI-Compatible Endpoint URL", fontSize = 12.sp) },
                        placeholder = { Text("https://api.together.xyz/v1", fontSize = 12.sp, color = TextSlateDark) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = studioFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.customApiKey,
                        onValueChange = viewModel::updateCustomApiKey,
                        label = { Text("Custom Provider Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = studioFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Dynamic fast-picks derived from live discovered models for the selected provider
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
                            text = "LIVE MODELS (${currentModels.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluxCyan,
                            letterSpacing = 1.sp
                        )
                        if (uiState.llmProviderId.lowercase() in listOf("groq", "gemini", "openai", "openrouter")) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianVoid,
                                border = BorderStroke(1.dp, HairlineHighlight),
                                modifier = Modifier.clickable { showModelCatalogSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = FluxCyan, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "Browse Directory ↗",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluxCyan
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((name, modelId, isFree) in dynamicQuickPicks) {
                            val isSelected = uiState.llmModelId.equals(modelId, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) ObsidianVoid else ObsidianCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HairlineHighlight else HairlineBorder
                                ),
                                modifier = Modifier.clickable { viewModel.updateLlmModel(modelId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isFree) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(FluxEmerald, CircleShape)
                                        )
                                    }
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) FluxCyan else TextSlateMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                } else if (isFetchingModels) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = FluxCyan
                        )
                        Text(
                            text = "Fetching live models from directory…",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSlateMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                } else if (uiState.llmProviderId.lowercase() in listOf("groq", "gemini", "openai", "openrouter")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No models fetched yet",
                            fontSize = 11.sp,
                            color = TextSlateMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianVoid,
                            border = BorderStroke(1.dp, HairlineHighlight),
                            modifier = Modifier.clickable { viewModel.refreshCurrentProviderModels() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = FluxCyan, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Fetch Live Models",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluxCyan
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                OutlinedTextField(
                    value = uiState.llmModelId,
                    onValueChange = viewModel::updateLlmModel,
                    label = { Text("Model Identifier (or select from live directory)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. openai/gpt-oss-120b", fontSize = 12.sp, color = TextSlateDark) },
                    trailingIcon = {
                        if (uiState.llmProviderId.lowercase() in listOf("groq", "gemini", "openai", "openrouter")) {
                            IconButton(onClick = { showModelCatalogSheet = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Browse Live Directory", tint = FluxCyan)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = studioFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sampling Temperature", fontSize = 12.sp, color = TextSlateMedium)
                    Text("${"%.2f".format(uiState.temperature)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPureWhite)
                }
                Slider(
                    value = uiState.temperature,
                    onValueChange = viewModel::updateTemperature,
                    valueRange = 0.0f..1.2f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = TextPureWhite, activeTrackColor = TextPureWhite, inactiveTrackColor = ObsidianVoid)
                )

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.systemPrompt,
                    onValueChange = viewModel::updateSystemPrompt,
                    label = { Text("System Persona & Prompt", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(8.dp),
                    colors = studioFieldColors()
                )
            }

            // Section 4: Voice Synthesis (TTS)
            StudioCard(title = "03 / VOICE SYNTHESIS (TTS)") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentedPill(label = "Android System", selected = uiState.ttsProviderId == "android") {
                        viewModel.updateTtsProvider("android")
                    }
                    SegmentedPill(label = "Cartesia Sonic", selected = uiState.ttsProviderId == "cartesia") {
                        viewModel.updateTtsProvider("cartesia")
                    }
                    SegmentedPill(label = "Deepgram Aura", selected = uiState.ttsProviderId == "deepgram") {
                        viewModel.updateTtsProvider("deepgram")
                    }
                    SegmentedPill(label = "ElevenLabs Neural", selected = uiState.ttsProviderId == "elevenlabs") {
                        viewModel.updateTtsProvider("elevenlabs")
                    }
                    SegmentedPill(label = "OpenAI Speech", selected = uiState.ttsProviderId == "openai") {
                        viewModel.updateTtsProvider("openai")
                    }
                    SegmentedPill(label = "Custom BYOK", selected = uiState.ttsProviderId == "custom") {
                        viewModel.updateTtsProvider("custom")
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (uiState.ttsProviderId) {
                    "cartesia" -> {
                        OutlinedTextField(
                            value = uiState.ttsVoiceId,
                            onValueChange = viewModel::updateTtsVoiceId,
                            label = { Text("Cartesia Voice ID", fontSize = 12.sp) },
                            placeholder = { Text("638efaaa-4d0c-442e-b701-3fae16aad012", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }
                    "elevenlabs" -> {
                        OutlinedTextField(
                            value = uiState.ttsVoiceId,
                            onValueChange = viewModel::updateTtsVoiceId,
                            label = { Text("ElevenLabs Voice ID", fontSize = 12.sp) },
                            placeholder = { Text("JBFqnCBsd6RMkjVDRZzb (George)", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }
                    "openai" -> {
                        OutlinedTextField(
                            value = uiState.ttsVoiceId,
                            onValueChange = viewModel::updateTtsVoiceId,
                            label = { Text("OpenAI Voice (alloy, echo, fable, onyx, nova, shimmer)", fontSize = 12.sp) },
                            placeholder = { Text("alloy", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }
                    "deepgram" -> {
                        OutlinedTextField(
                            value = uiState.ttsVoiceId,
                            onValueChange = viewModel::updateTtsVoiceId,
                            label = { Text("Deepgram Aura Voice Model", fontSize = 12.sp) },
                            placeholder = { Text("aura-asteria-en", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }
                    "custom" -> {
                        OutlinedTextField(
                            value = uiState.customEndpointUrl,
                            onValueChange = viewModel::updateCustomEndpointUrl,
                            label = { Text("Custom Speech Endpoint URL (/v1/audio/speech)", fontSize = 12.sp) },
                            placeholder = { Text("https://api.your-tts.com/v1", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = uiState.ttsVoiceId,
                            onValueChange = viewModel::updateTtsVoiceId,
                            label = { Text("Voice / Speaker ID", fontSize = 12.sp) },
                            placeholder = { Text("af_sarah", fontSize = 12.sp, color = TextSlateDark) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = studioFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                    }
                    "android" -> {
                        Text(
                            text = "Built-in offline Android TextToSpeech engine. Zero API keys, zero network latency.",
                            fontSize = 12.sp,
                            color = TextSlateMedium
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speech Velocity Rate", fontSize = 12.sp, color = TextSlateMedium)
                    Text("${"%.2f".format(uiState.speechRate)}x", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPureWhite)
                }
                Slider(
                    value = uiState.speechRate,
                    onValueChange = viewModel::updateSpeechRate,
                    valueRange = 0.7f..1.6f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = TextPureWhite, activeTrackColor = TextPureWhite, inactiveTrackColor = ObsidianVoid)
                )
            }

            // Section 5: Conversational Dynamics
            StudioCard(title = "04 / RUNTIME DYNAMICS & AEC") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware AEC Auto Barge-in", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextPureWhite)
                        Text("Interrupt assistant audio instantaneously when user speaks", fontSize = 11.sp, color = TextSlateMuted)
                    }
                    Switch(
                        checked = uiState.vadEnabled,
                        onCheckedChange = viewModel::updateVadEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianVoid, checkedTrackColor = TextPureWhite)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Backchannel Thinking Acknowledgment", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextPureWhite)
                        Text("Speaks quick fillers ('Got it', 'Sure') during slow remote queries", fontSize = 11.sp, color = TextSlateMuted)
                    }
                    Switch(
                        checked = uiState.backchannelEnabled,
                        onCheckedChange = viewModel::updateBackchannelEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianVoid, checkedTrackColor = TextPureWhite)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Modal Sheet for Exported SDK Code
    uiState.exportedSdkCode?.let { code ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSdkCode,
            containerColor = ObsidianSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 3.dp)
                        .background(HairlineBorder, CircleShape)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CODE GENERATOR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "FluxVoice Kotlin SDK",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPureWhite
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("FluxVoice SDK Code", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied Kotlin SDK code to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TextPureWhite)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = ObsidianVoid,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPureWhite,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    // Modal Sheet for Multi-Provider Live Neural Directory
    if (showModelCatalogSheet) {
        val providerTitle = when (uiState.llmProviderId.lowercase()) {
            "groq"       -> "Groq Cloud"
            "gemini"     -> "Google Gemini"
            "openai"     -> "OpenAI"
            "openrouter" -> "OpenRouter"
            else         -> uiState.llmProviderId.uppercase()
        }
        val hasKey = viewModel.hasKeyForProvider(uiState.llmProviderId)

        LaunchedEffect(showModelCatalogSheet, uiState.llmProviderId) {
            if (showModelCatalogSheet && currentModels.isEmpty()) {
                viewModel.refreshCurrentProviderModels()
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showModelCatalogSheet = false },
            containerColor = ObsidianSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 3.dp)
                        .background(HairlineBorder, CircleShape)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LIVE NEURAL DIRECTORY · ${uiState.llmProviderId.uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluxCyan,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$providerTitle Catalog (${currentModels.size} Models)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPureWhite
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewModel.refreshCurrentProviderModels()
                            Toast.makeText(context, "Refreshing $providerTitle models…", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = FluxCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showModelCatalogSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSlateMuted)
                        }
                    }
                }

                if (!hasKey && uiState.llmProviderId.lowercase() != "openrouter") {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianVoid,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing verified free-tier models. Add your $providerTitle key in Key Vault to load your live account models.",
                                fontSize = 11.sp,
                                color = TextSlateMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = modelSearchQuery,
                    onValueChange = { modelSearchQuery = it },
                    placeholder = { Text("Search models by ID, name, or capability…", fontSize = 12.sp, color = TextSlateDark) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSlateMuted) },
                    trailingIcon = {
                        if (modelSearchQuery.isNotBlank()) {
                            IconButton(onClick = { modelSearchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextSlateMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = studioFieldColors(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                val filtered = remember(currentModels, modelSearchQuery) {
                    if (modelSearchQuery.isBlank()) {
                        currentModels
                    } else {
                        val q = modelSearchQuery.trim().lowercase()
                        currentModels.filter {
                            it.id.lowercase().contains(q) ||
                            it.name.lowercase().contains(q) ||
                            it.description.lowercase().contains(q) ||
                            it.quotaNote.lowercase().contains(q)
                        }
                    }
                }

                if (currentModels.isEmpty() && isFetchingModels) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = FluxCyan
                            )
                            Text(
                                text = "Fetching live $providerTitle models…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPureWhite
                            )
                            Text(
                                text = "Querying live neural directory across providers",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSlateDark
                            )
                        }
                    }
                } else if (currentModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No live models found for $providerTitle.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPureWhite
                            )
                            Text(
                                text = "Check your connection or tap below to fetch.",
                                fontSize = 11.sp,
                                color = TextSlateMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.refreshCurrentProviderModels() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, HairlineHighlight)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = FluxCyan)
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch Live Models", fontSize = 12.sp, color = FluxCyan)
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No models matching \"$modelSearchQuery\"",
                            fontSize = 12.sp,
                            color = TextSlateMedium
                        )
                    }
                } else {
                    Text(
                        text = "${filtered.size} models available (${filtered.count { it.isFreeTier }} free-tier ready)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSlateDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            val isSelected = uiState.llmModelId.equals(model.id, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ObsidianVoid else ObsidianCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HairlineHighlight else HairlineBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateLlmModel(model.id)
                                        showModelCatalogSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        // Row 1: Model Name + Selection checkmark
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = model.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isSelected) FluxCyan else TextPureWhite,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isSelected) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = FluxCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Row 2: Metadata Badges (Free Tier, Quota, Popular, Context)
                                        val hasBadges = model.isFreeTier || model.quotaNote.isNotBlank() || model.isPopular || model.contextLength > 0
                                        if (hasBadges) {
                                            Spacer(Modifier.height(5.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (model.isFreeTier) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF06281E),
                                                        border = BorderStroke(1.dp, Color(0xFF10B981))
                                                    ) {
                                                        Text(
                                                            text = "FREE TIER",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF10B981),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (model.quotaNote.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ObsidianVoid,
                                                        border = BorderStroke(1.dp, HairlineBorder)
                                                    ) {
                                                        Text(
                                                            text = model.quotaNote,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = if (model.isFreeTier) Color(0xFF34D399) else TextSlateMedium,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                } else if (model.isPopular) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ObsidianVoid,
                                                        border = BorderStroke(1.dp, HairlineHighlight)
                                                    ) {
                                                        Text(
                                                            text = "POPULAR",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = FluxCyan,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (model.contextLength > 0) {
                                                    val ctxText = if (model.contextLength >= 1_000_000) {
                                                        "${model.contextLength / 1_000_000}M ctx"
                                                    } else {
                                                        "${model.contextLength / 1000}k ctx"
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ObsidianVoid,
                                                        border = BorderStroke(1.dp, HairlineBorder)
                                                    ) {
                                                        Text(
                                                            text = ctxText,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = TextSlateMedium,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Row 3: Technical Identifier
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            text = model.id,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextSlateDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Row 4: Description
                                        if (model.description.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = model.description,
                                                fontSize = 11.sp,
                                                color = TextSlateMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FluxCyan,
                letterSpacing = 1.0.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun SegmentedPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) ObsidianFloat else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) HairlineHighlight else HairlineBorder
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) FluxCyan else TextSlateMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun StudioFlowBlock(tag: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tag, fontSize = 9.sp, color = TextSlateDark, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPureWhite
        )
    }
}

@Composable
private fun studioFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HairlineHighlight,
    unfocusedBorderColor = HairlineBorder,
    focusedContainerColor = ObsidianVoid,
    unfocusedContainerColor = ObsidianVoid,
    focusedLabelColor = TextPureWhite,
    unfocusedLabelColor = TextSlateMuted
)

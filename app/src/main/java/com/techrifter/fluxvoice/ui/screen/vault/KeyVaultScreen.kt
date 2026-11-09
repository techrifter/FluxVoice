package com.techrifter.fluxvoice.ui.screen.vault

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.data.security.KeyValidationStatus
import com.techrifter.fluxvoice.ui.theme.AccentBlue
import com.techrifter.fluxvoice.ui.theme.AccentGreen
import com.techrifter.fluxvoice.ui.theme.AccentRose
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.HairlineHighlight
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.ObsidianVoid
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultScreen(
    onBack: () -> Unit,
    viewModel: KeyVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

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
                            text = "SECURITY & VAULT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Hardware Key Vault",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianVoid)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Security Protocol Badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AccentGreen.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero-Knowledge Local Storage",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPureWhite
                        )
                        Text(
                            text = "Hardware Keystore MasterKey.AES256_GCM. Keys never leave device memory.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSlateMuted
                        )
                    }
                }
            }

            KeyFieldCard(
                title = "Cartesia Sonic API Key",
                subtitle = "Ultra-low-latency neural voice synthesis (~90ms TTFA)",
                value = uiState.cartesiaKey,
                status = uiState.validationStatus["cartesia"],
                getKeyUrl = "https://play.cartesia.ai/keys",
                onValueChange = { viewModel.updateKey("cartesia", it) },
                onValidate = { viewModel.validateKey("cartesia") }
            )

            KeyFieldCard(
                title = "Deepgram Nova-3 API Key",
                subtitle = "Real-time streaming speech-to-text via sub-150ms WebSocket",
                value = uiState.deepgramKey,
                status = uiState.validationStatus["deepgram"],
                getKeyUrl = "https://console.deepgram.com",
                onValueChange = { viewModel.updateKey("deepgram", it) },
                onValidate = { viewModel.validateKey("deepgram") }
            )

            KeyFieldCard(
                title = "ElevenLabs API Key",
                subtitle = "Studio-grade neural voice synthesis (Flash v2.5 / Turbo v2.5)",
                value = uiState.elevenLabsKey,
                status = uiState.validationStatus["elevenlabs"],
                getKeyUrl = "https://elevenlabs.io/app/api-keys",
                onValueChange = { viewModel.updateKey("elevenlabs", it) },
                onValidate = { viewModel.validateKey("elevenlabs") }
            )

            KeyFieldCard(
                title = "Google Gemini API Key",
                subtitle = "Direct Google AI Studio access for Gemini 2.5 Flash",
                value = uiState.geminiKey,
                status = uiState.validationStatus["gemini"],
                getKeyUrl = "https://aistudio.google.com/app/apikey",
                onValueChange = { viewModel.updateKey("gemini", it) },
                onValidate = { viewModel.validateKey("gemini") }
            )

            KeyFieldCard(
                title = "Groq Cloud API Key",
                subtitle = "Powers sub-300ms GPT OSS 120B & Whisper Turbo",
                value = uiState.groqKey,
                status = uiState.validationStatus["groq"],
                getKeyUrl = "https://console.groq.com/keys",
                onValueChange = { viewModel.updateKey("groq", it) },
                onValidate = { viewModel.validateKey("groq") }
            )

            KeyFieldCard(
                title = "OpenAI API Key",
                subtitle = "Direct gateway to GPT-4o, GPT-4o-mini & Whisper",
                value = uiState.openAiKey,
                status = uiState.validationStatus["openai"],
                getKeyUrl = "https://platform.openai.com/api-keys",
                onValueChange = { viewModel.updateKey("openai", it) },
                onValidate = { viewModel.validateKey("openai") }
            )

            KeyFieldCard(
                title = "OpenRouter API Key",
                subtitle = "Unified access to Claude 3.5 Sonnet, Gemini 2.5 Flash, DeepSeek R1",
                value = uiState.openRouterKey,
                status = uiState.validationStatus["openrouter"],
                getKeyUrl = "https://openrouter.ai/keys",
                onValueChange = { viewModel.updateKey("openrouter", it) },
                onValidate = { viewModel.validateKey("openrouter") }
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun KeyFieldCard(
    title: String,
    subtitle: String,
    value: String,
    status: KeyValidationStatus?,
    getKeyUrl: String? = null,
    onValueChange: (String) -> Unit,
    onValidate: () -> Unit
) {
    var isRevealed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPureWhite
                )

                if (getKeyUrl != null) {
                    Text(
                        text = "Console ↗",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { uriHandler.openUri(getKeyUrl) }
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSlateMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste API key…", fontSize = 12.sp, color = TextSlateDark) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPureWhite
                ),
                singleLine = true,
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HairlineHighlight,
                    unfocusedBorderColor = HairlineBorder,
                    focusedContainerColor = ObsidianVoid,
                    unfocusedContainerColor = ObsidianVoid
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isBlank()) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clipText.isNullOrBlank()) onValueChange(clipText)
                            }) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = TextSlateMedium, modifier = Modifier.size(17.dp))
                            }
                        }
                        IconButton(onClick = { isRevealed = !isRevealed }) {
                            Icon(
                                imageVector = if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isRevealed) "Hide" else "Show",
                                tint = TextSlateMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onValidate,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSlateMedium),
                    border = BorderStroke(1.dp, HairlineBorder),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    enabled = value.isNotBlank() && status !is KeyValidationStatus.Checking
                ) {
                    if (status is KeyValidationStatus.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = TextPureWhite)
                        Spacer(Modifier.width(6.dp))
                        Text("Connecting…", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 11.sp)
                    }
                }

                if (status != null) {
                    when (status) {
                        is KeyValidationStatus.Valid -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = status.message,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is KeyValidationStatus.Invalid -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = AccentRose,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = status.reason.take(28),
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentRose,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

package com.techrifter.fluxvoice.ui.screen.voice.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.ui.theme.AccentBlue
import com.techrifter.fluxvoice.ui.theme.FluxAmber
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.FluxRose
import com.techrifter.fluxvoice.ui.theme.FluxSunsetOrange
import com.techrifter.fluxvoice.ui.theme.FluxViolet
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
fun QuickGuideSheet(
    onDismiss: () -> Unit,
    onGotIt: () -> Unit = onDismiss,
    onOpenKeyVault: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenCatalog: () -> Unit = {},
    allPipelines: List<PipelineConfig> = emptyList(),
    selectedPipelineId: String = "",
    onSelectPipeline: (PipelineConfig) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .background(HairlineBorder, CircleShape)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Header ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "QUICK START & USER GUIDE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluxCyan,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "How FluxVoice Works",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPureWhite
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextSlateMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A high-performance voice AI console. Speak naturally, interrupt anytime, and switch models in 1 click.",
                    fontSize = 12.5.sp,
                    color = TextSlateMedium,
                    lineHeight = 17.sp
                )
            }

            // ── 2. The 3-Step Flow ────────────────────────────────────────────
            item {
                GuideSectionHeader(title = "GET STARTED IN 3 STEPS")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepCard(
                        step = "1",
                        badge = "KEY VAULT",
                        badgeColor = FluxAmber,
                        title = "Add a Free API Key (30 Seconds)",
                        description = "Google AI Studio and Groq provide 100% free developer keys without a credit card. Add them into the hardware-encrypted Key Vault.",
                        actionLabel = "Open Key Vault ↗",
                        onAction = {
                            onDismiss()
                            onOpenKeyVault()
                        }
                    )

                    StepCard(
                        step = "2",
                        badge = "REAL-TIME",
                        badgeColor = FluxCyan,
                        title = "Tap the Voice Visualizer & Speak",
                        description = "Tap the glowing screen or the microphone button. Start talking naturally: speech is converted to text as you talk, with instant low-latency turnaround."
                    )

                    StepCard(
                        step = "3",
                        badge = "AUTO BARGE-IN",
                        badgeColor = FluxRose,
                        title = "Interrupt Anytime You Want",
                        description = "Don't wait for the assistant to finish. Speak over it whenever you want: FluxVoice's acoustic detector instantly stops speech playback and listens."
                    )
                }
            }

            // ── 3. Quick-Switch Presets ───────────────────────────────────────
            if (allPipelines.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    GuideSectionHeader(title = "ONE-CLICK READY PRESETS")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allPipelines.forEach { pipeline ->
                            val isSelected = pipeline.id == selectedPipelineId
                            PresetGuideCard(
                                pipeline = pipeline,
                                isSelected = isSelected,
                                onSelect = {
                                    onSelectPipeline(pipeline)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            // ── 4. Free Tiers Cheat Sheet ─────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                GuideSectionHeader(title = "FREE TIERS CHEAT SHEET")
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreeProviderItem(
                        icon = Icons.Rounded.Psychology,
                        name = "Google Gemini (Google AI Studio)",
                        quota = "15 RPM • 1M Tokens/min Free",
                        recommendation = "Gemini 2.5 Flash for deep multimodal analysis, structured answers, and complex queries ($0 balance required).",
                        color = FluxAzure
                    )
                    FreeProviderItem(
                        icon = Icons.Rounded.Bolt,
                        name = "Groq Cloud (LPU Accelerated)",
                        quota = "<250ms Response • Free Tier",
                        recommendation = "High-speed reasoning via openai/gpt-oss-120b and qwen/qwen3.8-27b on Groq LPUs, plus free Groq Whisper Turbo STT.",
                        color = FluxSunsetOrange
                    )
                    FreeProviderItem(
                        icon = Icons.Rounded.GraphicEq,
                        name = "Deepgram Nova-3 STT",
                        quota = "\$200 Free Credit (~45,000 mins)",
                        recommendation = "Ultra-low latency streaming WebSocket speech recognition (<150ms) with automated speech endpointing.",
                        color = FluxEmerald
                    )
                    FreeProviderItem(
                        icon = Icons.Rounded.RecordVoiceOver,
                        name = "Cartesia & ElevenLabs Voice",
                        quota = "Sonic 3.6 & Neural Voices",
                        recommendation = "Sonic 3.6 delivers lifelike voice turnaround with sentence-level streaming. Or use 100% offline Android TTS with zero keys.",
                        color = FluxViolet
                    )
                    FreeProviderItem(
                        icon = Icons.Rounded.Key,
                        name = "Universal BYOK (STT · LLM · TTS)",
                        quota = "OpenAI-Compatible & Local",
                        recommendation = "Bring your own keys and custom endpoints across all 3 pipeline stages: Whisper-compatible STT, chat completions, and speech synthesis.",
                        color = FluxCyan
                    )
                }
            }

            // ── 5. Understanding the Visualizer ─────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                GuideSectionHeader(title = "THE AUDIO VISUALIZER & TELEMETRY")
                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FluxAzure))
                            Text("Electric Cyan Wave:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPureWhite)
                            Text("Your vocal harmonics (Mic / STT)", fontSize = 12.sp, color = TextSlateMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FluxSunsetOrange))
                            Text("Sunset Amber Wave:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPureWhite)
                            Text("AI voice synthesis (TTS Output)", fontSize = 12.sp, color = TextSlateMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FluxViolet))
                            Text("Violet Lissajous Loop:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPureWhite)
                            Text("Active reasoning & generation", fontSize = 12.sp, color = TextSlateMedium)
                        }
                        HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                        Text(
                            text = "Telemetry HUD: Tap the mini metrics pill to inspect TTFT (Time to First Token), TTFA (Time to First Audio), and total roundtrip response speed.",
                            fontSize = 11.5.sp,
                            color = TextSlateMedium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── 6. Footer Navigation Actions ─────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenStudio()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluxCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Architecture", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenCatalog()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(14.dp), tint = FluxCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("All Models", fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = onGotIt,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianFloat),
                        border = BorderStroke(1.dp, HairlineHighlight),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Got It", fontSize = 11.5.sp, color = TextPureWhite)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = FluxCyan
    )
}

@Composable
private fun StepCard(
    step: String,
    badge: String,
    badgeColor: Color,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ObsidianVoid),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPureWhite
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPureWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObsidianVoid,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badge,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = TextSlateMedium,
                    lineHeight = 16.sp
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = actionLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluxCyan,
                        modifier = Modifier.clickable { onAction() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetGuideCard(
    pipeline: PipelineConfig,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ObsidianVoid else ObsidianCard,
        border = BorderStroke(1.dp, if (isSelected) HairlineHighlight else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = pipeline.name,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) FluxCyan else TextPureWhite
                    )
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF06281E),
                            border = BorderStroke(1.dp, FluxEmerald)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = FluxEmerald,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pipeline.description,
                    fontSize = 10.5.sp,
                    color = TextSlateMuted,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) FluxCyan else ObsidianFloat,
                border = BorderStroke(1.dp, if (isSelected) FluxCyan else HairlineBorder)
            ) {
                Text(
                    text = if (isSelected) "Selected" else "Select",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) ObsidianVoid else TextPureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FreeProviderItem(
    icon: ImageVector,
    name: String,
    quota: String,
    recommendation: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPureWhite
                    )
                    Text(
                        text = quota,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluxEmerald
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = recommendation,
                    fontSize = 11.sp,
                    color = TextSlateMedium,
                    lineHeight = 15.sp
                )
            }
        }
    }
}


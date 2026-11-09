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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrifter.fluxvoice.core.model.TurnMetrics
import com.techrifter.fluxvoice.ui.theme.AccentBlue
import com.techrifter.fluxvoice.ui.theme.AccentGreen
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianFloat
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkHud(
    metrics: TurnMetrics?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (metrics == null) return

    var showDetailSheet by remember { mutableStateOf(false) }

    val tokenPrefix = if (metrics.isTokensEstimated) "Est. " else ""

    // ── Quiet, Exquisite Micro Badge ─────────────────────────────────────────
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { showDetailSheet = true },
        shape = RoundedCornerShape(20.dp),
        color = ObsidianFloat.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(FluxCyan, CircleShape)
            )

            if (metrics.ttftMs > 0) {
                Text(
                    text = "${metrics.ttftMs}ms TTFT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FluxCyan
                )

                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = TextSlateMuted
                )
            }

            if (metrics.totalTurnLatencyMs > 0) {
                Text(
                    text = "${metrics.totalTurnLatencyMs}ms total",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSlateMedium
                )

                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = TextSlateMuted
                )
            }

            Text(
                text = "${tokenPrefix}${metrics.totalTokens} tokens",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSlateMedium
            )
        }
    }

    // ── Clean Studio Execution Telemetry Drawer ──────────────────────────────
    if (showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TURN EXECUTION WATERFALL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Latency & Resource Telemetry",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPureWhite
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showDetailSheet = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSlateMedium)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Stage Breakdown Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LatencyStageRow(
                            label = "Speech Transcription (STT)",
                            latencyMs = metrics.sttLatencyMs,
                            percentage = "Stage 1"
                        )
                        HorizontalDivider(color = HairlineBorder)
                        LatencyStageRow(
                            label = "First Token Generation (TTFT)",
                            latencyMs = metrics.ttftMs,
                            percentage = "Stage 2"
                        )
                        HorizontalDivider(color = HairlineBorder)
                        LatencyStageRow(
                            label = "Audio Synthesis Dispatch (TTFA)",
                            latencyMs = metrics.ttfaMs,
                            percentage = "Stage 3"
                        )
                        HorizontalDivider(color = HairlineBorder)
                        LatencyStageRow(
                            label = "Complete Roundtrip Latency",
                            latencyMs = metrics.totalTurnLatencyMs,
                            percentage = "Total",
                            isTotal = true
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Token & Throughput Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioMetricBox(
                        title = if (metrics.isTokensEstimated) "PROMPT / COMPLETION (EST.)" else "PROMPT / COMPLETION",
                        value = "${tokenPrefix}${metrics.promptTokens} in / ${tokenPrefix}${metrics.completionTokens} out",
                        modifier = Modifier.weight(1f)
                    )
                    StudioMetricBox(
                        title = if (metrics.isTokensEstimated) "TOTAL TOKENS (EST.)" else "TOTAL TOKENS",
                        value = "${tokenPrefix}${metrics.totalTokens} tokens",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LatencyStageRow(
    label: String,
    latencyMs: Long,
    percentage: String,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = if (isTotal) 13.sp else 12.sp,
                fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isTotal) TextPureWhite else TextSlateMedium
            )
        }
        Text(
            text = if (latencyMs > 0) "${latencyMs}ms" else "—",
            fontFamily = FontFamily.Monospace,
            fontSize = if (isTotal) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                latencyMs > 0 && isTotal -> FluxCyan
                latencyMs > 0            -> TextPureWhite
                else                     -> TextSlateMuted
            }
        )
    }
}

@Composable
private fun StudioMetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSlateMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPureWhite
            )
        }
    }
}

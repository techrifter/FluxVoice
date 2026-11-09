package com.techrifter.fluxvoice.ui.screen.benchmarks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkEntity
import com.techrifter.fluxvoice.ui.theme.AccentBlue
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.FluxRose
import com.techrifter.fluxvoice.ui.theme.FluxViolet
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.ObsidianVoid
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkHistoryScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                            text = "TELEMETRY & LOGS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Turn Benchmarks",
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
                    if (uiState.benchmarks.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAllBenchmarks) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear History", tint = TextSlateMuted)
                        }
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
                .padding(horizontal = 20.dp)
        ) {
            // Aggregate Telemetry Summary
            AnalyticsOverviewCard(summary = uiState.summary)

            Spacer(Modifier.height(20.dp))

            Text(
                text = "EXECUTION TURNS (${uiState.benchmarks.size})",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSlateMuted,
                letterSpacing = 1.0.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (uiState.benchmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(ObsidianSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = TextSlateDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No benchmarks recorded yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSlateMedium
                        )
                        Text(
                            text = "Run conversational turns in playground to capture latency",
                            fontSize = 12.sp,
                            color = TextSlateMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.benchmarks, key = { it.id }) { item ->
                        TurnLogItem(item)
                    }
                    item {
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsOverviewCard(summary: BenchmarkSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CUMULATIVE PERFORMANCE METRICS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSlateMuted,
                letterSpacing = 1.0.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "AVG TTFT", value = if (summary.avgTtftMs > 0) "${summary.avgTtftMs}ms" else "—")
                MetricColumn(label = "AVG TTFA", value = if (summary.avgTtfaMs > 0) "${summary.avgTtfaMs}ms" else "—")
                MetricColumn(label = "ROUNDTRIP", value = if (summary.avgTotalMs > 0) "%.2fs".format(summary.avgTotalMs / 1000.0) else "—")
                MetricColumn(label = "TOTAL TOKENS", value = "${summary.totalTokens}")
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TextSlateDark,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = FluxCyan
        )
    }
}

@Composable
private fun TurnLogItem(item: TurnBenchmarkEntity) {
    val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    val tokenPrefix = if (item.isTokensEstimated) "Est. " else ""
    val tokenStr = "${tokenPrefix}${item.totalTokens} tokens"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.pipelineName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPureWhite
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSlateDark
                    )
                }

                Text(
                    text = tokenStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSlateMedium
                )
            }

            // Architecture Stack Badges: STT · LLM · TTS
            val sttLabel = when (item.sttProvider.lowercase()) {
                "deepgram" -> "Nova-3"
                "groq", "groq_whisper" -> "Whisper Turbo"
                "whisper" -> "Whisper"
                "custom" -> "BYOK STT"
                else -> item.sttProvider.ifBlank { "Deepgram" }.uppercase()
            }
            val llmLabel = item.llmModel.ifBlank { "openai/gpt-oss-120b" }
                .substringAfterLast('/')
                .take(18)
                .uppercase()
            val ttsLabel = when (item.ttsProvider.lowercase()) {
                "cartesia" -> "Sonic 3.6"
                "elevenlabs" -> "ElevenLabs"
                "openai" -> "OpenAI"
                "deepgram", "deepgram_aura" -> "Aura"
                "android" -> "Android TTS"
                "custom" -> "BYOK TTS"
                else -> item.ttsProvider.ifBlank { "Cartesia" }.uppercase()
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BenchmarkStageBadge(label = "STT", value = sttLabel, accentColor = FluxAzure)
                BenchmarkStageBadge(label = "LLM", value = llmLabel, accentColor = FluxEmerald)
                BenchmarkStageBadge(label = "TTS", value = ttsLabel, accentColor = FluxViolet)
                if (item.bargeInOccurred) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObsidianVoid,
                        border = BorderStroke(1.dp, FluxRose.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "BARGE-IN",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluxRose,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Only display stages that actually executed
            val latencyParts = buildList {
                if (item.sttLatencyMs > 0) add("STT ${item.sttLatencyMs}ms")
                if (item.ttftMs > 0) add("TTFT ${item.ttftMs}ms")
                if (item.ttfaMs > 0) add("TTFA ${item.ttfaMs}ms")
                if (item.totalTurnMs > 0) add("TOTAL ${"%.2f".format(item.totalTurnMs / 1000.0)}s")
            }

            Text(
                text = if (latencyParts.isNotEmpty()) latencyParts.joinToString("  ·  ") else "No latency recorded",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSlateDark,
                letterSpacing = 0.4.sp
            )

            if (item.userTranscriptionSnippet.isNotBlank() || item.assistantResponseSnippet.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                if (item.userTranscriptionSnippet.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "You:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluxAzure
                        )
                        Text(
                            text = item.userTranscriptionSnippet,
                            fontSize = 11.5.sp,
                            color = TextSlateMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (item.assistantResponseSnippet.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "AI:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluxEmerald
                        )
                        Text(
                            text = item.assistantResponseSnippet,
                            fontSize = 11.5.sp,
                            color = TextPureWhite,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkStageBadge(
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = ObsidianVoid,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TextSlateMedium
            )
        }
    }
}

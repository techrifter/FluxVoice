package com.techrifter.fluxvoice.ui.screen.voice.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrifter.fluxvoice.domain.model.PipelineConfig
import com.techrifter.fluxvoice.ui.theme.FluxAzure
import com.techrifter.fluxvoice.ui.theme.FluxCyan
import com.techrifter.fluxvoice.ui.theme.FluxEmerald
import com.techrifter.fluxvoice.ui.theme.HairlineBorder
import com.techrifter.fluxvoice.ui.theme.HairlineHighlight
import com.techrifter.fluxvoice.ui.theme.ObsidianCard
import com.techrifter.fluxvoice.ui.theme.ObsidianSurface
import com.techrifter.fluxvoice.ui.theme.TextPureWhite
import com.techrifter.fluxvoice.ui.theme.TextSlateDark
import com.techrifter.fluxvoice.ui.theme.TextSlateMedium
import com.techrifter.fluxvoice.ui.theme.TextSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineSelectorSheet(
    pipelines: List<PipelineConfig>,
    selectedPipelineId: String,
    onSelectPipeline: (PipelineConfig) -> Unit,
    onAddNewPipeline: () -> Unit,
    onOpenStudio: () -> Unit,
    onEditPipeline: ((PipelineConfig) -> Unit)? = null,
    onDeletePipeline: (PipelineConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pipelineToDelete by remember { mutableStateOf<PipelineConfig?>(null) }

    if (pipelineToDelete != null) {
        val target = pipelineToDelete!!
        AlertDialog(
            onDismissRequest = { pipelineToDelete = null },
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
                    text = "Are you sure you want to delete '${target.name}'? This action cannot be undone.",
                    color = TextSlateMedium,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target
                        pipelineToDelete = null
                        onDeletePipeline(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = TextPureWhite, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pipelineToDelete = null },
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VOICE RUNTIME PIPELINES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSlateMuted,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Active Voice Model Stack",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPureWhite
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                onAddNewPipeline()
                            },
                        shape = CircleShape,
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Stack",
                                modifier = Modifier.size(18.dp),
                                tint = FluxCyan
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenStudio()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                        border = BorderStroke(1.dp, HairlineBorder),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSlateMedium)
                        Spacer(Modifier.width(5.dp))
                        Text("Studio", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HairlineBorder)
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pipelines, key = { it.id }) { pipeline ->
                    val isSelected = pipeline.id == selectedPipelineId
                    PipelineItemRow(
                        pipeline = pipeline,
                        isSelected = isSelected,
                        onClick = {
                            onSelectPipeline(pipeline)
                            onDismiss()
                        },
                        onEdit = if (!pipeline.isPreset && onEditPipeline != null) {
                            {
                                onDismiss()
                                onEditPipeline(pipeline)
                            }
                        } else null,
                        onDelete = if (!pipeline.isPreset) {
                            {
                                pipelineToDelete = pipeline
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineItemRow(
    pipeline: PipelineConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ObsidianCard else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) HairlineHighlight else HairlineBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pipeline.name,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = TextPureWhite
                    )
                    if (pipeline.isPreset) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PRESET",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CUSTOM",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluxEmerald,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = pipeline.description,
                    fontSize = 12.sp,
                    color = TextSlateMedium,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Clean stack metadata
                Text(
                    text = "${pipeline.sttProviderId.uppercase()} · ${pipeline.llmModelId.substringAfterLast('/').uppercase()} · ${pipeline.ttsProviderId.uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSlateDark,
                    letterSpacing = 0.4.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Edit Stack",
                            tint = TextSlateMedium,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Stack",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isSelected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = if (pipeline.isPreset) FluxAzure else FluxEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

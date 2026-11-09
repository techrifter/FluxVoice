package com.techrifter.fluxvoice.ui.screen.catalog

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techrifter.fluxvoice.ui.theme.AccentBlue
import com.techrifter.fluxvoice.ui.theme.FluxCyan
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
fun ModelCatalogScreen(
    onBack: () -> Unit,
    onOpenStudio: (modelId: String, provider: String, category: String) -> Unit,
    viewModel: ModelCatalogViewModel = hiltViewModel()
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
                            text = "DIRECTORY & PROVIDERS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateMuted,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Model Catalog",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPureWhite
                            )
                            if (uiState.totalModelsCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, HairlineHighlight)
                                ) {
                                    Text(
                                        text = "${uiState.totalModelsCount} Models",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FluxCyan,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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
                .padding(horizontal = 20.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search models by provider or name…", fontSize = 12.sp, color = TextSlateDark) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = TextSlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HairlineHighlight,
                    unfocusedBorderColor = HairlineBorder,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                )
            )

            Spacer(Modifier.height(12.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelCategory.entries.forEach { category ->
                    val isSelected = category == uiState.selectedCategory
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ObsidianFloat else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) HairlineHighlight else HairlineBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectCategory(category) }
                    ) {
                        Text(
                            text = category.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) FluxCyan else TextSlateMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Total available & filtered status line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.searchQuery.isNotBlank() || uiState.selectedCategory != ModelCategory.ALL) {
                        "Showing ${uiState.models.size} of ${uiState.totalModelsCount} models"
                    } else {
                        "Total ${uiState.totalModelsCount} models available"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSlateMedium
                )
                if (uiState.searchQuery.isNotBlank() || uiState.selectedCategory != ModelCategory.ALL) {
                    Text(
                        text = "Filtered",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluxCyan
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Model Catalog List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.models, key = { it.id }) { item ->
                    ModelCatalogCard(
                        item = item,
                        onConfigureInStudio = onOpenStudio
                    )
                }
                item {
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelCatalogCard(
    item: ModelCatalogItem,
    onConfigureInStudio: (modelId: String, provider: String, category: String) -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPureWhite
                    )
                    Text(
                        text = item.provider,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSlateMedium
                    )
                }

                // Latency Tier Monospace Badge
                Text(
                    text = item.latencyTier,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = TextSlateMedium,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(10.dp))

            // Pricing & Studio Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.pricing,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSlateDark
                )

                OutlinedButton(
                    onClick = { onConfigureInStudio(item.id, item.provider, item.category.name) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPureWhite),
                    border = BorderStroke(1.dp, HairlineBorder),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextSlateMedium)
                    Spacer(Modifier.width(4.dp))
                    Text("In Studio", fontSize = 11.sp)
                }
            }
        }
    }
}

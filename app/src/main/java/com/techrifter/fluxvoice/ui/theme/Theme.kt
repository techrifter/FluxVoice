package com.techrifter.fluxvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FluxDarkScheme = darkColorScheme(
    primary = FluxAzure,
    onPrimary = ObsidianVoid,
    primaryContainer = ObsidianCard,
    onPrimaryContainer = FluxAzure,

    secondary = FluxAzure,
    onSecondary = ObsidianVoid,
    secondaryContainer = ObsidianCard,
    onSecondaryContainer = FluxAzure,

    tertiary = FluxAzure,
    onTertiary = ObsidianVoid,
    tertiaryContainer = ObsidianCard,
    onTertiaryContainer = TextPureWhite,

    background = ObsidianVoid,
    onBackground = TextPureWhite,

    surface = ObsidianSurface,
    onSurface = TextPureWhite,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSlateMedium,

    outline = HairlineBorder,
    outlineVariant = HairlineBorder,

    error = FluxRose,
    onError = ObsidianVoid,
    errorContainer = ObsidianCard,
    onErrorContainer = FluxRose
)

@Composable
fun FluxVoiceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FluxDarkScheme,
        typography = Typography,
        content = content
    )
}

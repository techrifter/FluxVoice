package com.techrifter.fluxvoice.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FluxDarkColorScheme = darkColorScheme(
    background    = Color(0xFF080C12),
    surface       = Color(0xFF0F1520),
    primary       = Color(0xFF3B82F6),
    secondary     = Color(0xFF10B981),
    tertiary      = Color(0xFF8B5CF6),
    error         = Color(0xFFEF4444),
    surfaceVariant = Color(0xFF1E2535),
)

private val FluxLightColorScheme = lightColorScheme(
    background    = Color(0xFFF8FAFC),
    surface       = Color(0xFFFFFFFF),
    primary       = Color(0xFF2563EB),
    secondary     = Color(0xFF059669),
    tertiary      = Color(0xFF7C3AED),
    error         = Color(0xFFDC2626),
    surfaceVariant = Color(0xFFE2E8F0),
)

/**
 * Ready-to-use theme for [FluxVoiceScreen] and [FluxVoiceView].
 *
 * - On Android 12+ (API 31) uses Material You dynamic colours from the device wallpaper.
 * - On older devices falls back to FluxVoice's own dark / light palette.
 * - Follows the system dark/light setting by default.
 *
 * ```kotlin
 * // Recommended — respects system theme and Material You automatically
 * FluxVoiceTheme {
 *     FluxVoiceScreen(controller = controller)
 * }
 *
 * // Force dark regardless of system setting
 * FluxVoiceTheme(darkTheme = true) {
 *     FluxVoiceScreen(controller = controller)
 * }
 *
 * // Disable dynamic colours — always use FluxVoice static palette
 * FluxVoiceTheme(dynamicColor = false) {
 *     FluxVoiceScreen(controller = controller)
 * }
 * ```
 *
 * @param darkTheme     Follow system setting by default. Override to force dark or light.
 * @param dynamicColor  Use Material You wallpaper colours on Android 12+. Default `true`.
 */
@Composable
public fun FluxVoiceTheme(
    darkTheme: Boolean    = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> FluxDarkColorScheme
        else      -> FluxLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}

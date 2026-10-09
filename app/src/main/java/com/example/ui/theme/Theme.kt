package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SignalCyan,
    onPrimary = CyberNavyDark,
    primaryContainer = CyberSurfaceElevated,
    onPrimaryContainer = SignalCyan,
    secondary = SignalAmber,
    onSecondary = CyberNavyDark,
    secondaryContainer = CyberSurfaceVariantDark,
    onSecondaryContainer = SignalAmber,
    tertiary = SignalGreen,
    onTertiary = CyberNavyDark,
    background = CyberNavyDark,
    onBackground = TextPrimaryDark,
    surface = CyberSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CyberSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = TextMutedDark,
    error = SignalRed,
    onError = TextPrimaryDark
)

// Force dark tactical theme for high-tech telemetry and screen contrast
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

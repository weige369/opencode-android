package com.opencode.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OpenCode-inspired dark color palette
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF003D7A),
    primaryContainer = Color(0xFF004B8D),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002D6E),
    secondaryContainer = Color(0xFF003B8A),
    onSecondaryContainer = Color(0xFFDBE1FF),
    tertiary = Color(0xFF7FD1FF),
    onTertiary = Color(0xFF003549),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF8E9099),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A6EF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
)

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

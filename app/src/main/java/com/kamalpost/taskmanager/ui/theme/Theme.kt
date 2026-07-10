package com.kamalpost.taskmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    secondary = AccentGreen,
    onSecondary = Bg,
    tertiary = AccentPurple,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = AccentRed,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2
)

@Composable
fun TaskManagerTheme(content: @Composable () -> Unit) {
    // The web app is dark-only by design; the Android port keeps that identity.
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}

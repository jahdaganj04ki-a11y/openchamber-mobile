package com.openchamber.android.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The palette of the app, including the colors Material 3 has no slot for. */
val LocalOpenChamberColors = staticCompositionLocalOf { DarkColors }

/** The user's choice of light or dark, independent of the system setting. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}

private fun OpenChamberColors.toColorScheme(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = primaryHover,
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = subtle,
            onSurfaceVariant = mutedForeground,
            surfaceContainer = elevated,
            surfaceContainerHigh = elevated,
            outline = border,
            outlineVariant = borderHover,
            error = error,
            onError = Color.White,
            secondary = mutedForeground,
            onSecondary = background,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.16f),
            onPrimaryContainer = primaryHover,
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = muted,
            onSurfaceVariant = mutedForeground,
            surfaceContainer = elevated,
            surfaceContainerHigh = elevated,
            outline = border,
            outlineVariant = borderHover,
            error = error,
            onError = Color.White,
            secondary = mutedForeground,
            onSecondary = background,
        )
    }

/**
 * The app's theme.
 *
 * The Material 3 scheme is built from the shared OpenChamber palette, and the
 * palette itself stays reachable so the surfaces that need the syntax or the
 * markdown colors can read them.
 */
@Composable
fun OpenChamberTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.resolve(isSystemInDarkTheme())
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalOpenChamberColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(dark),
            typography = OpenChamberTypography,
            content = content,
        )
    }
}

/** The palette of the current theme. */
object OpenChamberTheme {
    val colors: OpenChamberColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOpenChamberColors.current
}

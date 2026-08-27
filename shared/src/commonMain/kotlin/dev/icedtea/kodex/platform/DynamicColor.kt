package dev.icedtea.kodex.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Material You (Monet) dynamic colour, wallpaper-derived. Available only on Android 12+; every other
 * platform returns null so [dev.icedtea.kodex.ui.theme.KodexTheme] falls back to the selected palette.
 */
@Composable
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

/** Whether this device can produce a dynamic colour scheme (Android 12+ / API 31+). */
expect fun isDynamicColorSupported(): Boolean

package app.kodex.client.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null

actual fun isDynamicColorSupported(): Boolean = false

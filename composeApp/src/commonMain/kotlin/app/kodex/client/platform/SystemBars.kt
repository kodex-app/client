package app.kodex.client.platform

import androidx.compose.runtime.Composable

/**
 * Sets the system status-bar icon appearance for the current screen. [darkIcons] = true draws dark
 * icons (for light backgrounds); false draws light/white icons (for dark backgrounds). The previous
 * value is restored when the caller leaves composition. No-op off Android.
 */
@Composable
expect fun StatusBarIcons(darkIcons: Boolean)

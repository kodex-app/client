package app.kodex.client.platform

import androidx.compose.runtime.Composable

/**
 * Sets the system status-bar icon appearance for the current screen. [darkIcons] = true draws dark
 * icons (for light backgrounds); false draws light/white icons (for dark backgrounds). The previous
 * value is restored when the caller leaves composition. No-op off Android.
 *
 * [navDarkIcons] does the same for the navigation bar, defaulting to [darkIcons] — full-screen
 * surfaces cover both bars, but a strip that only covers the status bar (the incognito banner) needs
 * to flip that one alone.
 */
@Composable
expect fun StatusBarIcons(darkIcons: Boolean, navDarkIcons: Boolean = darkIcons)

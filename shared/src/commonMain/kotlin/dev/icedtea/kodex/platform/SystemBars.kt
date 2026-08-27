package dev.icedtea.kodex.platform

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

/**
 * Hides the status and navigation bars while [hidden] is true. The readers pass their own chrome
 * flag, so the system bars come and go with the reader's bars instead of floating over the page.
 * The bars still slide in transiently on a swipe from either edge, and are restored for good when
 * the caller leaves composition. No-op off Android.
 */
@Composable
expect fun SystemBarsHidden(hidden: Boolean)

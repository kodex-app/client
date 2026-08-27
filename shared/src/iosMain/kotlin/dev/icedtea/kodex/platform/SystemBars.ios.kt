package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

@Composable
actual fun StatusBarIcons(darkIcons: Boolean, navDarkIcons: Boolean) {
    // TODO(iOS): drive status-bar style via the hosting UIViewController's preferredStatusBarStyle.
}

@Composable
actual fun SystemBarsHidden(hidden: Boolean) {
    // TODO(iOS): drive the hosting UIViewController's prefersStatusBarHidden / home-indicator
    //  auto-hide from this flag.
}

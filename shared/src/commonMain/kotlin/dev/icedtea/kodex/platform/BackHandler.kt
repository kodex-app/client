package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform "back" gesture/button when [enabled], invoking [onBack] instead of the
 * default (which on Android would finish the activity). Wired to the in-app back stack so system
 * back navigates within the app. No-op on iOS (no global back button).
 */
@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)

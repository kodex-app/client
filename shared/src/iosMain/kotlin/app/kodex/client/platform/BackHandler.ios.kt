package app.kodex.client.platform

import androidx.compose.runtime.Composable

@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses edge-swipe within its own nav; no global back button to intercept here.
}

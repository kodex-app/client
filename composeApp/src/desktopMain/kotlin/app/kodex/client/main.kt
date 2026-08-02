package app.kodex.client

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.kodex.client.platform.disposeWebEngine

/** Desktop entry point — primarily a fast dev/preview harness for the shared UI on Windows/macOS/Linux. */
fun main() = application {
    Window(
        onCloseRequest = {
            // Chromium (if the ebook reader ever started it) holds native threads that keep the JVM
            // alive after the window closes.
            disposeWebEngine()
            exitApplication()
        },
        title = "Kodex",
        state = rememberWindowState(width = 420.dp, height = 860.dp),
    ) {
        App()
    }
}

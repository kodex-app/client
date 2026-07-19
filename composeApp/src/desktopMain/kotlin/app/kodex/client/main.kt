package app.kodex.client

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop entry point — primarily a fast dev/preview harness for the shared UI on Windows/macOS/Linux. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kodex",
        state = rememberWindowState(width = 420.dp, height = 860.dp),
    ) {
        App()
    }
}

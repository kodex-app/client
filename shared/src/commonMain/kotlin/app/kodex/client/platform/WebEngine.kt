package app.kodex.client.platform

/** How far along [ensureWebEngine] is. Only desktop ever reports anything but [Ready]. */
sealed interface WebEngineState {
    data object Ready : WebEngineState

    /** Fetching the engine; [progress] is 0–1, or null when the size isn't known yet. */
    data class Preparing(val progress: Float?) : WebEngineState

    data class Failed(val message: String) : WebEngineState

    /** The engine was installed but the process must restart before it can be used. */
    data object RestartRequired : WebEngineState
}

/**
 * Makes the platform's web engine usable, which the ebook reader needs before it can render.
 *
 * Android and iOS have a system WebView and return [WebEngineState.Ready] immediately. Desktop
 * embeds Chromium (KCEF), which downloads a bundle on first use — hence the progress reporting, so
 * that download is something the reader can show rather than a silent stall.
 */
expect suspend fun ensureWebEngine(onState: (WebEngineState) -> Unit)

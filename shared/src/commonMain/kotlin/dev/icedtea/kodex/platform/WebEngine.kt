package dev.icedtea.kodex.platform

/**
 * How far along [ensureWebEngine] is.
 *
 * Both surviving platforms report [Ready] at once, so the other states are currently unreachable —
 * they existed for the dropped desktop target, which downloaded Chromium on first use. Kept because
 * an embedded-engine platform is the normal reason to need this gate at all, and because the states
 * cost nothing while the reader is already written against them.
 */
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
 * Android and iOS both have a system WebView, so both actuals return [WebEngineState.Ready] without
 * doing any work. The suspend-and-report shape is what an embedded engine needs (the dropped desktop
 * target fetched Chromium here), and it keeps the reader from mounting a WebView before asking.
 */
expect suspend fun ensureWebEngine(onState: (WebEngineState) -> Unit)

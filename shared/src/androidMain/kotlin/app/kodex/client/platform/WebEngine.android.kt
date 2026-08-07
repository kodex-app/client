package app.kodex.client.platform

/** The system WebView is always available here — nothing to install. */
actual suspend fun ensureWebEngine(onState: (WebEngineState) -> Unit) = onState(WebEngineState.Ready)

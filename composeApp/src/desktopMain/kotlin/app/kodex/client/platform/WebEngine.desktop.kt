package app.kodex.client.platform

import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val initMutex = Mutex()
private var initialized = false

/**
 * Where Chromium is unpacked. Deliberately under the user's home rather than the working directory:
 * Gradle runs the desktop app with the CWD inside the checkout, so a relative path drops ~250 MB of
 * native binaries into the repo — which is exactly how they once ended up staged for commit.
 */
private val kcefHome: File
    get() = File(System.getProperty("user.home"), ".kodex/kcef")

/**
 * Desktop has no system WebView, so the reader runs on embedded Chromium (KCEF). The bundle is
 * fetched on first use into [kcefHome] and reused thereafter — roughly 250 MB unpacked, which is why
 * this is driven on demand from the reader (with progress) instead of at startup: nothing else in
 * the app needs a browser.
 */
actual suspend fun ensureWebEngine(onState: (WebEngineState) -> Unit) {
    if (initialized) return onState(WebEngineState.Ready)
    initMutex.withLock {
        if (initialized) return onState(WebEngineState.Ready)
        onState(WebEngineState.Preparing(null))
        withContext(Dispatchers.IO) {
            runCatching {
                KCEF.init(
                    builder = {
                        installDir(File(kcefHome, "bundle"))
                        progress {
                            onDownloading { onState(WebEngineState.Preparing((it / 100f).coerceIn(0f, 1f))) }
                            onInitialized { onState(WebEngineState.Ready) }
                        }
                        settings { cachePath = File(kcefHome, "cache").absolutePath }
                    },
                    onError = { onState(WebEngineState.Failed(it?.message ?: "Couldn't start the web engine.")) },
                    onRestartRequired = { onState(WebEngineState.RestartRequired) },
                )
            }.onFailure {
                onState(WebEngineState.Failed(it.message ?: "Couldn't start the web engine."))
                return@withContext
            }
            initialized = true
        }
    }
}

/** Releases Chromium at shutdown; skipped entirely when the reader was never opened. */
fun disposeWebEngine() {
    if (initialized) runCatching { KCEF.disposeBlocking() }
}

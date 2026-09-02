package dev.icedtea.kodex.ui

import androidx.compose.material3.SnackbarHostState
import dev.icedtea.kodex.auth.SessionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A tiny app-wide snackbar controller. One [SnackbarHostState] is hosted at the app root (over the
 * content), so any screen — even one inside its own `Scaffold` — can post feedback via [LocalSnackbar]
 * without threading a host through every call site. Replaces the earlier fire-and-forget actions.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message)
        }
    }
}

val LocalSnackbar = compositionLocalOf<SnackbarController?> { null }

/** Convenience accessor for screens that want to post snackbars. */
@Composable
fun rememberSnackbar(): SnackbarController? = LocalSnackbar.current

/**
 * Run a settings write on the session's own scope, telling the user if it fails.
 *
 * Both halves matter. A write started on the composition's scope is cancelled the moment that screen
 * goes away — and changing a setting and immediately leaving is the ordinary way to use one, so the
 * change was lost exactly when it was most deliberate. And these writes are otherwise fire-and-forget:
 * a swallowed failure leaves the control showing the new value while the server never heard of it,
 * which is what let several save bugs sit unnoticed.
 */
fun SessionManager.persistSetting(
    snackbar: SnackbarController?,
    failureMessage: String = "Couldn't save settings.",
    block: suspend () -> Unit,
) {
    persistDetached {
        runCatching { block() }.onFailure { snackbar?.show(failureMessage) }
    }
}

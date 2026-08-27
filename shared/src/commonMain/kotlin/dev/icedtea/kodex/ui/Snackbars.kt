package dev.icedtea.kodex.ui

import androidx.compose.material3.SnackbarHostState
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

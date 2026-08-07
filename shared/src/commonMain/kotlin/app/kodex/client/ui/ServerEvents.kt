package app.kodex.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import app.kodex.client.network.EventBus
import app.kodex.client.network.ServerEvent

/** The app-wide live [EventBus], provided at the root so any screen can react without prop-drilling. */
val LocalEventBus = compositionLocalOf<EventBus?> { null }

/**
 * Runs [onEvent] whenever the server emits one of [names] (any event if [names] is empty). Screens
 * use it to auto-refresh from SSE — e.g. Downloads reacts to `DownloadStatusChanged`. The latest
 * [onEvent] is always used, so capturing screen state is safe.
 */
@Composable
fun OnServerEvent(vararg names: String, onEvent: (ServerEvent) -> Unit) {
    val bus = LocalEventBus.current ?: return
    val handler = rememberUpdatedState(onEvent)
    val filter = names.toSet()
    LaunchedEffect(bus, filter) {
        bus.events.collect { event ->
            if (filter.isEmpty() || event.name in filter) handler.value(event)
        }
    }
}

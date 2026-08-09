package app.kodex.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kodex.client.ui.nav.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
}

/** What one [LoadedContent] has loaded, kept separately so it can be retained across navigation. */
private class LoadHolder {
    val state = mutableStateOf<LoadState<Any?>>(LoadState.Loading)
    val reload = mutableStateOf(0)

    /** The [key] the current [state] belongs to; a different key means the content is stale. */
    var loadedKey: Any? = null
    var everLoaded = false
}

/**
 * Runs [load] when [key] changes and renders loading / error+retry / [content]. Shared by the simple
 * one-shot screens (Libraries, a library's series, Browse's source list).
 */
@Composable
fun <T> LoadedContent(
    key: Any?,
    load: suspend () -> T,
    modifier: Modifier = Modifier,
    /**
     * Opt in to surviving this screen being covered by another (the reader, a detail screen). With it
     * set, coming back keeps what was on screen and refreshes it in place rather than falling back to
     * the spinner — so read state updates without the list flashing away and losing its scroll
     * position. The string only has to be unique within the screen. See `nav/RetainedState.kt`.
     */
    retainKey: String? = null,
    content: @Composable (T) -> Unit,
) {
    val holder = retain(retainKey) { LoadHolder() }
    var reload by holder.reload

    LaunchedEffect(key, reload) {
        // A changed key means genuinely different data, so drop what is shown. The same key arriving
        // again is this screen being re-entered — refresh underneath what's already there.
        if (holder.loadedKey != key || !holder.everLoaded || holder.state.value !is LoadState.Ready<*>) {
            holder.state.value = LoadState.Loading
        }
        holder.loadedKey = key
        holder.state.value = runCatching { load() }.fold(
            onSuccess = { holder.everLoaded = true; LoadState.Ready(it) },
            onFailure = { LoadState.Error(it.friendlyMessage()) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    when (val s = holder.state.value as LoadState<T>) {
        is LoadState.Loading ->
            Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        is LoadState.Error ->
            Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s.message,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { reload++ }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                }
            }

        is LoadState.Ready -> content(s.data)
    }
}

/** Centered muted message for empty states. */
@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

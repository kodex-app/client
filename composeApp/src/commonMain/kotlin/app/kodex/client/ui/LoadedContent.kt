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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
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
    content: @Composable (T) -> Unit,
) {
    var reload by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }

    LaunchedEffect(key, reload) {
        state = LoadState.Loading
        state = runCatching { load() }.fold(
            onSuccess = { LoadState.Ready(it) },
            onFailure = { LoadState.Error(it.friendlyMessage()) },
        )
    }

    when (val s = state) {
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

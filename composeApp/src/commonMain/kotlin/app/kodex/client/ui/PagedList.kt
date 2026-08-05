package app.kodex.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.network.PageResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Holds the state for an infinitely-scrolling, page-fetched list: the accumulated [items], the
 * current phase, and the plumbing to [refresh] (reset to page 0) or [loadMore] (append the next
 * page). One [fetch] returns a [PageResponse]; the helper stops once [PageResponse.last] is true.
 *
 * The reusable Phase-0 enabler behind Updates, History, and Downloads.
 */
class PagedListState<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> PageResponse<T>,
) {
    val items = emptyList<T>().toMutableStateList()

    var loading by mutableStateOf(false)
        private set
    var appending by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set

    private var nextPage by mutableIntStateOf(0)
    private var loadedOnce = false

    val isEmpty: Boolean get() = items.isEmpty()

    /** First load for a screen (no-op if already loaded — survives recomposition). */
    fun start() {
        if (loadedOnce) return
        loadedOnce = true
        reload(initial = true)
    }

    /** Pull-to-refresh: reset to the first page, keeping current items on screen until it arrives. */
    fun refresh() {
        refreshing = true
        reload(initial = false)
    }

    /** Background poll: silently re-fetch the first page in place (no spinner). Used by Downloads. */
    fun silentRefresh() {
        if (loading || refreshing) return
        reload(initial = false)
    }

    private fun reload(initial: Boolean) {
        // Set synchronously, before launching: loadMore() checks these flags, and flipping them inside
        // the coroutine left a window where it saw an idle state and paged in parallel with page 0.
        if (initial) loading = true
        scope.launch {
            error = null
            runCatching { fetch(0) }.fold(
                onSuccess = { pageResp ->
                    items.clear()
                    items.addAll(pageResp.content)
                    endReached = pageResp.last
                    nextPage = 1
                },
                onFailure = { if (initial) error = it.friendlyMessage() },
            )
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        if (appending || loading || refreshing || endReached) return
        // Nothing to page past yet. Without this, an early trigger (the near-end check fires on an
        // empty list) would fetch page 0 a second time and duplicate every row.
        if (!loadedOnce || nextPage == 0 || items.isEmpty()) return
        appending = true
        scope.launch {
            runCatching { fetch(nextPage) }.fold(
                onSuccess = { pageResp ->
                    items.addAll(pageResp.content)
                    endReached = pageResp.last
                    nextPage += 1
                },
                onFailure = { /* keep what we have; a later scroll retries */ },
            )
            appending = false
        }
    }

    /** Remove matching items locally (e.g. after a "clear history" or "cancel" action). */
    fun removeIf(predicate: (T) -> Boolean) {
        items.removeAll(predicate)
    }
}

@Composable
fun <T> rememberPagedList(key: Any?, fetch: suspend (page: Int) -> PageResponse<T>): PagedListState<T> {
    val scope = rememberCoroutineScope()
    val state = remember(key) { PagedListState(scope, fetch) }
    LaunchedEffect(state) { state.start() }
    return state
}

/**
 * Renders a [PagedListState]: loading spinner / error+retry / empty message on the first page, then a
 * pull-to-refresh [LazyColumn] that appends pages as the user nears the end. [itemsContent] builds the
 * rows (and any sticky day headers) from the current snapshot of items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PagedList(
    state: PagedListState<T>,
    emptyText: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    itemsContent: LazyListScope.(List<T>) -> Unit,
) {
    // Trigger loadMore when the user scrolls within a few items of the end.
    LaunchedEffect(listState, state) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // `total > 0` matters: on an empty list this read as "near the end" (0 >= -4) and asked
            // for another page before the first had even arrived.
            total > 0 && last >= total - 4
        }.distinctUntilChanged().collect { near -> if (near) state.loadMore() }
    }

    when {
        state.loading && state.isEmpty ->
            Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.isEmpty ->
            Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { state.refresh() }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                }
            }

        state.isEmpty ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { state.refresh() }, modifier = modifier.fillMaxSize()) {
                // Keep it scrollable so pull-to-refresh works even when empty.
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Box(Modifier.fillMaxWidth().padding(top = 120.dp), Alignment.Center) { EmptyMessageInline(emptyText) } }
                }
            }

        else ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { state.refresh() }, modifier = modifier.fillMaxSize()) {
                LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                    itemsContent(state.items)
                    if (state.appending) {
                        item("paging-spinner") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun EmptyMessageInline(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium,
    )
}

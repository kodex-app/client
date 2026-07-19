package app.kodex.client.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage

/**
 * A content source's browse feed — Popular / Latest (Latest only if the source supports it) — as an
 * infinite-scroll cover grid. Pages are appended as the user nears the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceFeedScreen(
    session: SessionManager,
    api: KodexApi,
    source: SourceDescriptor,
    onBack: () -> Unit,
    onOpenSourceSeries: (SourceSearchResult) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    var feed by remember(source.id) { mutableStateOf("popular") }

    val items = remember(source.id) { mutableStateListOf<SourceSearchResult>() }
    var page by remember(source.id) { mutableStateOf(0) }
    var hasNext by remember(source.id) { mutableStateOf(true) }
    var loading by remember(source.id) { mutableStateOf(false) }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(source.id) { mutableStateOf(0) }
    val gridState = rememberLazyGridState()

    suspend fun loadNext() {
        val s = server ?: return
        if (loading || !hasNext) return
        loading = true
        error = null
        val next = page + 1
        runCatching { api.sourceFeed(s.baseUrl, s.apiKey, source.id, feed, next) }
            .onSuccess {
                items.addAll(it.items)
                page = next
                hasNext = it.hasNextPage
            }
            .onFailure {
                error = it.friendlyMessage()
                hasNext = false // stop auto-paging; user can retry
            }
        loading = false
    }

    // (Re)load from scratch when the source or the selected feed changes (or on retry).
    LaunchedEffect(source.id, feed, reloadKey) {
        items.clear()
        page = 0
        hasNext = true
        error = null
        loadNext()
    }

    // Infinite scroll: fetch the next page as the last row approaches.
    LaunchedEffect(gridState, feed) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (items.isNotEmpty() && hasNext && !loading && last >= items.size - 8) loadNext()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source.displayName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (source.supportsLatest) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = feed == "popular", onClick = { feed = "popular" }, label = { Text("Popular") })
                    FilterChip(selected = feed == "latest", onClick = { feed = "latest" }, label = { Text("Latest") })
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    items.isEmpty() && loading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    items.isEmpty() && error != null ->
                        RetryBox(error!!) { reloadKey++ }

                    items.isEmpty() ->
                        EmptyMessage("Nothing to show here.")

                    else -> FeedGrid(
                        baseUrl = server?.baseUrl ?: "",
                        apiKey = server?.apiKey ?: "",
                        sourceId = source.id,
                        items = items,
                        gridState = gridState,
                        loadingMore = loading,
                        onOpen = onOpenSourceSeries,
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun FeedGrid(
    baseUrl: String,
    apiKey: String,
    sourceId: String,
    items: List<SourceSearchResult>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    loadingMore: Boolean,
    onOpen: (SourceSearchResult) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items, key = { i, it -> "$i-${it.externalId}" }) { _, item ->
            CoverCard(
                coverUrl = sourceCoverUrl(baseUrl, item.providerId ?: sourceId, item.coverUrl),
                apiKey = apiKey,
                title = item.title,
                subtitle = item.author,
                unread = null,
                onClick = { onOpen(item) },
                width = null,
            )
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
    }
}

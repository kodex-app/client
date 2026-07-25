package app.kodex.client.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.CheckBoxFilter
import app.kodex.client.network.FilterListDto
import app.kodex.client.network.GroupFilter
import app.kodex.client.network.HeaderFilter
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SelectFilter
import app.kodex.client.network.SeparatorFilter
import app.kodex.client.network.SortFilter
import app.kodex.client.network.SortSelection
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceFilter
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.network.TextFilterDto
import app.kodex.client.network.TriStateFilter
import app.kodex.client.network.SeriesPage
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * A content source: browse feed (Popular / Latest), full-text Search, and the source's own filters.
 * Results stream into an infinite-scroll cover grid; searching or applying filters reloads from page 1.
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
    val scope = rememberCoroutineScope()
    val snackbar = rememberSnackbar()

    var feed by remember(source.id) { mutableStateOf("popular") }
    var searchOpen by remember(source.id) { mutableStateOf(false) }
    var query by remember(source.id) { mutableStateOf("") }
    var searching by remember(source.id) { mutableStateOf(false) }
    var appliedFilters by remember(source.id) { mutableStateOf(FilterListDto()) }
    var searchToken by remember(source.id) { mutableIntStateOf(0) }
    var filterSheetOpen by remember(source.id) { mutableStateOf(false) }
    var loadedFilters by remember(source.id) { mutableStateOf<List<SourceFilter>?>(null) }

    val items = remember(source.id) { mutableStateListOf<SourceSearchResult>() }
    var page by remember(source.id) { mutableStateOf(0) }
    var hasNext by remember(source.id) { mutableStateOf(true) }
    var loading by remember(source.id) { mutableStateOf(false) }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(source.id) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    suspend fun fetch(next: Int): SeriesPage {
        val s = server!!
        return if (searching) api.sourceSearch(s.baseUrl, s.apiKey, source.id, query, next, appliedFilters)
        else api.sourceFeed(s.baseUrl, s.apiKey, source.id, feed, next)
    }

    suspend fun loadNext() {
        server ?: return
        if (loading || !hasNext) return
        loading = true
        error = null
        val next = page + 1
        runCatching { fetch(next) }
            .onSuccess { items.addAll(it.items); page = next; hasNext = it.hasNextPage }
            .onFailure { error = it.friendlyMessage(); hasNext = false }
        loading = false
    }

    // Reload from scratch when the mode changes (feed / search submit / filter apply / retry).
    LaunchedEffect(source.id, feed, searching, searchToken, reloadKey) {
        items.clear(); page = 0; hasNext = true; error = null
        loadNext()
    }

    LaunchedEffect(gridState, feed, searching, searchToken) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (items.isNotEmpty() && hasNext && !loading && last >= items.size - 8) loadNext() }
    }

    fun openFilters() {
        filterSheetOpen = true
        if (loadedFilters == null) {
            val s = server ?: return
            scope.launch {
                runCatching { api.sourceFilters(s.baseUrl, s.apiKey, source.id) }
                    .onSuccess { loadedFilters = it.filters }
                    .onFailure { snackbar?.show("This source has no filters.") }
            }
        }
    }

    fun submitSearch() {
        searching = query.isNotBlank() || appliedFilters.filters.isNotEmpty()
        searchToken++
    }

    fun clearSearch() {
        searchOpen = false; query = ""; searching = false; appliedFilters = FilterListDto(); searchToken++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search ${source.displayName}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        )
                    } else {
                        Text(source.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (searchOpen) clearSearch() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchOpen) {
                        IconButton(onClick = { if (query.isBlank()) clearSearch() else { query = ""; } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!searching) {
                    FilterChip(selected = feed == "popular", onClick = { feed = "popular" }, label = { Text("Popular") })
                    if (source.supportsLatest) {
                        FilterChip(selected = feed == "latest", onClick = { feed = "latest" }, label = { Text("Latest") })
                    }
                } else {
                    FilterChip(
                        selected = true,
                        onClick = { clearSearch() },
                        label = { Text(if (query.isNotBlank()) "\"$query\"  ✕" else "Filtered  ✕") },
                    )
                }
                val filterCount = appliedFilters.filters.count { it.isActive() }
                FilterChip(
                    selected = filterCount > 0,
                    onClick = { openFilters() },
                    label = { Text(if (filterCount > 0) "Filters ($filterCount)" else "Filters") },
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    items.isEmpty() && error != null -> RetryBox(error!!) { reloadKey++ }
                    items.isEmpty() -> EmptyMessage(if (searching) "No results." else "Nothing to show here.")
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

    if (filterSheetOpen) {
        FilterSheet(
            filters = loadedFilters,
            onDismiss = { filterSheetOpen = false },
            onReset = { loadedFilters = loadedFilters?.map { it.reset() } },
            onApply = { edited ->
                filterSheetOpen = false
                loadedFilters = edited
                appliedFilters = FilterListDto(edited)
                submitSearch()
            },
        )
    }
}

// ── Filter sheet ─────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    filters: List<SourceFilter>?,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (List<SourceFilter>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (filters == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        val working = remember(filters) { filters.toMutableStateList() }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            working.forEachIndexed { i, f ->
                FilterControl(f) { working[i] = it }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(onClick = { onApply(working.toList()) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun FilterControl(filter: SourceFilter, onChange: (SourceFilter) -> Unit) {
    when (filter) {
        is HeaderFilter -> Text(
            filter.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        is SeparatorFilter -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
        is TextFilterDto -> OutlinedTextField(
            value = filter.state,
            onValueChange = { onChange(filter.copy(state = it)) },
            label = { Text(filter.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        is CheckBoxFilter -> Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = { onChange(filter.copy(state = it)) })
            Text(filter.name, style = MaterialTheme.typography.bodyMedium)
        }
        is TriStateFilter -> Row(
            Modifier.fillMaxWidth()
                .padding(vertical = 6.dp)
                .then(Modifier)
                .clickableRow { onChange(filter.copy(state = (filter.state + 1) % 3)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(triGlyph(filter.state), modifier = Modifier.size(24.dp).padding(end = 4.dp), color = triColor(filter.state))
            Text(filter.name, style = MaterialTheme.typography.bodyMedium)
        }
        is SelectFilter -> DropdownRow(
            label = filter.name,
            current = filter.values.getOrNull(filter.state) ?: "",
            options = filter.values,
            onSelect = { idx -> onChange(filter.copy(state = idx)) },
        )
        is SortFilter -> {
            val sel = filter.state ?: SortSelection(0, false)
            DropdownRow(
                label = filter.name,
                current = (filter.values.getOrNull(sel.index) ?: "") + if (sel.ascending) " ↑" else " ↓",
                options = filter.values,
                onSelect = { idx ->
                    val ascending = if (idx == sel.index) !sel.ascending else false
                    onChange(filter.copy(state = SortSelection(idx, ascending)))
                },
            )
        }
        is GroupFilter -> {
            Text(filter.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            filter.state.forEachIndexed { ci, child ->
                FilterControl(child) { updated ->
                    val newChildren = filter.state.toMutableList().also { it[ci] = updated }
                    onChange(filter.copy(state = newChildren))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(label: String, current: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clickableRow { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(current.ifBlank { "Any" }, maxLines = 1) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { idx, opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; onSelect(idx) })
                }
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun triGlyph(state: Int): String = when (state) {
    TriStateFilter.INCLUDE -> "✓"
    TriStateFilter.EXCLUDE -> "✕"
    else -> "▢"
}

@Composable
private fun triColor(state: Int) = when (state) {
    TriStateFilter.INCLUDE -> MaterialTheme.colorScheme.primary
    TriStateFilter.EXCLUDE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// Whether a filter carries a non-default (active) value — for the "Filters (N)" badge.
private fun SourceFilter.isActive(): Boolean = when (this) {
    is TextFilterDto -> state.isNotBlank()
    is CheckBoxFilter -> state
    is TriStateFilter -> state != 0
    is SelectFilter -> state != 0
    is SortFilter -> state != null && state.index != 0
    is GroupFilter -> state.any { it.isActive() }
    else -> false
}

private fun SourceFilter.reset(): SourceFilter = when (this) {
    is TextFilterDto -> copy(state = "")
    is CheckBoxFilter -> copy(state = false)
    is TriStateFilter -> copy(state = 0)
    is SelectFilter -> copy(state = 0)
    is SortFilter -> copy(state = null)
    is GroupFilter -> copy(state = state.map { it.reset() })
    else -> this
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

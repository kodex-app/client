package app.kodex.client.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.kodex.client.network.LibraryDto
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
import app.kodex.client.ui.SelectionState
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSelection
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
    initialFeed: String = "popular",
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    val snackbar = rememberSnackbar()
    val openUrl = app.kodex.client.platform.rememberUrlOpener()

    var feed by remember(source.id) { mutableStateOf(initialFeed) }
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

    // Multi-select (long-press) + "Add to libraries". Selection is keyed by the item's external id.
    val selection = rememberSelection<String>()
    var libraryPicker by remember(source.id) { mutableStateOf<List<LibraryDto>?>(null) }
    var addingBusy by remember(source.id) { mutableStateOf(false) }

    // External ids already followed into one of the user's libraries → "in library" marks on the grid.
    var followedIds by remember(source.id) { mutableStateOf<Set<String>>(emptySet()) }
    var followedReload by remember(source.id) { mutableIntStateOf(0) }
    LaunchedEffect(source.id, server?.id, followedReload) {
        val s = server ?: return@LaunchedEffect
        followedIds = runCatching { api.followedExternalIds(s.baseUrl, s.apiKey, source.id).toSet() }.getOrDefault(emptySet())
    }

    // System back exits selection mode first (before leaving the screen).
    app.kodex.client.platform.AppBackHandler(enabled = selection.active) { selection.clear() }

    // Follow every selected source series into [libraryId] (add to that WEB library).
    fun addSelectedTo(libraryId: String) {
        val s = server ?: return
        val chosen = items.filter { it.externalId in selection.selected }
        if (chosen.isEmpty()) return
        libraryPicker = null
        addingBusy = true
        scope.launch {
            var ok = 0
            chosen.forEach { it2 ->
                runCatching { api.followWebSeries(s.baseUrl, s.apiKey, libraryId, it2.providerId ?: source.id, it2.externalId) }
                    .onSuccess { ok++ }
            }
            addingBusy = false
            followedReload++ // refresh the "in library" marks
            snackbar?.show(if (ok == chosen.size) "Added $ok to library" else "Added $ok of ${chosen.size} (some already exist)")
            selection.clear()
        }
    }

    // Resolve which WEB library to add to: pick directly when there's one, else show a picker.
    fun onAddToLibraries() {
        val s = server ?: return
        scope.launch {
            val webLibs = runCatching { api.libraries(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()).filter { it.isWeb }
            when {
                webLibs.size == 1 -> addSelectedTo(webLibs.first().id)
                webLibs.isEmpty() -> runCatching { api.webLibrary(s.baseUrl, s.apiKey) }
                    .fold({ addSelectedTo(it.id) }, { snackbar?.show("No web library to add to.") })
                else -> libraryPicker = webLibs
            }
        }
    }

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
            if (selection.active) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    title = { Text("${selection.count} selected", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { selection.clear() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
                    },
                    actions = {
                        Tip("Select all") {
                            IconButton(onClick = { selection.selectAll(items.map { it.externalId }) }) {
                                Icon(app.kodex.client.ui.icons.SelectAllIcon, contentDescription = "Select all")
                            }
                        }
                        Tip("Add the selected series to a library") {
                            TextButton(onClick = { onAddToLibraries() }, enabled = !addingBusy) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add to library", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    },
                )
            } else {
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
                        Tip("Clear") {
                            IconButton(onClick = { if (query.isBlank()) clearSearch() else { query = ""; } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    } else {
                        // Open the source's own site. Only offered when the plugin declares one —
                        // `website` is optional in the SPI, so a blank value means "no homepage".
                        source.website?.takeIf { it.isNotBlank() }?.let { site ->
                            Tip("Source website") {
                                IconButton(onClick = { openUrl(site) }) {
                                    Icon(app.kodex.client.ui.icons.OpenInWebIcon, contentDescription = "Source website")
                                }
                            }
                        }
                        Tip("Search") {
                            IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                        }
                    }
                },
            )
            }
        },
        floatingActionButton = {
            val filterCount = appliedFilters.filters.count { it.isActive() }
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { openFilters() },
                icon = { Icon(app.kodex.client.ui.icons.FilterIcon, contentDescription = null) },
                text = { Text(if (filterCount > 0) "Filter ($filterCount)" else "Filter") },
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
                        selection = selection,
                        followedIds = followedIds,
                        onOpen = onOpenSourceSeries,
                    )
                }
            }
        }
    }

    // Library picker for "Add to libraries" when the user has more than one WEB library.
    libraryPicker?.let { libs ->
        ModalBottomSheet(onDismissRequest = { libraryPicker = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Add ${selection.count} to library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                )
                libs.forEach { lib ->
                    Text(
                        lib.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { addSelectedTo(lib.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (filterSheetOpen) {
        FilterSheet(
            filters = loadedFilters,
            initialQuery = query,
            onDismiss = { filterSheetOpen = false },
            onReset = { loadedFilters = loadedFilters?.map { it.reset() } },
            onApply = { edited, q ->
                filterSheetOpen = false
                loadedFilters = edited
                appliedFilters = FilterListDto(edited)
                query = q
                submitSearch()
            },
        )
    }
}

// ── Filter sheet ─────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: List<SourceFilter>?,
    initialQuery: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (List<SourceFilter>, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (filters == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        val working = remember(filters) { filters.toMutableStateList() }
        var query by remember(initialQuery) { mutableStateOf(initialQuery) }

        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp)) {
            Text("Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            // A search box that applies together with the filters below.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search this source") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Active filters shown as removable badges (a quick view of what's applied).
            val activeChips = working.toList().filterIndexed { _, f -> f.isActive() }
            if (activeChips.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    working.forEachIndexed { i, f ->
                        if (f.isActive()) {
                            androidx.compose.material3.InputChip(
                                selected = true,
                                onClick = { working[i] = f.reset() },
                                label = { Text(f.name, maxLines = 1) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }

            // Scrollable filter body — capped so the footer stays visible in the half-height sheet.
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                working.forEachIndexed { i, f ->
                    FilterControl(f) { working[i] = it }
                }
            }

            // Fixed footer.
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { query = ""; onReset() }, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(onClick = { onApply(working.toList(), query) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
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

/** Wraps an action with a plain tooltip shown on long-press / hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

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
    selection: SelectionState<String>,
    followedIds: Set<String>,
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
                onClick = { if (selection.active) selection.toggle(item.externalId) else onOpen(item) },
                onLongClick = { selection.toggle(item.externalId) },
                selected = selection.isSelected(item.externalId),
                inLibrary = item.externalId in followedIds,
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

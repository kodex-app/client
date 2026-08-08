package app.kodex.client.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import kotlinx.coroutines.launch
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LabelDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.bookSubtitle
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.catalog.seriesSubtitle
import app.kodex.client.ui.catalog.seriesUnreadBadge
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Selected search facets. Empty = unfiltered. */
private data class Facets(
    val genres: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val readingStatuses: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val labelIds: Set<String> = emptySet(),
) {
    val count: Int get() = genres.size + statuses.size + readingStatuses.size + languages.size + tags.size + labelIds.size
    val isEmpty: Boolean get() = count == 0
}

/** Facet vocabulary fetched from the server (for the filter sheet). */
private data class FacetVocab(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val labels: List<LabelDto> = emptyList(),
)

private sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Ready(val series: List<SeriesDto>, val books: List<BookDto>) : SearchUiState
}

/** Online (source) search: results grouped per content source. */
private data class SourceResults(val source: SourceDescriptor, val items: List<SourceSearchResult>, val error: Boolean)

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Ready(val perSource: List<SourceResults>) : OnlineState
}

private val SERIES_STATUSES = listOf("ONGOING", "COMPLETED", "PUBLISHING_FINISHED", "LICENSED", "CANCELLED", "ON_HIATUS", "UNKNOWN")
private val READING_STATUSES = listOf("NOT_STARTED" to "Unread", "IN_PROGRESS" to "In progress", "COMPLETED" to "Read")

/**
 * Full-screen global search — the mobile form of the web's library search + facet filters. A debounced
 * field queries series (with facets) + books in parallel; a filter sheet narrows by genre, status,
 * reading status, language, tag, and label. With facets set, the query can be empty (browse by facet).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    session: SessionManager,
    api: KodexApi,
    onClose: () -> Unit,
    onOpenSeries: (SeriesDto) -> Unit = {},
    onOpenBook: (BookDto) -> Unit = {},
    onOpenSourceSeries: (SourceDescriptor, SourceSearchResult) -> Unit = { _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    var query by remember { mutableStateOf("") }
    // Library/Online is a two-page pager so the modes can be swiped between as well as tapped;
    // `online` stays derived from it, keeping one source of truth for the search effect below.
    val modePager = androidx.compose.foundation.pager.rememberPagerState(0) { 2 }
    val modeScope = androidx.compose.runtime.rememberCoroutineScope()
    val online = modePager.currentPage == 1 // false = library (local), true = sources (online)
    var facets by remember { mutableStateOf(Facets()) }
    var state by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sourceSheetOpen by remember { mutableStateOf(false) }
    var vocab by remember { mutableStateOf<FacetVocab?>(null) }
    // Online mode: installed sources + which to search (empty = all) + per-source results.
    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var selectedSources by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onlineState by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
    val focusRequester = remember { FocusRequester() }

    // Load the installed content sources once per server (for the online-mode source picker + search).
    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        sources = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
    }

    // Online search: fan out the query across the selected sources (or all) and group results per source.
    LaunchedEffect(query, selectedSources, online, server?.id, sources) {
        if (!online) return@LaunchedEffect
        val current = server ?: return@LaunchedEffect
        val text = query.trim()
        if (text.isEmpty()) { onlineState = OnlineState.Idle; return@LaunchedEffect }
        val targets = if (selectedSources.isEmpty()) sources else sources.filter { it.id in selectedSources }
        if (targets.isEmpty()) { onlineState = OnlineState.Ready(emptyList()); return@LaunchedEffect }
        delay(350)
        onlineState = OnlineState.Loading
        val perSource = coroutineScope {
            targets.map { src ->
                async {
                    runCatching { api.sourceSearch(current.baseUrl, current.apiKey, src.id, text, 1) }
                        .fold({ SourceResults(src, it.items, error = false) }, { SourceResults(src, emptyList(), error = true) })
                }
            }.map { it.await() }
        }
        onlineState = OnlineState.Ready(perSource)
    }

    LaunchedEffect(query, facets, server?.id) {
        val current = server ?: return@LaunchedEffect
        val text = query.trim()
        if (text.isEmpty() && facets.isEmpty) {
            state = SearchUiState.Idle
            return@LaunchedEffect
        }
        delay(350)
        state = SearchUiState.Loading
        val results = coroutineScope {
            val series = async {
                runCatching {
                    api.querySeries(
                        current.baseUrl, current.apiKey,
                        search = text.ifBlank { null },
                        genres = facets.genres.toList(),
                        statuses = facets.statuses.toList(),
                        readingStatuses = facets.readingStatuses.toList(),
                        languages = facets.languages.toList(),
                        tags = facets.tags.toList(),
                        labelIds = facets.labelIds.toList(),
                    )
                }
            }
            // Books have no facets; only search them when there's a query.
            val books = async { if (text.isBlank()) Result.success(emptyList()) else runCatching { api.searchBooks(current.baseUrl, current.apiKey, text) } }
            series.await() to books.await()
        }
        state = if (results.first.isFailure && results.second.isFailure) {
            SearchUiState.Error("Search failed. Check your connection.")
        } else {
            SearchUiState.Ready(results.first.getOrDefault(emptyList()), results.second.getOrDefault(emptyList()))
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    // Load vocab when the sheet first opens.
    LaunchedEffect(sheetOpen) {
        if (sheetOpen && vocab == null) {
            val s = server ?: return@LaunchedEffect
            val v = coroutineScope {
                val g = async { runCatching { api.seriesGenres(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) }
                val t = async { runCatching { api.seriesTags(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) }
                val l = async { runCatching { api.seriesLanguages(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) }
                val lb = async { runCatching { api.labels(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) }
                FacetVocab(g.await(), t.await(), l.await(), lb.await())
            }
            vocab = v
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(if (online) "Search all sources" else "Search your library") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            val label = if (online) {
                if (selectedSources.isNotEmpty()) "Sources (${selectedSources.size})" else "Sources"
            } else {
                if (facets.count > 0) "Filters (${facets.count})" else "Filters"
            }
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { if (online) sourceSheetOpen = true else sheetOpen = true },
                icon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                text = { Text(label) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Local (library) vs Online (installed sources) mode.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(selected = !online, onClick = { modeScope.launch { modePager.animateScrollToPage(0) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Library") }
                SegmentedButton(selected = online, onClick = { modeScope.launch { modePager.animateScrollToPage(1) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Online") }
            }
            androidx.compose.foundation.pager.HorizontalPager(state = modePager, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 1) {
                    when (val os = onlineState) {
                        is OnlineState.Idle ->
                            if (sources.isEmpty()) Hint("No content sources installed. Install one from Browse → Extensions.")
                            else Hint("Search across your installed sources.")
                        is OnlineState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        is OnlineState.Ready ->
                            if (os.perSource.all { it.items.isEmpty() }) Hint("No results.")
                            else OnlineResults(server?.baseUrl ?: "", server?.apiKey ?: "", os.perSource, onOpenSourceSeries)
                    }
                } else {
                    when (val s = state) {
                        is SearchUiState.Idle -> Hint("Search series and books, or tap Filters to browse by genre, status, and more.")
                        is SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        is SearchUiState.Error -> Hint(s.message)
                        is SearchUiState.Ready ->
                            if (s.series.isEmpty() && s.books.isEmpty()) Hint("No results.")
                            else Results(server?.baseUrl ?: "", server?.apiKey ?: "", s, onOpenSeries, onOpenBook)
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        FacetSheet(
            vocab = vocab,
            facets = facets,
            onDismiss = { sheetOpen = false },
            onClear = { facets = Facets() },
            onApply = { facets = it; sheetOpen = false },
        )
    }
    if (sourceSheetOpen) {
        SourcePickerSheet(
            sources = sources,
            selected = selectedSources,
            onDismiss = { sourceSheetOpen = false },
            onClear = { selectedSources = emptySet() },
            onApply = { selectedSources = it; sourceSheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FacetSheet(
    vocab: FacetVocab?,
    facets: Facets,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (Facets) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var working by remember(facets) { mutableStateOf(facets) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            // Scrollable filter body — capped so the fixed footer stays visible in the half-height sheet.
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {

            FacetGroup("Reading status", READING_STATUSES.map { it.first }, working.readingStatuses,
                labelOf = { v -> READING_STATUSES.first { it.first == v }.second },
                onToggle = { working = working.copy(readingStatuses = working.readingStatuses.toggle(it)) })

            FacetGroup("Status", SERIES_STATUSES, working.statuses,
                labelOf = { it.titleCase() },
                onToggle = { working = working.copy(statuses = working.statuses.toggle(it)) })

            if (vocab == null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            } else {
                if (vocab.genres.isNotEmpty()) FacetGroup("Genre", vocab.genres, working.genres,
                    onToggle = { working = working.copy(genres = working.genres.toggle(it)) })
                if (vocab.languages.isNotEmpty()) FacetGroup("Language", vocab.languages, working.languages,
                    labelOf = { it.uppercase() },
                    onToggle = { working = working.copy(languages = working.languages.toggle(it)) })
                if (vocab.tags.isNotEmpty()) FacetGroup("Tag", vocab.tags.take(60), working.tags,
                    onToggle = { working = working.copy(tags = working.tags.toggle(it)) })
                if (vocab.labels.isNotEmpty()) FacetGroup("Label", vocab.labels.map { it.id }, working.labelIds,
                    labelOf = { id -> vocab.labels.first { it.id == id }.name },
                    onToggle = { working = working.copy(labelIds = working.labelIds.toggle(it)) })
            }
            } // ── end scrollable body; footer below stays fixed ──

            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { working = Facets(); onClear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(onClick = { onApply(working) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetGroup(
    title: String,
    values: List<String>,
    selected: Set<String>,
    labelOf: (String) -> String = { it },
    onToggle: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { v ->
            FilterChip(selected = v in selected, onClick = { onToggle(v) }, label = { Text(labelOf(v)) })
        }
    }
}

private fun Set<String>.toggle(v: String): Set<String> = if (v in this) this - v else this + v
private fun String.titleCase(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun Results(
    baseUrl: String,
    apiKey: String,
    ready: SearchUiState.Ready,
    onOpenSeries: (SeriesDto) -> Unit,
    onOpenBook: (BookDto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (ready.series.isNotEmpty()) {
            header("Series", ready.series.size)
            items(ready.series, key = { "s-${it.id}" }) { series ->
                CoverCard(
                    coverUrl = seriesCoverUrl(baseUrl, series),
                    apiKey = apiKey,
                    title = series.title,
                    subtitle = seriesSubtitle(series),
                    unread = seriesUnreadBadge(series),
                    onClick = { onOpenSeries(series) },
                    width = null,
                )
            }
        }
        if (ready.books.isNotEmpty()) {
            header("Books", ready.books.size)
            items(ready.books, key = { "b-${it.id}" }) { book ->
                CoverCard(
                    coverUrl = bookCoverUrl(baseUrl, book.id),
                    apiKey = apiKey,
                    title = book.title,
                    subtitle = bookSubtitle(book),
                    unread = null,
                    onClick = { onOpenBook(book) },
                    width = null,
                )
            }
        }
    }
}

/** Online-mode results: one horizontal row of covers per content source (Mihon-style). */
@Composable
private fun OnlineResults(
    baseUrl: String,
    apiKey: String,
    perSource: List<SourceResults>,
    onOpen: (SourceDescriptor, SourceSearchResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(perSource, key = { it.source.id }) { sr ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    sr.source.displayName.ifBlank { sr.source.id },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                when {
                    sr.error -> RowHint("Couldn't search this source.")
                    sr.items.isEmpty() -> RowHint("No results.")
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sr.items, key = { it.externalId }) { item ->
                            CoverCard(
                                coverUrl = sourceCoverUrl(baseUrl, item.providerId ?: sr.source.id, item.coverUrl),
                                apiKey = apiKey,
                                title = item.title,
                                subtitle = item.author,
                                unread = null,
                                onClick = { onOpen(sr.source, item) },
                                width = 112.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Online-mode filter: pick which installed sources to search (none selected = all). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SourcePickerSheet(
    sources: List<SourceDescriptor>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var working by remember(selected) { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            Text("None selected searches every source.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (sources.isEmpty()) {
                    Text("No sources installed.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = src.id in working,
                            onClick = { working = working.toggle(src.id) },
                            label = { Text(src.displayName.ifBlank { src.id }) },
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { working = emptySet(); onClear() }, modifier = Modifier.weight(1f)) { Text("All") }
                Button(onClick = { onApply(working) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String, count: Int) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            "$title  ·  $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

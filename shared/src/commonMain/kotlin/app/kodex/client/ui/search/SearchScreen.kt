package app.kodex.client.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
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
import app.kodex.client.ui.catalog.rememberSourceNames
import app.kodex.client.ui.catalog.sourceLabel
import app.kodex.client.ui.sheetMaxHeight
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
import app.kodex.client.ui.InlineLoadError
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.nav.retain
import app.kodex.client.ui.collectAsStateSafe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Search state that outlives opening a result. The host keeps search *under* the detail stack, so this
 * screen is un-composed while a result is open and every `remember` in it would die — the query, the
 * results and the scroll offsets live here instead. Dropped when search is closed for good.
 * See RetainedState.kt.
 */
private class SearchState {
    val query = mutableStateOf("")
    val facets = mutableStateOf(Facets())
    val reloadKey = mutableIntStateOf(0)
    val local = mutableStateOf<SearchUiState>(SearchUiState.Idle)
    val online = mutableStateOf<OnlineState>(OnlineState.Idle)
    val onlineQuery = mutableStateOf("")
    val onlineToken = mutableIntStateOf(0)
    val sources = mutableStateOf<List<SourceDescriptor>>(emptyList())
    val selectedSources = mutableStateOf<Set<String>>(emptySet())
    val vocab = mutableStateOf<FacetVocab?>(null)
    val mode = mutableIntStateOf(0) // 0 = library, 1 = online
    val localGrid = LazyGridState()
    val onlineList = LazyListState()

    // Which inputs the loaded results belong to. Coming back from a result restarts the search effects
    // with unchanged inputs; without these they would re-query and flash a spinner over results that
    // are already here. Set only once results land, so a cancelled search still re-runs.
    var loadedLocal: String? = null
    var loadedOnline: String? = null
    var loadedSourcesFor: String? = null
}

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
    /**
     * [partialFailure] is set when one half of the search failed and the other did not. Without it a
     * failed series query behind a successful book query rendered as "no series matched", which is a
     * claim about the library rather than about the request.
     */
    data class Ready(
        val series: List<SeriesDto>,
        val books: List<BookDto>,
        val partialFailure: String? = null,
    ) : SearchUiState
}

/** Online (source) search: results grouped per content source. */
private data class SourceResults(
    val source: SourceDescriptor,
    val items: List<SourceSearchResult>,
    val error: Boolean,
    val favorite: Boolean = false,
)

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    /** [pending] = the favourite sources are in, the rest of the fan-out is still running. */
    data class Ready(val perSource: List<SourceResults>, val pending: Boolean = false) : OnlineState
}

/**
 * Shortest library query worth running: one or two characters match a large slice of a library, so the
 * round trip per keystroke buys nothing. Facet-only browsing (empty query) is exempt.
 */
private const val MIN_LOCAL_QUERY = 3

/** Chips a facet group shows while collapsed; the rest are behind "Show all". */
private const val FACET_COLLAPSED = 10

/** From this many values a group gets a find box when expanded — scanning chips stops working. */
private const val FACET_SEARCHABLE = 12

/** Hard cap on chips laid out at once, however big the vocabulary; the find box is the way past it. */
private const val FACET_MAX_VISIBLE = 100

private val SERIES_STATUSES = listOf("ONGOING", "COMPLETED", "PUBLISHING_FINISHED", "LICENSED", "CANCELLED", "ON_HIATUS", "UNKNOWN")
private val READING_STATUSES = listOf("NOT_STARTED" to "Unread", "IN_PROGRESS" to "In progress", "COMPLETED" to "Read")

/**
 * Full-screen global search — the mobile form of the web's library search + facet filters. Library mode
 * queries series (with facets) + books in parallel, debounced, once the query reaches [MIN_LOCAL_QUERY]
 * characters; a filter sheet narrows by genre, status, reading status, language, tag, and label. With
 * facets set, the query can be empty (browse by facet). Online mode only searches on Enter — a fan-out
 * across every installed source is far too expensive to run while the user is still typing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    session: SessionManager,
    api: KodexApi,
    sourcePrefs: app.kodex.client.data.SourcePrefsStore,
    onClose: () -> Unit,
    onOpenSeries: (SeriesDto) -> Unit = {},
    onOpenBook: (BookDto) -> Unit = {},
    /** Long-press: the book's details, since a tap now reads it. */
    onShowBookDetails: (BookDto) -> Unit = {},
    onOpenSourceSeries: (SourceDescriptor, SourceSearchResult) -> Unit = { _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    val sourceNames = rememberSourceNames(session, api)
    val st = retain("search") { SearchState() }
    var query by st.query
    // Library/Online is a two-page pager so the modes can be swiped between as well as tapped;
    // `online` stays derived from it, keeping one source of truth for the search effect below. The page
    // is seeded from (and written back to) the retained mode, so returning to search reopens the tab
    // the results are on.
    val modePager = androidx.compose.foundation.pager.rememberPagerState(st.mode.intValue) { 2 }
    val modeScope = androidx.compose.runtime.rememberCoroutineScope()
    val online = modePager.currentPage == 1 // false = library (local), true = sources (online)
    LaunchedEffect(modePager.currentPage) { st.mode.intValue = modePager.currentPage }
    var facets by st.facets
    // Bumped by the retry action so a failed search can be re-run without editing the query.
    var reloadKey by st.reloadKey
    var state by st.local
    var sheetOpen by remember { mutableStateOf(false) }
    var sourceSheetOpen by remember { mutableStateOf(false) }
    var vocab by st.vocab
    // Online mode: installed sources + which to search (empty = all) + per-source results.
    var sources by st.sources
    var selectedSources by st.selectedSources
    var onlineState by st.online
    // Online searches the query the user *submitted*, not the one being typed. The token re-runs the
    // same text (pressing Enter again after a source-level failure) without needing to edit it.
    var onlineQuery by st.onlineQuery
    var onlineToken by st.onlineToken
    // Favourite sources (server-persisted, shared with Browse) - searched first, see the effect below.
    val favorites by sourcePrefs.favorites.collectAsStateSafe()
    val favoriteIds = remember(favorites) { favorites.toSet() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun submitOnline() {
        onlineQuery = query.trim()
        onlineToken++
        keyboard?.hide()
    }

    // Load the installed content sources once per server (for the online-mode source picker + search).
    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        if (st.loadedSourcesFor == s.id) return@LaunchedEffect
        sources = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        st.loadedSourcesFor = s.id
    }

    // Online search: fan out the submitted query across the selected sources (or all) and group results
    // per source. Keyed on the submitted text, so typing alone never starts a fan-out.
    LaunchedEffect(onlineQuery, onlineToken, selectedSources, online, server?.id, sources) {
        if (!online) return@LaunchedEffect
        val current = server ?: return@LaunchedEffect
        val text = onlineQuery
        if (text.isEmpty()) { st.loadedOnline = null; onlineState = OnlineState.Idle; return@LaunchedEffect }
        val loadKey = "${current.id}|$text|$onlineToken|${selectedSources.sorted()}"
        if (st.loadedOnline == loadKey) return@LaunchedEffect // already on screen — came back from a result
        val targets = if (selectedSources.isEmpty()) sources else sources.filter { it.id in selectedSources }
        if (targets.isEmpty()) { onlineState = OnlineState.Ready(emptyList()); return@LaunchedEffect }
        // Favourites go first, and in a wave of their own: the sources the user actually cares about
        // land (and render) without waiting on the slowest of everything else. Within a wave the order
        // stays the server's, and every source in a wave is still searched in parallel.
        val (favTargets, restTargets) = targets.partition { it.id in favoriteIds }
        onlineState = OnlineState.Loading
        suspend fun searchAll(batch: List<SourceDescriptor>, favorite: Boolean): List<SourceResults> = coroutineScope {
            batch.map { src ->
                async {
                    runCatching { api.sourceSearch(current.baseUrl, current.apiKey, src.id, text, 1) }
                        .fold(
                            { SourceResults(src, it.items, error = false, favorite = favorite) },
                            { SourceResults(src, emptyList(), error = true, favorite = favorite) },
                        )
                }
            }.map { it.await() }
        }
        val favResults = searchAll(favTargets, favorite = true)
        // Favourite rows go up as soon as they are in; the rest append under them.
        if (favResults.isNotEmpty() && restTargets.isNotEmpty()) {
            onlineState = OnlineState.Ready(favResults, pending = true)
        }
        onlineState = OnlineState.Ready(favResults + searchAll(restTargets, favorite = false))
        st.loadedOnline = loadKey
    }

    LaunchedEffect(query, facets, server?.id, reloadKey) {
        val current = server ?: return@LaunchedEffect
        val text = query.trim()
        // Runs on a long-enough query, or on facets alone; anything shorter stays Idle (see the hint).
        if (!(text.length >= MIN_LOCAL_QUERY || (text.isEmpty() && !facets.isEmpty))) {
            st.loadedLocal = null
            state = SearchUiState.Idle
            return@LaunchedEffect
        }
        val loadKey = "${current.id}|$text|$facets|$reloadKey"
        if (st.loadedLocal == loadKey) return@LaunchedEffect // already on screen — came back from a result
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
        st.loadedLocal = loadKey
        state = if (results.first.isFailure && results.second.isFailure) {
            SearchUiState.Error("Search failed. Check your connection.")
        } else {
            SearchUiState.Ready(
                series = results.first.getOrDefault(emptyList()),
                books = results.second.getOrDefault(emptyList()),
                partialFailure = when {
                    results.first.isFailure -> "Couldn't search series — ${results.first.exceptionOrNull()!!.friendlyMessage()}"
                    results.second.isFailure -> "Couldn't search books — ${results.second.exceptionOrNull()!!.friendlyMessage()}"
                    else -> null
                },
            )
        }
    }

    // Only on a fresh open: returning from a result shouldn't throw the keyboard back over the results.
    LaunchedEffect(Unit) {
        if (query.isNotEmpty() || !facets.isEmpty) return@LaunchedEffect
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
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; onlineQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // Enter is what starts an online search; in library mode it only closes the
                        // keyboard, since those results are already following the text.
                        keyboardActions = KeyboardActions(onSearch = { if (online) submitOnline() else keyboard?.hide() }),
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
                            else Hint("Type a title and press Enter to search your installed sources.")
                        is OnlineState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        is OnlineState.Ready ->
                            if (!os.pending && os.perSource.all { it.items.isEmpty() }) Hint("No results.")
                            else OnlineResults(server?.baseUrl ?: "", server?.apiKey ?: "", os.perSource, os.pending, st.onlineList, onOpenSourceSeries)
                    }
                } else {
                    when (val s = state) {
                        is SearchUiState.Idle -> Hint(
                            if (query.isNotBlank()) "Type at least $MIN_LOCAL_QUERY characters to search your library."
                            else "Search series and books, or tap Filters to browse by genre, status, and more.",
                        )
                        is SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        is SearchUiState.Error -> Hint(s.message)
                        is SearchUiState.Ready ->
                            // "No results" is only honest when both halves actually answered.
                            if (s.series.isEmpty() && s.books.isEmpty() && s.partialFailure == null) Hint("No results.")
                            else Column(Modifier.fillMaxSize()) {
                                s.partialFailure?.let { InlineLoadError(it) { reloadKey++ } }
                                Results(
                                    server?.baseUrl ?: "", server?.apiKey ?: "", s, sourceNames, st.localGrid,
                                    onOpenSeries, onOpenBook, onShowBookDetails,
                                )
                            }
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
            sources = remember(sources, favoriteIds) { sources.sortedBy { it.id !in favoriteIds } },
            favorites = favoriteIds,
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var working by remember(facets) { mutableStateOf(facets) }

    ModalBottomSheet(modifier = Modifier.heightIn(max = sheetMaxHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
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
                if (vocab.tags.isNotEmpty()) FacetGroup("Tag", vocab.tags, working.tags,
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
    var expanded by remember(title) { mutableStateOf(false) }
    var find by remember(title) { mutableStateOf("") }

    // Picked values sort to the front, so a selection is never buried below the collapsed cut-off or
    // filtered out of sight — the group always shows what it is currently contributing to the search.
    val matches = remember(values, selected, find) {
        val needle = find.trim()
        values.filter { needle.isEmpty() || labelOf(it).contains(needle, ignoreCase = true) }
            .sortedByDescending { it in selected }
    }
    val visible = matches.take(if (expanded) FACET_MAX_VISIBLE else FACET_COLLAPSED)
    val overflow = matches.size - visible.size
    val picked = selected.count { it in values }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (picked > 0) "$title · $picked" else title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (expanded || matches.size > FACET_COLLAPSED) {
            TextButton(onClick = { expanded = !expanded; if (!expanded) find = "" }) {
                Text(if (expanded) "Show less" else "Show all (${values.size})")
            }
        }
    }
    // Big vocabularies (genres, tags) are unusable as a wall of chips; expanding one opens a find box so
    // the wanted value is a few keystrokes away instead of a long scroll.
    if (expanded && values.size > FACET_SEARCHABLE) {
        OutlinedTextField(
            value = find,
            onValueChange = { find = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Find ${title.lowercase()}") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (find.isNotEmpty()) {
                    IconButton(onClick = { find = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                }
            },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        visible.forEach { v ->
            FilterChip(selected = v in selected, onClick = { onToggle(v) }, label = { Text(labelOf(v)) })
        }
    }
    when {
        matches.isEmpty() -> FacetNote("No $title matches \"${find.trim()}\".")
        expanded && overflow > 0 -> FacetNote("+$overflow more — type above to narrow.")
    }
}

/** Small caption under a facet group: the overflow count, or an empty find result. */
@Composable
private fun FacetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun Set<String>.toggle(v: String): Set<String> = if (v in this) this - v else this + v
private fun String.titleCase(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun Results(
    baseUrl: String,
    apiKey: String,
    ready: SearchUiState.Ready,
    sourceNames: Map<String, String>,
    gridState: LazyGridState,
    onOpenSeries: (SeriesDto) -> Unit,
    onOpenBook: (BookDto) -> Unit,
    onShowBookDetails: (BookDto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = gridState,
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
                    source = sourceLabel(series, sourceNames),
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
                    onLongClick = { onShowBookDetails(book) },
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
    /** Favourites are on screen; the remaining sources are still being searched. */
    pending: Boolean,
    listState: LazyListState,
    onOpen: (SourceDescriptor, SourceSearchResult) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(perSource, key = { it.source.id }) { sr ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sr.favorite) {
                        Icon(Icons.Filled.Star, contentDescription = "Favorite source", tint = FavoriteAmber, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        sr.source.displayName.ifBlank { sr.source.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
        if (pending) {
            item(key = "pending") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "Searching the other sources…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val FavoriteAmber = Color(0xFFF59E0B)

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
    favorites: Set<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var working by remember(selected) { mutableStateOf(selected) }
    ModalBottomSheet(modifier = Modifier.heightIn(max = sheetMaxHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            Text("None selected searches every source; favourites are searched first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (sources.isEmpty()) {
                    Text("No sources installed.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = src.id in working,
                            onClick = { working = working.toggle(src.id) },
                            label = { Text(src.displayName.ifBlank { src.id }) },
                            leadingIcon = if (src.id !in favorites) null else {
                                { Icon(Icons.Filled.Star, contentDescription = null, tint = FavoriteAmber, modifier = Modifier.size(14.dp)) }
                            },
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

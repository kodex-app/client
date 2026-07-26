package app.kodex.client.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LabelDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.bookSubtitle
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.catalog.seriesSubtitle
import app.kodex.client.ui.catalog.seriesUnreadBadge
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
) {
    val server by session.activeServer.collectAsStateSafe()
    var query by remember { mutableStateOf("") }
    var facets by remember { mutableStateOf(Facets()) }
    var state by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }
    var sheetOpen by remember { mutableStateOf(false) }
    var vocab by remember { mutableStateOf<FacetVocab?>(null) }
    val focusRequester = remember { FocusRequester() }

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
                        placeholder = { Text("Search your library") },
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
                actions = {
                    TextButton(onClick = { sheetOpen = true }) {
                        Text(if (facets.count > 0) "Filters (${facets.count})" else "Filters")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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

    if (sheetOpen) {
        FacetSheet(
            vocab = vocab,
            facets = facets,
            onDismiss = { sheetOpen = false },
            onClear = { facets = Facets() },
            onApply = { facets = it; sheetOpen = false },
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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

            Column(Modifier.padding(top = 16.dp)) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { working = Facets(); onClear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = { onApply(working) }, modifier = Modifier.weight(1f)) { Text("Apply") }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
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

package app.kodex.client.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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

private data class SearchResults(
    val series: Result<List<SeriesDto>>,
    val books: Result<List<BookDto>>,
)

private sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Ready(val series: List<SeriesDto>, val books: List<BookDto>) : SearchUiState
}

/**
 * Full-screen global search — the mobile form of the web's top-bar search (library mode). A debounced
 * field (350 ms, matching the web) queries series + books in parallel and renders them as two labelled
 * grids. Facet/plugin modes from the web are a later addition.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var state by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query, server?.id) {
        val current = server ?: return@LaunchedEffect
        val text = query.trim()
        if (text.isEmpty()) {
            state = SearchUiState.Idle
            return@LaunchedEffect
        }
        delay(350) // debounce — cancelled and restarted on each keystroke
        state = SearchUiState.Loading
        val results = coroutineScope {
            val series = async { runCatching { api.searchSeries(current.baseUrl, current.apiKey, text) } }
            val books = async { runCatching { api.searchBooks(current.baseUrl, current.apiKey, text) } }
            SearchResults(series.await(), books.await())
        }
        state = if (results.series.isFailure && results.books.isFailure) {
            SearchUiState.Error("Search failed. Check your connection.")
        } else {
            SearchUiState.Ready(
                series = results.series.getOrDefault(emptyList()),
                books = results.books.getOrDefault(emptyList()),
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(120) // let the field attach before requesting focus (opens the keyboard)
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is SearchUiState.Idle -> Hint("Search series and books across your library.")
                is SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is SearchUiState.Error -> Hint(s.message)
                is SearchUiState.Ready ->
                    if (s.series.isEmpty() && s.books.isEmpty()) {
                        Hint("No results for “${query.trim()}”.")
                    } else {
                        Results(server?.baseUrl ?: "", server?.apiKey ?: "", s, onOpenSeries, onOpenBook)
                    }
            }
        }
    }
}

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

/** Full-width grid section header. */
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
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

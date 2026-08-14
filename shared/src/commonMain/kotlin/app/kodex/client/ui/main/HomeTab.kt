package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.visibleOnHome
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverSection
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.bookSubtitle
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.catalog.seriesSubtitle
import app.kodex.client.ui.catalog.seriesUnreadBadge
import app.kodex.client.ui.collectAsStateSafe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class HomeResults(
    val keepReading: Result<List<BookDto>>,
    val recentSeries: Result<List<SeriesDto>>,
    val updatedSeries: Result<List<SeriesDto>>,
    val recentBooks: Result<List<BookDto>>,
)

private data class HomeData(
    val continueReading: List<BookDto> = emptyList(),
    val recentSeries: List<SeriesDto> = emptyList(),
    val updatedSeries: List<SeriesDto> = emptyList(),
    val recentBooks: List<BookDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = continueReading.isEmpty() && recentSeries.isEmpty() &&
            updatedSeries.isEmpty() && recentBooks.isEmpty()
}

private sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(val data: HomeData) : HomeUiState
}

/**
 * Home mirrors the web UI: four horizontal cover rails — Continue reading, Recent series, Recently
 * updated series, Recently added books. Rows load independently; only a full failure surfaces an
 * error (with retry), matching the web's per-query behaviour.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    session: SessionManager,
    api: KodexApi,
    onOpenBook: (BookDto) -> Unit = {},
    onOpenSeries: (SeriesDto) -> Unit = {},
    onSeeAll: (app.kodex.client.ui.catalog.SeeAllKind) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    var reloadKey by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    // Distinct from the cold-load spinner: a pull keeps the current rails on screen and shows the
    // indicator instead of blanking Home back to a spinner.
    var refreshing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(server?.id, reloadKey) {
        val current = server ?: run { refreshing = false; return@LaunchedEffect }
        if (!refreshing) state = HomeUiState.Loading
        val navPrefs = app.kodex.client.data.loadLibraryNavPrefs(api, current.baseUrl, current.apiKey)
        val (keepReading, recentSeries, updatedSeries, recentBooks) = coroutineScope {
            val kr = async { runCatching { api.keepReading(current.baseUrl, current.apiKey) } }
            val rs = async { runCatching { api.recentSeries(current.baseUrl, current.apiKey) } }
            val us = async { runCatching { api.recentlyUpdatedSeries(current.baseUrl, current.apiKey) } }
            val rb = async { runCatching { api.recentBooks(current.baseUrl, current.apiKey) } }
            HomeResults(kr.await(), rs.await(), us.await(), rb.await())
        }
        val all = listOf(keepReading, recentSeries, updatedSeries, recentBooks)

        state = if (all.all { it.isFailure }) {
            HomeUiState.Error("Couldn't reach ${current.label}. Tap retry.")
        } else {
            HomeUiState.Ready(
                HomeData(
                    continueReading = keepReading.getOrDefault(emptyList()).visibleOnHome(navPrefs) { it.libraryId },
                    recentSeries = recentSeries.getOrDefault(emptyList()).visibleOnHome(navPrefs) { it.libraryId },
                    updatedSeries = updatedSeries.getOrDefault(emptyList()).visibleOnHome(navPrefs) { it.libraryId },
                    recentBooks = recentBooks.getOrDefault(emptyList()).visibleOnHome(navPrefs) { it.libraryId },
                ),
            )
        }
        refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; reloadKey++ },
        modifier = Modifier.fillMaxSize(),
    ) {
      when (val s = state) {
        is HomeUiState.Loading -> Centered { CircularProgressIndicator() }

        is HomeUiState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { reloadKey++ }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
            }
        }

        is HomeUiState.Ready -> {
            val apiKey = server?.apiKey ?: ""
            val baseUrl = server?.baseUrl ?: ""
            if (s.data.isEmpty) {
                Centered {
                    Text(
                        "Nothing here yet.\nAdd a library on your server to get started.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (s.data.continueReading.isNotEmpty()) item {
                        CoverSection("Continue reading", s.data.continueReading, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.KEEP_READING) }) { b ->
                            CoverCard(
                                coverUrl = bookCoverUrl(baseUrl, b.id),
                                apiKey = apiKey,
                                title = b.title,
                                subtitle = bookSubtitle(b),
                                unread = null,
                                onClick = { onOpenBook(b) },
                            )
                        }
                    }
                    if (s.data.recentSeries.isNotEmpty()) item {
                        CoverSection("Recent series", s.data.recentSeries, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.RECENT_SERIES) }) { series ->
                            SeriesCard(baseUrl, apiKey, series, onOpenSeries)
                        }
                    }
                    if (s.data.updatedSeries.isNotEmpty()) item {
                        CoverSection("Recently updated", s.data.updatedSeries, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.UPDATED_SERIES) }) { series ->
                            SeriesCard(baseUrl, apiKey, series, onOpenSeries)
                        }
                    }
                    if (s.data.recentBooks.isNotEmpty()) item {
                        CoverSection("Recently added", s.data.recentBooks, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.RECENT_BOOKS) }) { b ->
                            CoverCard(
                                coverUrl = bookCoverUrl(baseUrl, b.id),
                                apiKey = apiKey,
                                title = b.title,
                                subtitle = bookSubtitle(b),
                                unread = null,
                                onClick = { onOpenBook(b) },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SeriesCard(baseUrl: String, apiKey: String, series: SeriesDto, onOpen: (SeriesDto) -> Unit) {
    CoverCard(
        coverUrl = seriesCoverUrl(baseUrl, series),
        apiKey = apiKey,
        title = series.title,
        subtitle = seriesSubtitle(series),
        unread = seriesUnreadBadge(series),
        onClick = { onOpen(series) },
    )
}

/**
 * A full-screen centred message that is still *scrollable*, so pull-to-refresh works on the empty and
 * error states — the very screens where you'd pull. A plain Box emits no scroll events, leaving the
 * gesture dead exactly when Home has nothing to show. A single `fillParentMaxSize` item keeps the
 * content vertically centred, which a bare `verticalScroll` would not (it measures against unbounded
 * height, pinning the content to the top).
 */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

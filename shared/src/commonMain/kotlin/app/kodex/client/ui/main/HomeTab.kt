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
import app.kodex.client.network.HomeDto
import app.kodex.client.network.KeepReadingDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverSection
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.bookSubtitle
import app.kodex.client.ui.catalog.keepReadingCover
import app.kodex.client.ui.catalog.openKeepReading
import app.kodex.client.ui.catalog.rememberSourceNames
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.catalog.seriesSubtitle
import app.kodex.client.ui.catalog.seriesUnreadBadge
import app.kodex.client.ui.catalog.sourceLabel
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage

// Rail titles, shared by the section headers and the "See all" screens so the two can't drift.
private const val RAIL_CONTINUE = "Continue reading"
private const val RAIL_RECENT_SERIES = "Recent series"
private const val RAIL_UPDATED = "Recently updated"
private const val RAIL_RECENT_BOOKS = "Recently added"

private sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(val data: HomeDto) : HomeUiState
}

private val HomeDto.isEmpty: Boolean
    get() = keepReading.isEmpty() && recentSeries.isEmpty() &&
        recentlyUpdatedSeries.isEmpty() && recentBooks.isEmpty()

/**
 * Home mirrors the web UI: four horizontal cover rails — Continue reading, Recent series, Recently
 * updated series, Recently added books.
 *
 * All four come from one request (`GET /api/v1/home`), which is also what applies the user's
 * hidden-library / hidden-source preferences: the server owns that rule now, so this screen no longer
 * fetches the preferences and filters the rails itself. Because it is a single request, a failure is a
 * whole-screen failure rather than a rail-shaped one.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    session: SessionManager,
    api: KodexApi,
    onOpenBook: (String) -> Unit = {},
    onOpenSeries: (String) -> Unit = {},
    onOpenBrowseReader: OpenBrowseReader = { _, _, _ -> },
    onSeeAll: (app.kodex.client.ui.catalog.SeeAllKind) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    // Source labels for the cover cards; cached per server, so this is a lookup after the first load.
    val sourceNames = rememberSourceNames(session, api)
    var reloadKey by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    // Distinct from the cold-load spinner: a pull keeps the current rails on screen and shows the
    // indicator instead of blanking Home back to a spinner.
    var refreshing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(server?.id, reloadKey) {
        val current = server ?: run { refreshing = false; return@LaunchedEffect }
        if (!refreshing) state = HomeUiState.Loading
        state = runCatching { api.home(current.baseUrl, current.apiKey) }
            .fold(
                onSuccess = { HomeUiState.Ready(it) },
                onFailure = { HomeUiState.Error(it.friendlyMessage()) },
            )
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
                    if (s.data.keepReading.isNotEmpty()) item {
                        CoverSection(
                            RAIL_CONTINUE,
                            s.data.keepReading,
                            key = { "${it.kind}-${it.bookId ?: it.chapterId}" },
                            onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.KEEP_READING) },
                        ) { k ->
                            KeepReadingCard(baseUrl, apiKey, k, onOpenBook, onOpenSeries, onOpenBrowseReader)
                        }
                    }
                    if (s.data.recentSeries.isNotEmpty()) item {
                        CoverSection(RAIL_RECENT_SERIES, s.data.recentSeries, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.RECENT_SERIES) }) { series ->
                            SeriesCard(baseUrl, apiKey, series, sourceNames) { onOpenSeries(it.id) }
                        }
                    }
                    if (s.data.recentlyUpdatedSeries.isNotEmpty()) item {
                        CoverSection(RAIL_UPDATED, s.data.recentlyUpdatedSeries, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.UPDATED_SERIES) }) { series ->
                            SeriesCard(baseUrl, apiKey, series, sourceNames) { onOpenSeries(it.id) }
                        }
                    }
                    if (s.data.recentBooks.isNotEmpty()) item {
                        CoverSection(RAIL_RECENT_BOOKS, s.data.recentBooks, key = { it.id }, onSeeAll = { onSeeAll(app.kodex.client.ui.catalog.SeeAllKind.RECENT_BOOKS) }) { b ->
                            CoverCard(
                                coverUrl = bookCoverUrl(baseUrl, b.id),
                                apiKey = apiKey,
                                title = b.title,
                                subtitle = bookSubtitle(b),
                                unread = null,
                                onClick = { onOpenBook(b.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * One "Continue reading" card. The rail spans both reading paths, so the card shows the series it
 * belongs to over the entry you're part-way through, and resolves its own cover and destination.
 */
@Composable
private fun KeepReadingCard(
    baseUrl: String,
    apiKey: String,
    entry: KeepReadingDto,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenBrowseReader: OpenBrowseReader,
) {
    CoverCard(
        coverUrl = keepReadingCover(baseUrl, entry),
        apiKey = apiKey,
        title = entry.seriesName.ifBlank { entry.title.orEmpty() },
        subtitle = entry.title,
        unread = null,
        onClick = { openKeepReading(entry, onOpenBook, onOpenSeries, onOpenBrowseReader) },
    )
}

@Composable
private fun SeriesCard(
    baseUrl: String,
    apiKey: String,
    series: SeriesDto,
    sourceNames: Map<String, String>,
    onOpen: (SeriesDto) -> Unit,
) {
    CoverCard(
        coverUrl = seriesCoverUrl(baseUrl, series),
        apiKey = apiKey,
        title = series.title,
        subtitle = seriesSubtitle(series),
        unread = seriesUnreadBadge(series),
        onClick = { onOpen(series) },
        source = sourceLabel(series, sourceNames),
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

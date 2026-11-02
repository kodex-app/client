package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.icedtea.kodex.ui.catalog.CoverImage
import dev.icedtea.kodex.ui.catalog.CoverThumb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.HomeDto
import dev.icedtea.kodex.network.KeepReadingDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.SeriesDto
import dev.icedtea.kodex.network.home
import dev.icedtea.kodex.ui.catalog.CoverCard
import dev.icedtea.kodex.ui.catalog.CoverSection
import dev.icedtea.kodex.ui.catalog.bookCoverUrl
import dev.icedtea.kodex.ui.catalog.bookSubtitle
import dev.icedtea.kodex.ui.catalog.keepReadingCover
import dev.icedtea.kodex.ui.catalog.openKeepReading
import dev.icedtea.kodex.ui.catalog.rememberSourceNames
import dev.icedtea.kodex.ui.catalog.seriesCoverUrl
import dev.icedtea.kodex.ui.catalog.seriesSubtitle
import dev.icedtea.kodex.ui.catalog.seriesUnreadBadge
import dev.icedtea.kodex.ui.catalog.sourceLabel
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.nav.retain

// Rail titles, shared by the section headers and the "See all" screens so the two can't drift.
private const val RAIL_CONTINUE = "Continue reading"
private const val RAIL_JUMP_BACK = "Jump back in"
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
 * Home mirrors the web UI: a greeting, a hero for the entry you read last, a "Jump back in" rail of the
 * rest you're part-way through, then the Recent series / Recently updated / Recently added rails.
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
    /** Long-press: the book's details, since a tap now reads it. */
    onShowBookDetails: (String) -> Unit = {},
    onOpenSeries: (String) -> Unit = {},
    onOpenSourceReader: OpenSourceReader = { _, _, _, _ -> },
    onOpenBrowseReader: OpenBrowseReader = { _, _, _ -> },
    onSeeAll: (dev.icedtea.kodex.ui.catalog.SeeAllKind) -> Unit = {},
    /** Empty-state shortcuts to the Browse and Libraries tabs. */
    onBrowse: () -> Unit = {},
    onLibraries: () -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    val user by session.currentUser.collectAsStateSafe()
    // Source labels for the cover cards; cached per server, so this is a lookup after the first load.
    val sourceNames = rememberSourceNames(session, api)
    var reloadKey by remember { mutableStateOf(0) }
    // Retained, not remembered: opening a cover unmounts the whole tab area, so a plain `remember`
    // sent Home back to the spinner and the top of the rails on every return. Held here, the rails
    // stay on screen and refresh underneath — the same deal LoadedContent gives the other tabs.
    var state by retain("home:state") { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    val listState = retain("home:scroll") { LazyListState() }
    // Distinct from the cold-load spinner: a pull keeps the current rails on screen and shows the
    // indicator instead of blanking Home back to a spinner.
    var refreshing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(server?.id, reloadKey) {
        val current = server ?: run { refreshing = false; return@LaunchedEffect }
        // Blank back to the spinner only when there is nothing to show; re-entering the tab with rails
        // already loaded refreshes them in place, which is what keeps the scroll position meaningful.
        if (!refreshing && state !is HomeUiState.Ready) state = HomeUiState.Loading
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
        is HomeUiState.Loading -> HomeSkeleton()

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
                Centered { HomeEmpty(onBrowse = onBrowse, onLibraries = onLibraries) }
            } else {
                // The newest entry you're part-way through gets the hero; the rest share a rail of
                // compact resume cards beneath it, so Home leads with "pick up where you left off".
                val hero = s.data.keepReading.firstOrNull()
                val moreKeepReading = s.data.keepReading.drop(1)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { Greeting(user?.email) }
                    if (hero != null) item {
                        ContinueHero(
                            baseUrl, apiKey, hero,
                            onResume = { openKeepReading(hero, onOpenBook, onOpenSeries, onOpenSourceReader, onOpenBrowseReader) },
                            onOpenSeries = hero.seriesId?.let { id -> { onOpenSeries(id) } },
                            onLongClick = hero.bookId?.takeIf { hero.isBook }?.let { id -> { onShowBookDetails(id) } },
                        )
                    }
                    if (moreKeepReading.isNotEmpty()) item {
                        CoverSection(
                            RAIL_JUMP_BACK,
                            moreKeepReading,
                            key = { "${it.kind}-${it.bookId ?: it.chapterId}" },
                            onSeeAll = { onSeeAll(dev.icedtea.kodex.ui.catalog.SeeAllKind.KEEP_READING) },
                            stateKey = "rail:continue",
                        ) { k ->
                            ResumeCard(baseUrl, apiKey, k, onOpenBook, onShowBookDetails, onOpenSeries, onOpenSourceReader, onOpenBrowseReader)
                        }
                    }
                    if (s.data.recentSeries.isNotEmpty()) item {
                        CoverSection(RAIL_RECENT_SERIES, s.data.recentSeries, key = { it.id }, onSeeAll = { onSeeAll(dev.icedtea.kodex.ui.catalog.SeeAllKind.RECENT_SERIES) }, stateKey = "rail:recent-series") { series ->
                            SeriesCard(baseUrl, apiKey, series, sourceNames) { onOpenSeries(it.id) }
                        }
                    }
                    if (s.data.recentlyUpdatedSeries.isNotEmpty()) item {
                        CoverSection(RAIL_UPDATED, s.data.recentlyUpdatedSeries, key = { it.id }, onSeeAll = { onSeeAll(dev.icedtea.kodex.ui.catalog.SeeAllKind.UPDATED_SERIES) }, stateKey = "rail:updated") { series ->
                            SeriesCard(baseUrl, apiKey, series, sourceNames) { onOpenSeries(it.id) }
                        }
                    }
                    if (s.data.recentBooks.isNotEmpty()) item {
                        CoverSection(RAIL_RECENT_BOOKS, s.data.recentBooks, key = { it.id }, onSeeAll = { onSeeAll(dev.icedtea.kodex.ui.catalog.SeeAllKind.RECENT_BOOKS) }, stateKey = "rail:recent-books") { b ->
                            CoverCard(
                                coverUrl = bookCoverUrl(baseUrl, b.id),
                                apiKey = apiKey,
                                title = b.title,
                                subtitle = bookSubtitle(b),
                                unread = null,
                                onClick = { onOpenBook(b.id) },
                                onLongClick = { onShowBookDetails(b.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** "Welcome back, <name>": the local part of the account's email, the only name the server keeps. */
@Composable
private fun Greeting(email: String?) {
    val name = email?.substringBefore('@')?.takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            if (name != null) "Welcome back, $name" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Pick up where you left off, or find something new.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How far into an entry you are, 0..1, or null when the entry doesn't know its page count. */
private fun KeepReadingDto.progress(): Float? =
    pageCount?.takeIf { it > 0 }?.let { (page.toFloat() / it).coerceIn(0f, 1f) }

private fun KeepReadingDto.progressLabel(): String? {
    val total = pageCount?.takeIf { it > 0 } ?: return if (page > 0) "Page $page" else null
    return "Page $page of $total · ${((progress() ?: 0f) * 100).toInt()}%"
}

/**
 * The hero for the entry you read last: its cover over a blurred, darkened copy of itself, the series
 * and chapter, how far you got, and the two ways on — resume, or the series. White on a dark scrim
 * whatever the theme, since it sits over arbitrary artwork.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ContinueHero(
    baseUrl: String,
    apiKey: String,
    entry: KeepReadingDto,
    onResume: () -> Unit,
    onOpenSeries: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val cover = keepReadingCover(baseUrl, entry)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onResume, onLongClick = onLongClick),
    ) {
        CoverThumb(cover, apiKey, Modifier.matchParentSize().blur(32.dp))
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.45f))),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(100.dp).aspectRatio(2f / 3f)
                    .shadow(10.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) { CoverImage(cover, apiKey, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    RAIL_CONTINUE.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.seriesName.ifBlank { entry.title.orEmpty() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.title?.takeIf { it.isNotBlank() && it != entry.seriesName }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                entry.progress()?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        drawStopIndicator = {},
                    )
                }
                entry.progressLabel()?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onResume, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Continue")
                    }
                    if (onOpenSeries != null) {
                        OutlinedButton(
                            onClick = onOpenSeries,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        ) { Text("Series") }
                    }
                }
            }
        }
    }
}

/**
 * A compact "Jump back in" card: thumbnail, series over the entry, and a progress bar — landscape, so
 * a rail of them reads as a to-do list rather than a shelf of posters.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ResumeCard(
    baseUrl: String,
    apiKey: String,
    entry: KeepReadingDto,
    onOpenBook: (String) -> Unit,
    onShowBookDetails: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader,
) {
    val bookId = entry.bookId.takeIf { entry.isBook }
    Row(
        Modifier.width(264.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = { openKeepReading(entry, onOpenBook, onOpenSeries, onOpenSourceReader, onOpenBrowseReader) },
                onLongClick = bookId?.let { { onShowBookDetails(it) } },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 78.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) { CoverImage(keepReadingCover(baseUrl, entry), apiKey, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.seriesName.ifBlank { entry.title.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.title?.takeIf { it.isNotBlank() && it != entry.seriesName }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.progress()?.let { p ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

/** Nothing to show at all: say what fills Home, and offer the two places that do. */
@Composable
private fun HomeEmpty(onBrowse: () -> Unit, onLibraries: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Your shelf is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add a library, or follow a series from a source — what you're reading and what's new will show up here.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBrowse) { Text("Browse sources") }
            OutlinedButton(onClick = onLibraries) { Text("Libraries") }
        }
    }
}

/** First-load placeholder shaped like Home itself — greeting, hero, a rail — instead of a lone spinner. */
@Composable
private fun HomeSkeleton() {
    val tone = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth(0.6f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(tone))
        Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(20.dp)).background(tone))
        Box(Modifier.fillMaxWidth(0.4f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(tone))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(tone))
            }
        }
    }
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

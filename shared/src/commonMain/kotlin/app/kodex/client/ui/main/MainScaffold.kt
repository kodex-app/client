package app.kodex.client.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.platform.AppBackHandler
import app.kodex.client.platform.nowMillis
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.nav.LocalRetainedSlot
import app.kodex.client.ui.nav.RetainedStateStore
import app.kodex.client.ui.recents.rememberNewUpdateCount
import app.kodex.client.ui.search.SearchScreen

/** Retained-state slot for the search screen; it keeps its query and results while a result is open. */
private const val SEARCH_SLOT = "search"

/** The five destinations of the main bottom navigation. */
enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Libraries("Libraries", Icons.Outlined.CollectionsBookmark),
    Recents("Recents", Icons.Outlined.History),
    Browse("Browse", Icons.Outlined.Explore),
    More("More", Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(session: SessionManager, api: KodexApi, appSettings: AppSettings, sourcePrefs: app.kodex.client.data.SourcePrefsStore) {
    var tab by remember { mutableStateOf(BottomTab.Home) }
    var searchOpen by remember { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<DetailRoute>() }
    // Screens under the top of the stack are not composed, so anything they must not lose (loaded
    // pages, scroll offsets) lives here rather than in their own `remember`. See RetainedState.kt.
    val retained = remember { RetainedStateStore() }

    /** Slot key for a detail entry — its depth plus its identity, so two of the same screen differ. */
    fun detailSlot(index: Int) = "d$index/${backStack[index]}"

    fun popDetail() {
        val i = backStack.lastIndex
        retained.forget(detailSlot(i))
        backStack.removeAt(i)
    }

    /** Leaving search for good — its retained query and results are dropped, so it reopens clean. */
    fun closeSearch() {
        searchOpen = false
        retained.forget(SEARCH_SLOT)
    }
    val incognito by appSettings.incognitoMode.collectAsStateSafe()
    val server by session.activeServer.collectAsStateSafe()

    // New chapters since the tab was last opened, badged on Recents. Opening the tab is what marks
    // them seen — there is no separate "mark all read", because looking at the feed *is* that.
    val newUpdates = rememberNewUpdateCount(session, api, appSettings)
    // Keyed on the count as well, so chapters arriving while the tab is already open are marked seen
    // straight away rather than badging the tab the user is currently looking at.
    LaunchedEffect(tab, server?.id, newUpdates) {
        val id = server?.id ?: return@LaunchedEffect
        if (tab == BottomTab.Recents) appSettings.markUpdatesSeen(id, nowMillis())
    }

    val openSeries: (String) -> Unit = { backStack.add(DetailRoute.SeriesDetail(it)) }
    val openBook: (String) -> Unit = { backStack.add(DetailRoute.BookDetail(it)) }
    val openLibrary: (LibraryDto) -> Unit = { backStack.add(DetailRoute.LibrarySeries(it)) }

    /**
     * Open the series a reader belongs to, *on top of* the reader.
     *
     * Pushing rather than replacing costs a duplicate series entry when you arrived from that same
     * series, but it keeps back meaning "undo that button" — replacing dropped the reader, so back
     * from the series skipped past it to whatever was underneath.
     */
    val openSeriesFromReader: (DetailRoute) -> Unit = { route -> backStack.add(route) }

    // System back navigates within the app: pop a detail screen → close search → return to Home tab.
    // Details are popped first because search sits *under* them: opening a result from search keeps the
    // search screen on the stack, so back off that result has to land on the results again.
    // Disabled only on the Home tab with nothing open, so back there exits the app (expected).
    AppBackHandler(enabled = searchOpen || backStack.isNotEmpty() || tab != BottomTab.Home) {
        when {
            backStack.isNotEmpty() -> popDetail()
            searchOpen -> closeSearch()
            else -> tab = BottomTab.Home
        }
    }

    val immersive = backStack.lastOrNull().let { it is DetailRoute.Reader || it is DetailRoute.SourceReader }
    val turnOffIncognito = { appSettings.setIncognitoMode(false) }

    if (backStack.isNotEmpty()) {
      CompositionLocalProvider(LocalRetainedSlot provides retained.slot(detailSlot(backStack.lastIndex))) {
      WithIncognitoBanner(incognito && !immersive, turnOffIncognito) {
        DetailHost(
            route = backStack.last(),
            session = session,
            api = api,
            appSettings = appSettings,
            onOpenSeries = openSeries,
            onOpenBook = openBook,
            onOpenLibrary = openLibrary,
            onOpenSourceSeries = { source, seed -> backStack.add(DetailRoute.SourceSeries(source, seed)) },
            onOpenReader = { backStack.add(DetailRoute.Reader(it, incognito = incognito)) },
            onOpenReaderAt = { bookId, page -> backStack.add(DetailRoute.Reader(bookId, page, incognito = incognito)) },
            onOpenSourceReader = { providerId, chapterId, seriesId, chapterName ->
                backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = incognito))
            },
            onOpenBrowseReader = { source, chapterId, chapterName ->
                backStack.add(DetailRoute.SourceReader(source.providerId, chapterId, null, chapterName, source, incognito = incognito))
            },
            onOpenReaderIncognito = { backStack.add(DetailRoute.Reader(it, incognito = true)) },
            onOpenSourceReaderIncognito = { providerId, chapterId, seriesId, chapterName ->
                backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = true))
            },
            onOpenBrowseReaderIncognito = { source, chapterId, chapterName ->
                backStack.add(DetailRoute.SourceReader(source.providerId, chapterId, null, chapterName, source, incognito = true))
            },
            onOpenSeriesFromReader = openSeriesFromReader,
            onOpenMigrate = { seriesId, providerId, sourceSeriesId, title ->
                backStack.add(DetailRoute.Migrate(seriesId, providerId, sourceSeriesId, title))
            },
            onOpenPluginRepositories = { backStack.add(DetailRoute.PluginRepositories) },
            onBack = { popDetail() },
        )
      }
      }
        return
    }

    // Search renders *below* the detail stack: a result opens on top of it rather than replacing it, so
    // back returns to the results (still loaded — SearchScreen retains them in this slot).
    if (searchOpen) {
      CompositionLocalProvider(LocalRetainedSlot provides retained.slot(SEARCH_SLOT)) {
      WithIncognitoBanner(incognito, turnOffIncognito) {
        SearchScreen(
            session, api,
            onClose = { closeSearch() },
            onOpenSeries = { openSeries(it.id) },
            onOpenBook = { openBook(it.id) },
            onOpenSourceSeries = { source, seed -> backStack.add(DetailRoute.SourceSeries(source, seed)) },
        )
      }
      }
        return
    }

    // The tab area is likewise dropped while a detail screen is open, so each tab keeps its own slot.
    CompositionLocalProvider(LocalRetainedSlot provides retained.slot("tab:${tab.name}")) {
    WithIncognitoBanner(incognito, turnOffIncognito) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            if (entry == BottomTab.Recents && newUpdates > 0) {
                                BadgedBox(badge = { Badge { Text(if (newUpdates > 99) "99+" else "$newUpdates") } }) {
                                    Icon(entry.icon, contentDescription = entry.label)
                                }
                            } else {
                                Icon(entry.icon, contentDescription = entry.label)
                            }
                        },
                        label = { Text(entry.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (tab) {
                BottomTab.Home -> HomeTab(
                    session, api,
                    onOpenBook = openBook,
                    onOpenSeries = openSeries,
                    onOpenBrowseReader = { source, chapterId, chapterName ->
                        backStack.add(DetailRoute.SourceReader(source.providerId, chapterId, null, chapterName, source, incognito = incognito))
                    },
                    onSeeAll = { kind -> backStack.add(DetailRoute.SeeAll(kind)) },
                )
                BottomTab.Libraries -> LibrariesTab(
                    session, api, appSettings,
                    onOpenLibrary = openLibrary,
                    onArrange = { backStack.add(DetailRoute.Libraries) },
                )
                BottomTab.Recents -> RecentsTab(
                    session, api,
                    onOpenReader = { backStack.add(DetailRoute.Reader(it, incognito = incognito)) },
                    onOpenSourceReader = { providerId, chapterId, seriesId, chapterName ->
                        backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = incognito))
                    },
                    onOpenBrowseReader = { source, chapterId, chapterName ->
                        backStack.add(DetailRoute.SourceReader(source.providerId, chapterId, null, chapterName, source, incognito = incognito))
                    },
                    onOpenSeries = openSeries,
                )
                BottomTab.Browse -> BrowseTab(session, api, sourcePrefs, onOpenSource = { src, feed -> backStack.add(DetailRoute.SourceFeed(src, feed)) })
                BottomTab.More -> MoreTab(
                    session,
                    appSettings,
                    onOpenDownloads = { backStack.add(DetailRoute.Downloads) },
                    onOpenSettings = { backStack.add(DetailRoute.Settings) },
                    onEditServer = { backStack.add(DetailRoute.ServerConnection) },
                    onOpenAppearance = { backStack.add(DetailRoute.Appearance) },
                    onOpenAbout = { backStack.add(DetailRoute.About) },
                    onOpenLibraries = { backStack.add(DetailRoute.Libraries) },
                    onOpenLabels = { backStack.add(DetailRoute.Labels) },
                    onOpenPlugins = { backStack.add(DetailRoute.Plugins) },
                    onOpenUsers = { backStack.add(DetailRoute.Users) },
                    onOpenTasks = { backStack.add(DetailRoute.Tasks) },
                    onOpenServerActions = { backStack.add(DetailRoute.ServerActions) },
                    onOpenSecurity = { backStack.add(DetailRoute.Security) },
                    onOpenBackup = { backStack.add(DetailRoute.Backup) },
                    onOpenNetwork = { backStack.add(DetailRoute.NetworkSettings) },
                    onOpenLogs = { backStack.add(DetailRoute.Logs) },
                )
            }
        }
    }
    }
    }
}

/**
 * Wraps a screen with the persistent incognito strip. The strip takes the status-bar inset itself and
 * the content below is told it's consumed, so top bars underneath don't pad for it a second time.
 */
@Composable
private fun WithIncognitoBanner(show: Boolean, onTurnOff: () -> Unit, content: @Composable () -> Unit) {
    if (!show) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        IncognitoBanner(onTurnOff)
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { content() }
    }
}

/** Slim indigo strip: an unmistakable, app-wide sign that nothing is being recorded. */
@Composable
private fun IncognitoBanner(onTurnOff: () -> Unit) {
    Row(
        // Background before the inset padding, so the colour runs under the status bar.
        Modifier.fillMaxWidth().background(IncognitoIndigo).statusBarsPadding()
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Incognito — nothing is being recorded",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onTurnOff, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
            Text("Turn off", style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
        }
    }
}

/** The web UI's incognito tone, matched so both clients signal the mode identically. */
private val IncognitoIndigo = Color(0xFF4A3F8F)

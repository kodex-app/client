package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.network.KodexApi
import app.kodex.client.platform.AppBackHandler
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.search.SearchScreen

/** The five destinations of the main bottom navigation. */
enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Libraries("Libraries", Icons.AutoMirrored.Filled.List),
    Recents("Recents", Icons.Filled.Refresh),
    Browse("Browse", Icons.Filled.Search),
    More("More", Icons.Filled.MoreVert),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(session: SessionManager, api: KodexApi, appSettings: AppSettings) {
    var tab by remember { mutableStateOf(BottomTab.Home) }
    var searchOpen by remember { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<DetailRoute>() }
    val incognito by appSettings.incognitoMode.collectAsStateSafe()

    val openSeries: (String) -> Unit = { backStack.add(DetailRoute.SeriesDetail(it)) }
    val openBook: (String) -> Unit = { backStack.add(DetailRoute.BookDetail(it)) }

    // System back navigates within the app: close search → pop a detail screen → return to Home tab.
    // Disabled only on the Home tab with nothing open, so back there exits the app (expected).
    AppBackHandler(enabled = searchOpen || backStack.isNotEmpty() || tab != BottomTab.Home) {
        when {
            searchOpen -> searchOpen = false
            backStack.isNotEmpty() -> backStack.removeAt(backStack.lastIndex)
            else -> tab = BottomTab.Home
        }
    }

    if (searchOpen) {
        SearchScreen(
            session, api,
            onClose = { searchOpen = false },
            onOpenSeries = { searchOpen = false; openSeries(it.id) },
            onOpenBook = { searchOpen = false; openBook(it.id) },
        )
        return
    }

    if (backStack.isNotEmpty()) {
        DetailHost(
            route = backStack.last(),
            session = session,
            api = api,
            appSettings = appSettings,
            onOpenSeries = openSeries,
            onOpenBook = openBook,
            onOpenSourceSeries = { source, seed -> backStack.add(DetailRoute.SourceSeries(source, seed)) },
            onOpenReader = { backStack.add(DetailRoute.Reader(it, incognito = incognito)) },
            onOpenReaderAt = { bookId, page -> backStack.add(DetailRoute.Reader(bookId, page, incognito = incognito)) },
            onOpenSourceReader = { providerId, chapterId, seriesId, chapterName ->
                backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = incognito))
            },
            onOpenReaderIncognito = { backStack.add(DetailRoute.Reader(it, incognito = true)) },
            onOpenSourceReaderIncognito = { providerId, chapterId, seriesId, chapterName ->
                backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = true))
            },
            onOpenMigrate = { seriesId, providerId, sourceSeriesId, title ->
                backStack.add(DetailRoute.Migrate(seriesId, providerId, sourceSeriesId, title))
            },
            onBack = { backStack.removeAt(backStack.lastIndex) },
        )
        return
    }

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
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
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
                    onOpenBook = { openBook(it.id) },
                    onOpenSeries = { openSeries(it.id) },
                    onSeeAll = { kind -> backStack.add(DetailRoute.SeeAll(kind)) },
                )
                BottomTab.Libraries -> LibrariesTab(session, api, onOpenLibrary = { backStack.add(DetailRoute.LibrarySeries(it)) })
                BottomTab.Recents -> RecentsTab(
                    session, api,
                    onOpenReader = { backStack.add(DetailRoute.Reader(it, incognito = incognito)) },
                    onOpenSourceReader = { providerId, chapterId, seriesId, chapterName ->
                        backStack.add(DetailRoute.SourceReader(providerId, chapterId, seriesId, chapterName, incognito = incognito))
                    },
                    onOpenSeries = openSeries,
                )
                BottomTab.Browse -> BrowseTab(session, api, appSettings, onOpenSource = { src, feed -> backStack.add(DetailRoute.SourceFeed(src, feed)) })
                BottomTab.More -> MoreTab(
                    session,
                    appSettings,
                    onOpenDownloads = { backStack.add(DetailRoute.Downloads) },
                    onOpenSettings = { backStack.add(DetailRoute.Settings) },
                    onOpenAppearance = { backStack.add(DetailRoute.Appearance) },
                    onOpenAbout = { backStack.add(DetailRoute.About) },
                    onOpenLibraries = { backStack.add(DetailRoute.Libraries) },
                    onOpenLabels = { backStack.add(DetailRoute.Labels) },
                    onOpenPlugins = { backStack.add(DetailRoute.Plugins) },
                )
            }
        }
    }
}


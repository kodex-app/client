package app.kodex.client.ui.main

import androidx.compose.runtime.Composable
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.ui.book.BookDetailScreen
import app.kodex.client.ui.browse.SourceFeedScreen
import app.kodex.client.ui.browse.SourceSeriesScreen
import app.kodex.client.ui.downloads.DownloadsScreen
import app.kodex.client.ui.library.LibrarySeriesScreen
import app.kodex.client.ui.reader.ReaderScreen
import app.kodex.client.ui.reader.SourceReaderScreen
import app.kodex.client.ui.series.SeriesDetailScreen
import app.kodex.client.ui.manage.LabelsScreen
import app.kodex.client.ui.manage.LibrariesScreen
import app.kodex.client.ui.manage.MigrateScreen
import app.kodex.client.ui.manage.PluginsScreen
import app.kodex.client.ui.settings.AboutScreen
import app.kodex.client.ui.settings.AppearanceScreen
import app.kodex.client.ui.settings.SettingsScreen

/**
 * Minimal in-app navigation: full-screen "detail" routes pushed onto a back stack over the tab
 * scaffold (bottom nav hidden), popped with back. Deliberately lightweight — grows a case per
 * drill-down until a real nav library is warranted (the reader is the likely trigger).
 */
sealed interface DetailRoute {
    data class LibrarySeries(val library: LibraryDto) : DetailRoute
    data class SourceFeed(val source: SourceDescriptor, val feed: String = "popular") : DetailRoute
    data class SourceSeries(val source: SourceDescriptor, val seed: SourceSearchResult) : DetailRoute
    data class SeriesDetail(val seriesId: String) : DetailRoute
    data class BookDetail(val bookId: String) : DetailRoute
    data class Reader(val bookId: String, val startPage: Int? = null, val incognito: Boolean = false) : DetailRoute
    data class SourceReader(
        val providerId: String,
        val chapterId: String,
        val seriesId: String?,
        val chapterName: String?,
        val incognito: Boolean = false,
    ) : DetailRoute

    data object Downloads : DetailRoute
    data object Settings : DetailRoute
    data object Appearance : DetailRoute
    data object About : DetailRoute
    data object Libraries : DetailRoute
    data object Labels : DetailRoute
    data object Plugins : DetailRoute
    data class Migrate(
        val seriesId: String,
        val providerId: String,
        val sourceSeriesId: String,
        val title: String,
    ) : DetailRoute
}

/** Opens a streamed (no-download) chapter reader. */
typealias OpenSourceReader = (providerId: String, chapterId: String, seriesId: String?, chapterName: String?) -> Unit

@Composable
fun DetailHost(
    route: DetailRoute,
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    onOpenSeries: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSourceSeries: (SourceDescriptor, SourceSearchResult) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenReaderAt: (bookId: String, page: Int) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenReaderIncognito: (String) -> Unit,
    onOpenSourceReaderIncognito: OpenSourceReader,
    onOpenMigrate: (seriesId: String, providerId: String, sourceSeriesId: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        is DetailRoute.LibrarySeries ->
            LibrarySeriesScreen(session, api, appSettings, route.library, onBack, onOpenSeries = { onOpenSeries(it.id) })

        is DetailRoute.SourceFeed ->
            SourceFeedScreen(session, api, route.source, onBack, onOpenSourceSeries = { onOpenSourceSeries(route.source, it) }, initialFeed = route.feed)

        is DetailRoute.SourceSeries ->
            SourceSeriesScreen(session, api, route.source, route.seed, onBack)

        is DetailRoute.SeriesDetail ->
            SeriesDetailScreen(
                session, api, route.seriesId, onBack,
                onOpenBook = onOpenBook,
                onOpenReader = onOpenReader,
                onOpenSourceReader = onOpenSourceReader,
                onOpenMigrate = onOpenMigrate,
                onOpenReaderAt = onOpenReaderAt,
                onOpenSeries = onOpenSeries,
                onOpenReaderIncognito = onOpenReaderIncognito,
                onOpenSourceReaderIncognito = onOpenSourceReaderIncognito,
            )

        is DetailRoute.BookDetail ->
            BookDetailScreen(session, api, route.bookId, onBack, onRead = onOpenReader, onOpenReaderAt = onOpenReaderAt)

        is DetailRoute.Reader ->
            ReaderScreen(session, api, route.bookId, onBack, route.startPage, route.incognito)

        is DetailRoute.SourceReader ->
            SourceReaderScreen(session, api, route.providerId, route.chapterId, route.seriesId, route.chapterName, onBack, route.incognito)

        is DetailRoute.Downloads ->
            DownloadsScreen(session, api, onBack)

        is DetailRoute.Settings ->
            SettingsScreen(session, api, onBack)

        is DetailRoute.Appearance ->
            AppearanceScreen(appSettings, onBack)

        is DetailRoute.About ->
            AboutScreen(onBack)

        is DetailRoute.Libraries ->
            LibrariesScreen(session, api, onBack)

        is DetailRoute.Labels ->
            LabelsScreen(session, api, onBack)

        is DetailRoute.Plugins ->
            PluginsScreen(session, api, onBack)

        is DetailRoute.Migrate ->
            MigrateScreen(session, api, route.seriesId, route.providerId, route.sourceSeriesId, route.title, onBack)
    }
}

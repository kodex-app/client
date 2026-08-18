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
import app.kodex.client.ui.manage.BackupScreen
import app.kodex.client.ui.manage.LogsScreen
import app.kodex.client.ui.manage.NetworkSettingsScreen
import app.kodex.client.ui.manage.PluginRepositoriesScreen
import app.kodex.client.ui.manage.PluginsScreen
import app.kodex.client.ui.manage.ServerActionsScreen
import app.kodex.client.ui.manage.TasksScreen
import app.kodex.client.ui.manage.UsersScreen
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
        val sourceSeries: SourceSeriesContext? = null,
        val incognito: Boolean = false,
    ) : DetailRoute

    data object Downloads : DetailRoute
    data object Settings : DetailRoute
    data object ServerConnection : DetailRoute
    data object Appearance : DetailRoute
    data object About : DetailRoute
    data object Libraries : DetailRoute
    data object Labels : DetailRoute
    data object Plugins : DetailRoute
    data object Users : DetailRoute
    data object Tasks : DetailRoute
    data object ServerActions : DetailRoute
    data object Security : DetailRoute
    data object Backup : DetailRoute
    data object NetworkSettings : DetailRoute
    data object Logs : DetailRoute
    data object PluginRepositories : DetailRoute
    data class SeeAll(val kind: app.kodex.client.ui.catalog.SeeAllKind) : DetailRoute
    data class Migrate(
        val seriesId: String,
        val providerId: String,
        val sourceSeriesId: String,
        val title: String,
    ) : DetailRoute
}

/** Opens a streamed (no-download) chapter reader. */
typealias OpenSourceReader = (providerId: String, chapterId: String, seriesId: String?, chapterName: String?) -> Unit

/**
 * A Browse source series' own identity, carried into the streamed reader. It lets the reader build
 * prev/next navigation from the source's live chapter list, scope its display settings, and cache the
 * series title/cover on the progress record so History can render the entry — none of which needs the
 * series to be in a library.
 */
data class SourceSeriesContext(
    val providerId: String,
    val externalId: String,
    val title: String,
    val coverUrl: String? = null,
    /** BOOK-kind sources stream novel text, so their chapters open in the ebook reader, not the image one. */
    val isNovel: Boolean = false,
)

/** Opens a streamed chapter of a source series being browsed (not followed into a library). */
typealias OpenBrowseReader = (source: SourceSeriesContext, chapterId: String, chapterName: String?) -> Unit

@Composable
fun DetailHost(
    route: DetailRoute,
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    onOpenSeries: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenLibrary: (LibraryDto) -> Unit,
    onOpenSourceSeries: (SourceDescriptor, SourceSearchResult) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenReaderAt: (bookId: String, page: Int) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader,
    onOpenReaderIncognito: (String) -> Unit,
    onOpenSourceReaderIncognito: OpenSourceReader,
    onOpenBrowseReaderIncognito: OpenBrowseReader,
    onOpenMigrate: (seriesId: String, providerId: String, sourceSeriesId: String, title: String) -> Unit,
    onOpenPluginRepositories: () -> Unit,
    /** Swap the open reader for the series it belongs to (local series detail, or a source series). */
    onOpenSeriesFromReader: (DetailRoute) -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        is DetailRoute.LibrarySeries ->
            LibrarySeriesScreen(session, api, appSettings, route.library, onBack, onOpenSeries = { onOpenSeries(it.id) })

        is DetailRoute.SourceFeed ->
            SourceFeedScreen(session, api, route.source, onBack, onOpenSourceSeries = { onOpenSourceSeries(route.source, it) }, initialFeed = route.feed)

        is DetailRoute.SourceSeries ->
            SourceSeriesScreen(
                session, api, route.source, route.seed, onBack,
                onOpenReader = onOpenBrowseReader,
                onOpenReaderIncognito = onOpenBrowseReaderIncognito,
            )

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
            ReaderScreen(session, api, route.bookId, onBack, route.startPage, route.incognito, onOpenSeriesFromReader)

        is DetailRoute.SourceReader ->
            SourceReaderScreen(
                session, api, route.providerId, route.chapterId, route.seriesId, route.chapterName,
                onBack, route.sourceSeries, route.incognito, onOpenSeriesFromReader,
            )

        is DetailRoute.Downloads ->
            DownloadsScreen(session, api, onBack)

        is DetailRoute.Settings ->
            SettingsScreen(session, api, onBack)

        is DetailRoute.ServerConnection ->
            app.kodex.client.ui.settings.ServerConnectionScreen(session, onBack)

        is DetailRoute.Appearance ->
            AppearanceScreen(appSettings, onBack)

        is DetailRoute.About ->
            AboutScreen(onBack)

        is DetailRoute.Libraries ->
            LibrariesScreen(session, api, onBack, onOpenLibrary = onOpenLibrary)

        is DetailRoute.Labels ->
            LabelsScreen(session, api, onBack)

        is DetailRoute.Plugins ->
            PluginsScreen(session, api, onBack, onOpenRepositories = onOpenPluginRepositories)

        is DetailRoute.PluginRepositories ->
            PluginRepositoriesScreen(session, api, onBack)

        is DetailRoute.Users ->
            UsersScreen(session, api, onBack)

        is DetailRoute.Tasks ->
            TasksScreen(session, api, onBack)

        is DetailRoute.ServerActions ->
            ServerActionsScreen(session, api, onBack)

        is DetailRoute.Security ->
            app.kodex.client.ui.settings.SecurityScreen(session, api, onBack)

        is DetailRoute.Backup ->
            BackupScreen(session, api, onBack)

        is DetailRoute.NetworkSettings ->
            NetworkSettingsScreen(session, api, onBack)

        is DetailRoute.Logs ->
            LogsScreen(session, api, onBack)

        is DetailRoute.SeeAll ->
            app.kodex.client.ui.catalog.SeeAllScreen(
                session, api, route.kind, onBack,
                onOpenSeries = { onOpenSeries(it.id) },
                onOpenBook = { onOpenBook(it.id) },
            )

        is DetailRoute.Migrate ->
            MigrateScreen(session, api, route.seriesId, route.providerId, route.sourceSeriesId, route.title, onBack)
    }
}

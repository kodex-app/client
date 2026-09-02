package dev.icedtea.kodex.ui.main

import androidx.compose.runtime.Composable
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.SourceSearchResult
import dev.icedtea.kodex.ui.browse.SourceFeedScreen
import dev.icedtea.kodex.ui.browse.SourceSeriesScreen
import dev.icedtea.kodex.ui.downloads.DownloadsScreen
import dev.icedtea.kodex.ui.library.LibrarySeriesScreen
import dev.icedtea.kodex.ui.reader.ReaderScreen
import dev.icedtea.kodex.ui.reader.SourceReaderScreen
import dev.icedtea.kodex.ui.series.SeriesDetailScreen
import dev.icedtea.kodex.ui.manage.LabelsScreen
import dev.icedtea.kodex.ui.manage.LibrariesScreen
import dev.icedtea.kodex.ui.manage.MigrateScreen
import dev.icedtea.kodex.ui.manage.BackupScreen
import dev.icedtea.kodex.ui.manage.LogsScreen
import dev.icedtea.kodex.ui.manage.NetworkSettingsScreen
import dev.icedtea.kodex.ui.manage.PluginRepositoriesScreen
import dev.icedtea.kodex.ui.manage.PluginsScreen
import dev.icedtea.kodex.ui.manage.ServerActionsScreen
import dev.icedtea.kodex.ui.manage.TasksScreen
import dev.icedtea.kodex.ui.manage.UsersScreen
import dev.icedtea.kodex.ui.settings.AboutScreen
import dev.icedtea.kodex.ui.settings.AppearanceScreen
import dev.icedtea.kodex.ui.settings.SettingsScreen

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
    data class SeeAll(val kind: dev.icedtea.kodex.ui.catalog.SeeAllKind) : DetailRoute
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
    /** Opens the book-detail bottom sheet (books have no screen of their own — tapping one reads it). */
    onShowBookDetails: (String) -> Unit,
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
                onShowBookDetails = onShowBookDetails,
                onOpenReader = onOpenReader,
                onOpenSourceReader = onOpenSourceReader,
                onOpenMigrate = onOpenMigrate,
                onOpenReaderAt = onOpenReaderAt,
                onOpenSeries = onOpenSeries,
                onOpenReaderIncognito = onOpenReaderIncognito,
                onOpenSourceReaderIncognito = onOpenSourceReaderIncognito,
            )

        is DetailRoute.Reader ->
            ReaderScreen(session, api, appSettings, route.bookId, onBack, route.startPage, route.incognito, onOpenSeriesFromReader)

        is DetailRoute.SourceReader ->
            SourceReaderScreen(
                session, api, appSettings, route.providerId, route.chapterId, route.seriesId, route.chapterName,
                onBack, route.sourceSeries, route.incognito, onOpenSeriesFromReader,
            )

        is DetailRoute.Downloads ->
            DownloadsScreen(session, api, onBack)

        is DetailRoute.Settings ->
            SettingsScreen(session, api, onBack)

        is DetailRoute.ServerConnection ->
            dev.icedtea.kodex.ui.settings.ServerConnectionScreen(session, onBack)

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
            dev.icedtea.kodex.ui.settings.SecurityScreen(session, api, onBack)

        is DetailRoute.Backup ->
            BackupScreen(session, api, onBack)

        is DetailRoute.NetworkSettings ->
            NetworkSettingsScreen(session, api, onBack)

        is DetailRoute.Logs ->
            LogsScreen(session, api, onBack)

        is DetailRoute.SeeAll ->
            dev.icedtea.kodex.ui.catalog.SeeAllScreen(
                session, api, route.kind, onBack,
                onOpenSeries = onOpenSeries,
                onOpenBook = onOpenBook,
                onShowBookDetails = onShowBookDetails,
                onOpenSourceReader = onOpenSourceReader,
                onOpenBrowseReader = onOpenBrowseReader,
            )

        is DetailRoute.Migrate ->
            MigrateScreen(session, api, route.seriesId, route.providerId, route.sourceSeriesId, route.title, onBack)
    }
}

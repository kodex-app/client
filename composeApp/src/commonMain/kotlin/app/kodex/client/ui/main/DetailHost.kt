package app.kodex.client.ui.main

import androidx.compose.runtime.Composable
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.ui.book.BookDetailScreen
import app.kodex.client.ui.browse.SourceFeedScreen
import app.kodex.client.ui.browse.SourceSeriesScreen
import app.kodex.client.ui.library.LibrarySeriesScreen
import app.kodex.client.ui.reader.ReaderScreen
import app.kodex.client.ui.series.SeriesDetailScreen

/**
 * Minimal in-app navigation: full-screen "detail" routes pushed onto a back stack over the tab
 * scaffold (bottom nav hidden), popped with back. Deliberately lightweight — grows a case per
 * drill-down until a real nav library is warranted (the reader is the likely trigger).
 */
sealed interface DetailRoute {
    data class LibrarySeries(val library: LibraryDto) : DetailRoute
    data class SourceFeed(val source: SourceDescriptor) : DetailRoute
    data class SourceSeries(val source: SourceDescriptor, val seed: SourceSearchResult) : DetailRoute
    data class SeriesDetail(val seriesId: String) : DetailRoute
    data class BookDetail(val bookId: String) : DetailRoute
    data class Reader(val bookId: String) : DetailRoute
}

@Composable
fun DetailHost(
    route: DetailRoute,
    session: SessionManager,
    api: KodexApi,
    onOpenSeries: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSourceSeries: (SourceDescriptor, SourceSearchResult) -> Unit,
    onOpenReader: (String) -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        is DetailRoute.LibrarySeries ->
            LibrarySeriesScreen(session, api, route.library, onBack, onOpenSeries = { onOpenSeries(it.id) })

        is DetailRoute.SourceFeed ->
            SourceFeedScreen(session, api, route.source, onBack, onOpenSourceSeries = { onOpenSourceSeries(route.source, it) })

        is DetailRoute.SourceSeries ->
            SourceSeriesScreen(session, api, route.source, route.seed, onBack)

        is DetailRoute.SeriesDetail ->
            SeriesDetailScreen(session, api, route.seriesId, onBack, onOpenBook = onOpenBook)

        is DetailRoute.BookDetail ->
            BookDetailScreen(session, api, route.bookId, onBack, onRead = onOpenReader)

        is DetailRoute.Reader ->
            ReaderScreen(session, api, route.bookId, onBack)
    }
}

package app.kodex.client.ui.catalog

import app.kodex.client.network.BookDto
import app.kodex.client.network.KeepReadingDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.main.OpenBrowseReader
import app.kodex.client.ui.main.SourceSeriesContext
import io.ktor.http.encodeURLParameter

/** Book thumbnail endpoint. */
fun bookCoverUrl(baseUrl: String, bookId: String): String =
    "$baseUrl/api/v1/books/$bookId/thumbnail"

/** Rendered page image for the reader. [page] is 1-based (comics/DIVINA + PDF; EPUB is not an image). */
fun bookPageUrl(baseUrl: String, bookId: String, page: Int): String =
    "$baseUrl/api/v1/books/$bookId/pages/$page"

/**
 * Page image streamed live from a content source. [index] is 0-based (unlike book pages).
 *
 * [chapterId] goes through [encodeURLParameter], not `encodeURLQueryComponent`: the latter leaves
 * RFC 3986's reserved characters (`/`, `:`, …) literal, which is legal in a query but trips
 * path-traversal rules in reverse proxies sitting in front of a server. nhentai chapter ids are
 * `/g/<id>/`, so every page request carried bare slashes and came back 403 — while the same URL
 * worked against a directly-exposed server, which is what hid this.
 */
fun sourcePageUrl(baseUrl: String, providerId: String, chapterId: String, index: Int): String =
    "$baseUrl/api/v1/content-sources/$providerId/page?chapterId=${chapterId.encodeURLParameter()}&index=$index"

/**
 * Series cover, mirroring the web `coverSrc`: a custom [SeriesDto.coverUrl] wins (absolute URLs are
 * used as-is, server-relative ones are prefixed), otherwise fall back to the series thumbnail.
 */
fun seriesCoverUrl(baseUrl: String, series: SeriesDto): String =
    seriesCoverUrl(baseUrl, series.id, series.coverUrl)

fun seriesCoverUrl(baseUrl: String, seriesId: String, coverUrl: String?): String = when {
    coverUrl.isNullOrBlank() -> "$baseUrl/api/v1/series/$seriesId/thumbnail"
    coverUrl.startsWith("/") -> "$baseUrl$coverUrl"
    else -> coverUrl
}

/**
 * Source-series cover, routed through the core's proxy (which sends the source's Referer so
 * hotlink-protected images load). Empty when the source gave no cover URL.
 */
fun sourceCoverUrl(baseUrl: String, providerId: String, coverUrl: String?): String {
    if (coverUrl.isNullOrBlank()) return ""
    return "$baseUrl/api/v1/content-sources/$providerId/cover?url=${coverUrl.encodeURLParameter()}"
}

/**
 * Cover for a "Continue reading" entry: a downloaded book's own cover, the followed series' cover, or
 * the source's — whichever the entry actually has. Mirrors History's `historyCover`, since the two
 * lists carry the same mix of local and streamed items.
 */
fun keepReadingCover(baseUrl: String, entry: KeepReadingDto): String = when {
    entry.isBook && entry.bookId != null -> bookCoverUrl(baseUrl, entry.bookId)
    entry.seriesId != null && entry.coverUrl.isNullOrBlank() -> seriesCoverUrl(baseUrl, entry.seriesId, null)
    else -> sourceCoverUrl(baseUrl, entry.providerId ?: "", entry.coverUrl)
}

/**
 * Where a "Continue reading" card goes. A downloaded book and a followed series both have a screen of
 * their own, so those open it; a chapter read straight from Browse has none — its series isn't in any
 * library — so it resumes in the reader, with the source's identity so chapter navigation still works.
 *
 * Shared by the Home rail and its "See all" so tapping the same entry does the same thing on both.
 */
fun openKeepReading(
    entry: KeepReadingDto,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenBrowseReader: OpenBrowseReader,
) {
    val provider = entry.providerId
    val chapter = entry.chapterId
    val sourceSeries = entry.sourceSeriesId
    when {
        entry.isBook && entry.bookId != null -> onOpenBook(entry.bookId)
        entry.seriesId != null -> onOpenSeries(entry.seriesId)
        provider == null || chapter == null || sourceSeries == null -> Unit
        else -> onOpenBrowseReader(
            SourceSeriesContext(provider, sourceSeries, entry.seriesName.ifBlank { entry.title.orEmpty() }, entry.coverUrl),
            chapter,
            entry.title,
        )
    }
}

/** Subtitle under a book cover, e.g. "24 pages". */
fun bookSubtitle(book: BookDto): String =
    "${book.pageCount} ${if (book.pageCount == 1) "page" else "pages"}"

/** Subtitle under a series cover: chapters for WEB series, otherwise book count. */
fun seriesSubtitle(series: SeriesDto): String =
    if (series.totalChapters != null) {
        "${series.totalChapters} ${if (series.totalChapters == 1) "chapter" else "chapters"}"
    } else {
        "${series.bookCount} ${if (series.bookCount == 1) "book" else "books"}"
    }

/**
 * Unread badge count for a series (ported from `seriesReadState`). WEB series with nothing
 * downloaded yet show no badge.
 */
fun seriesUnreadBadge(series: SeriesDto): Int {
    if (series.totalChapters != null && series.bookCount == 0) return 0
    return series.unreadCount.coerceAtLeast(0)
}

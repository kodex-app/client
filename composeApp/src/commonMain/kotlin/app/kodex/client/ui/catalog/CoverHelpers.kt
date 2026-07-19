package app.kodex.client.ui.catalog

import app.kodex.client.network.BookDto
import app.kodex.client.network.SeriesDto
import io.ktor.http.encodeURLQueryComponent

/** Book thumbnail endpoint. */
fun bookCoverUrl(baseUrl: String, bookId: String): String =
    "$baseUrl/api/v1/books/$bookId/thumbnail"

/** Rendered page image for the reader. [page] is 1-based (comics/DIVINA + PDF; EPUB is not an image). */
fun bookPageUrl(baseUrl: String, bookId: String, page: Int): String =
    "$baseUrl/api/v1/books/$bookId/pages/$page"

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
    return "$baseUrl/api/v1/content-sources/$providerId/cover?url=${coverUrl.encodeURLQueryComponent()}"
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

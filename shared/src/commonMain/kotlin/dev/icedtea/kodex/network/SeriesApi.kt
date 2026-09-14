package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

// One series or book: detail, chapters, read state, progress, bookmarks, metadata edits. Extensions on [KodexApi]; see that file for the auth model.

// ── Series / book detail ──────────────────────────────────────────────────────────────────────

suspend fun KodexApi.seriesDetail(baseUrl: String, apiKey: String, seriesId: String): SeriesDetailDto =
    client.get("$baseUrl/api/v1/series/$seriesId") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** All books in a series, ordered by number (LOCAL series). */
suspend fun KodexApi.seriesBooks(baseUrl: String, apiKey: String, seriesId: String): List<BookDto> =
    client.get("$baseUrl/api/v1/series/$seriesId/books") {
        header(HEADER_API_KEY, apiKey)
        parameter("size", SERIES_BOOKS_SIZE)
        parameter("sort", "number,asc")
    }.body<PageResponse<BookDto>>().content

/** A WEB series' tracked chapters (with read/downloaded state). */
suspend fun KodexApi.seriesChapters(baseUrl: String, apiKey: String, seriesId: String): List<SeriesChapterDto> =
    client.get("$baseUrl/api/v1/series/$seriesId/chapters") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** Mark the whole series read or unread (LOCAL books + WEB chapters). */
suspend fun KodexApi.markSeriesRead(baseUrl: String, apiKey: String, seriesId: String, read: Boolean) {
    client.post("$baseUrl/api/v1/series/$seriesId/mark-read") {
        header(HEADER_API_KEY, apiKey)
        parameter("read", read)
    }
}

/** Re-fetch a WEB series' chapter list from its source (discovers new chapters). */
suspend fun KodexApi.refreshSeriesChapters(baseUrl: String, apiKey: String, seriesId: String) {
    client.post("$baseUrl/api/v1/series/$seriesId/chapters/refresh") {
        header(HEADER_API_KEY, apiKey)
    }
}

/** Mark specific WEB chapters read/unread for the current user. */
suspend fun KodexApi.markChaptersRead(baseUrl: String, apiKey: String, seriesId: String, chapterIds: List<String>, read: Boolean) {
    client.post("$baseUrl/api/v1/series/$seriesId/chapters/mark-read") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MarkChaptersRequest(chapterIds, read))
    }
}

/** Re-run metadata providers for the series (updates title/summary/genres/etc.). */
suspend fun KodexApi.refreshSeriesMetadata(baseUrl: String, apiKey: String, seriesId: String) {
    client.post("$baseUrl/api/v1/series/$seriesId/metadata/refresh") {
        header(HEADER_API_KEY, apiKey)
    }
}

suspend fun KodexApi.book(baseUrl: String, apiKey: String, bookId: String): BookDto =
    client.get("$baseUrl/api/v1/books/$bookId") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** Partial-update a book's metadata (only non-null fields are applied server-side). */
suspend fun KodexApi.updateBookMetadata(baseUrl: String, apiKey: String, bookId: String, patch: UpdateBookMetadataRequest): BookDto =
    client.patch("$baseUrl/api/v1/books/$bookId/metadata") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()

/** Re-run content analysis for a book (recomputes media type / page count / thumbnail). */
suspend fun KodexApi.analyzeBook(baseUrl: String, apiKey: String, bookId: String) {
    client.post("$baseUrl/api/v1/books/$bookId/analyze") {
        header(HEADER_API_KEY, apiKey)
    }
}

/** Delete a book (and its file). */
suspend fun KodexApi.deleteBook(baseUrl: String, apiKey: String, bookId: String) {
    client.delete("$baseUrl/api/v1/books/$bookId") {
        header(HEADER_API_KEY, apiKey)
    }
}

/** Mark a book fully read (progress at last page, completed). */
suspend fun KodexApi.markBookRead(baseUrl: String, apiKey: String, book: BookDto) {
    client.patch("$baseUrl/api/v1/books/${book.id}/read-progress") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(ReadProgressDto(page = maxOf(1, book.pageCount), completed = true))
    }
}

/** Clear a book's read progress (mark unread). */
suspend fun KodexApi.markBookUnread(baseUrl: String, apiKey: String, bookId: String) {
    client.delete("$baseUrl/api/v1/books/$bookId/read-progress") {
        header(HEADER_API_KEY, apiKey)
    }
}

/**
 * Persist reader position: [page] is 1-based; [completed] is set at the last page. Reflowable
 * ebooks also pass [locator] (foliate CFI) and [fraction] so a resume lands on the exact spot
 * rather than a page number that means nothing once the text reflows.
 */
suspend fun KodexApi.saveReadProgress(
    baseUrl: String,
    apiKey: String,
    bookId: String,
    page: Int,
    completed: Boolean,
    locator: String? = null,
    fraction: Double? = null,
) {
    client.patch("$baseUrl/api/v1/books/$bookId/read-progress") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(ReadProgressDto(page = page, locator = locator, fraction = fraction, completed = completed))
    }
}

/** Saved position for a book, or null when the user has never opened it. */
suspend fun KodexApi.readProgress(baseUrl: String, apiKey: String, bookId: String): ReadProgressDto? {
    val text = client.get("$baseUrl/api/v1/books/$bookId/read-progress") {
        header(HEADER_API_KEY, apiKey)
    }.bodyAsText()
    return if (text.isBlank()) null else runCatching { json.decodeFromString<ReadProgressDto>(text) }.getOrNull()
}

// ── Series metadata edit (partial) ────────────────────────────────────────────────────────────

suspend fun KodexApi.updateSeriesMetadata(baseUrl: String, apiKey: String, seriesId: String, patch: UpdateSeriesMetadataRequest): SeriesDetailDto =
    client.patch("$baseUrl/api/v1/series/$seriesId/metadata") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()

// ── Bookmarks ─────────────────────────────────────────────────────────────────────────────────

suspend fun KodexApi.bookBookmarks(baseUrl: String, apiKey: String, bookId: String): List<BookmarkDto> =
    client.get("$baseUrl/api/v1/books/$bookId/bookmarks") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.addBookmark(baseUrl: String, apiKey: String, bookId: String, page: Int, label: String?): BookmarkDto =
    client.post("$baseUrl/api/v1/books/$bookId/bookmarks") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(CreateBookmarkRequest(page = page, label = label))
    }.body()

/** Bookmark a spot in a reflowable ebook — a foliate CFI plus the fraction it sits at. */
suspend fun KodexApi.addEbookBookmark(
    baseUrl: String,
    apiKey: String,
    bookId: String,
    locator: String?,
    fraction: Double?,
    label: String?,
): BookmarkDto =
    client.post("$baseUrl/api/v1/books/$bookId/bookmarks") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(CreateBookmarkRequest(locator = locator, fraction = fraction, label = label))
    }.body()

suspend fun KodexApi.deleteBookmark(baseUrl: String, apiKey: String, bookId: String, bookmarkId: String) {
    client.delete("$baseUrl/api/v1/books/$bookId/bookmarks/$bookmarkId") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.seriesBookmarks(baseUrl: String, apiKey: String, seriesId: String): List<SeriesBookmarkDto> =
    client.get("$baseUrl/api/v1/series/$seriesId/bookmarks") { header(HEADER_API_KEY, apiKey) }.body()

package app.kodex.client.network

import kotlinx.serialization.Serializable

/**
 * Spring `PageResponse<T>` — the server envelope is
 * `{content, page, size, totalElements, totalPages, first, last}`. [page] is the 0-based page
 * index and [last] flags the final page (both drive the infinite-list helper). [number] is kept as
 * an alias for older call sites that read the page index under that name.
 */
@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val page: Int = 0,
    val number: Int = 0,
    val size: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)

/**
 * Reader position. Image/PDF reads use [page] alone; reflowable ebooks additionally carry a foliate
 * CFI in [locator] (the exact spot) and [fraction] (0–1 through the book) — [page] stays a coarse
 * proxy so progress bars and "continue reading" work the same for every media type.
 */
@Serializable
data class ReadProgressDto(
    val page: Int = 0,
    val locator: String? = null,
    val fraction: Double? = null,
    val completed: Boolean = false,
)

@Serializable
data class AuthorDto(
    val name: String = "",
    val role: String = "",
)

/** A book/chapter. List rows use a subset; the detail screen uses the richer fields. */
@Serializable
data class BookDto(
    val id: String,
    val seriesId: String? = null,
    /** Which library it belongs to — lets Home honour the user's "hide from home" preference. */
    val libraryId: String? = null,
    val title: String = "",
    val summary: String = "",
    val number: Double = 0.0,
    val numberDisplay: String? = null,
    val mediaStatus: String? = null,
    val mediaType: String? = null,
    val pageCount: Int = 0,
    val fileSize: Long = 0,
    val releaseDate: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val readProgress: ReadProgressDto? = null,
    val isbn: String? = null,
    val identifiers: Map<String, String> = emptyMap(),
    val externalLinks: List<WebLinkDto> = emptyList(),
    /** Fields the user pinned; metadata providers skip these on refresh so manual edits survive. */
    val lockedFields: Set<String> = emptySet(),
) {
    val isReady: Boolean get() = mediaStatus == null || mediaStatus == "READY"
}

/**
 * One resumable item on Home's "Continue reading" rail: a downloaded book, or a chapter streamed
 * straight from a content source. [kind] picks what to open — `BOOK` by [bookId], `SOURCE` by
 * [providerId] + [chapterId]. A chapter read from Browse without following the series has no local
 * [seriesId], so it carries the source's own identity ([sourceSeriesId], [seriesName], [coverUrl])
 * instead. Entries are one per series, newest first.
 */
@Serializable
data class KeepReadingDto(
    val kind: String = "BOOK",
    val bookId: String? = null,
    val seriesId: String? = null,
    val sourceSeriesId: String? = null,
    val providerId: String? = null,
    val chapterId: String? = null,
    val title: String? = null,
    val seriesName: String = "",
    val coverUrl: String? = null,
    val libraryId: String? = null,
    val page: Int = 0,
    val pageCount: Int? = null,
    val readDate: String? = null,
) {
    val isBook: Boolean get() = kind == "BOOK"
}

/**
 * The whole Home screen (`GET /api/v1/home`) — every rail in one response, already scoped to what this
 * user wants to see. The server reads their hidden-library / hidden-source preferences itself, so the
 * client neither fetches those preferences nor filters rows after the fact.
 */
@Serializable
data class HomeDto(
    val keepReading: List<KeepReadingDto> = emptyList(),
    val recentSeries: List<SeriesDto> = emptyList(),
    val recentlyUpdatedSeries: List<SeriesDto> = emptyList(),
    val recentBooks: List<BookDto> = emptyList(),
)

/** A canonical "open on …" link derived from a book's identifiers. */
@Serializable
data class WebLinkDto(val label: String = "", val url: String = "")

/** A series. `totalChapters` is non-null only for WEB series and switches the subtitle to chapters. */
@Serializable
data class SeriesDto(
    val id: String,
    /** Which library it belongs to — lets Home honour the user's "hide from home" preference. */
    val libraryId: String? = null,
    val name: String = "",
    val title: String = "",
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val coverUrl: String? = null,
    val totalChapters: Int? = null,
    /** Non-null for a WEB (followed) series — which content source it is tracked from. */
    val sourceProviderId: String? = null,
)

/** Full series metadata for the detail screen (server `SeriesDetailDto`). */
@Serializable
data class SeriesDetailDto(
    val id: String,
    val libraryId: String? = null,
    val name: String = "",
    val title: String = "",
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val coverUrl: String? = null,
    val totalChapters: Int? = null,
    val status: String? = null,
    val summary: String = "",
    val publisher: String = "",
    val author: String = "",
    val artist: String = "",
    val language: String = "",
    val readingDirection: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Non-null for WEB (followed) series — switches the detail list from books to source chapters. */
    val sourceProviderId: String? = null,
    val sourceSeriesId: String? = null,
    val labels: List<LabelDto> = emptyList(),
    /** Fields the user pinned; metadata providers skip these on refresh so manual edits survive. */
    val lockedFields: Set<String> = emptySet(),
) {
    val isWeb: Boolean get() = sourceProviderId != null
}

/**
 * Partial book-metadata edit (`PATCH /books/{id}/metadata`). The server applies each field only when
 * non-null, so leave anything untouched as null. [number] and [releaseDate] are strings the server
 * parses ([releaseDate] as `yyyy-MM-dd`).
 */
@Serializable
data class UpdateBookMetadataRequest(
    val title: String? = null,
    val summary: String? = null,
    val number: String? = null,
    val releaseDate: String? = null,
    val isbn: String? = null,
    val authors: List<AuthorDto>? = null,
    val tags: List<String>? = null,
    /** Non-null replaces the whole locked set. */
    val lockedFields: List<String>? = null,
    val identifiers: Map<String, String>? = null,
)

/** A WEB series' stored chapter (tracked catalogue), with per-user read/downloaded state. */
@Serializable
data class SeriesChapterDto(
    val chapterId: String,
    val name: String? = null,
    val number: Double? = null,
    val releaseDate: String? = null,
    val downloaded: Boolean = false,
    val bookId: String? = null,
    val read: Boolean = false,
    val isNew: Boolean = false,
    val page: Int? = null,
    val volume: String? = null,
    val scanlator: String? = null,
)

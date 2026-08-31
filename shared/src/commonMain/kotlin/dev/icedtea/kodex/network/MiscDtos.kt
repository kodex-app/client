package dev.icedtea.kodex.network

import kotlinx.serialization.Serializable

/** A metadata label (`GET /api/v1/labels`). */
@Serializable
data class LabelDto(
    val id: String,
    val name: String = "",
)

/** A WEB category (`GET /api/v1/categories`). */
@Serializable
data class CategoryDto(
    val id: String,
    val name: String = "",
    val autoDownload: Boolean = false,
    val sortOrder: Int = 0,
    val seriesCount: Long = 0,
)

/** Body of `POST /api/v1/categories/assign` — bulk add/remove categories across series. */
@Serializable
data class AssignCategoriesRequest(
    val seriesIds: List<String>,
    val addCategoryIds: List<String> = emptyList(),
    val removeCategoryIds: List<String> = emptyList(),
)

/** Body of `POST /api/v1/series/move` — move one or more series to another library (same kind). */
@Serializable
data class MoveSeriesRequest(
    val seriesIds: List<String>,
    val targetLibraryId: String,
)

/** One group's live series count (`GET /api/v1/series/groups`); [key] is a status/providerId/categoryId. */
@Serializable
data class SeriesGroupCount(
    val key: String = "",
    val count: Long = 0,
)

/** A reading bookmark within a book (`GET /api/v1/books/{id}/bookmarks`). */
@Serializable
data class BookmarkDto(
    val id: String,
    val page: Int? = null,
    val locator: String? = null,
    val fraction: Double? = null,
    val label: String? = null,
    val createdDate: String? = null,
)

/** Body of `POST /api/v1/books/{id}/bookmarks`. */
@Serializable
data class CreateBookmarkRequest(
    val page: Int? = null,
    val locator: String? = null,
    val fraction: Double? = null,
    val label: String? = null,
)

/** One zip entry of an EPUB — foliate needs the name/size table up front to resolve hrefs. */
@Serializable
data class EbookEntryDto(val name: String = "", val size: Long = 0)

/** `GET /books/{id}/manifest` (and the source-chapter equivalent). */
@Serializable
data class EbookManifestDto(
    val mediaType: String = "",
    val pageCount: Int = 0,
    val entries: List<EbookEntryDto> = emptyList(),
)

/** A font the user uploaded on the server, offered by the ebook reader's font picker. */
@Serializable
data class CustomFontDto(
    val id: String,
    val family: String = "",
    /** File extension the server stored it under — woff2 / woff / ttf / otf. */
    val format: String = "woff2",
    val fileSize: Long = 0,
    val createdDate: String? = null,
)

/**
 * A reader font shipped with the server — the same six OFL faces the web reader offers. The catalog
 * (and every @font-face descriptor in it) comes from the server rather than a table of our own, so the
 * app renders exactly the font a book was configured with in the browser.
 */
@Serializable
data class BundledFontDto(
    /** Stored in prefs as `bundled:<id>`. */
    val id: String,
    /** CSS font-family to declare and apply. */
    val family: String = "",
    /** Generic family to fall back to until the woff2 has loaded. */
    val fallback: String = "serif",
    val faces: List<BundledFontFaceDto> = emptyList(),
)

/** One @font-face of a [BundledFontDto]; [file] is fetched through the reader host's font proxy. */
@Serializable
data class BundledFontFaceDto(
    val file: String,
    val weight: Int = 400,
    val style: String = "normal",
    /** Keeps the latin and vietnamese subsets from overriding one another. */
    val unicodeRange: String = "",
)

/** A series-level bookmark (aggregated across the series' books). */
@Serializable
data class SeriesBookmarkDto(
    val id: String,
    val bookId: String? = null,
    val bookName: String? = null,
    val bookNumber: Int? = null,
    val page: Int? = null,
    val locator: String? = null,
    val fraction: Double? = null,
    val label: String? = null,
    val createdDate: String? = null,
)

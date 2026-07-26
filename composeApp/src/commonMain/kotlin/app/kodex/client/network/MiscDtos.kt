package app.kodex.client.network

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

package app.kodex.client.network

import kotlinx.serialization.Serializable

/** Spring `PageResponse<T>` — we only need `content` for the Home rows. */
@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 0,
)

@Serializable
data class ReadProgressDto(
    val page: Int = 0,
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
) {
    val isReady: Boolean get() = mediaStatus == null || mediaStatus == "READY"
}

/** A series. `totalChapters` is non-null only for WEB series and switches the subtitle to chapters. */
@Serializable
data class SeriesDto(
    val id: String,
    val title: String = "",
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val coverUrl: String? = null,
    val totalChapters: Int? = null,
)

/** Full series metadata for the detail screen (server `SeriesDetailDto`). */
@Serializable
data class SeriesDetailDto(
    val id: String,
    val title: String = "",
    val bookCount: Int = 0,
    val unreadCount: Int = 0,
    val coverUrl: String? = null,
    val totalChapters: Int? = null,
    val summary: String = "",
    val publisher: String = "",
    val language: String = "",
    val readingDirection: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

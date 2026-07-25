package app.kodex.client.network

import kotlinx.serialization.Serializable

/**
 * DTOs for the Recents (Updates + History) and Downloads screens. Instants arrive as ISO-8601
 * strings (Jackson's default), kept as [String] and parsed for day-grouping / relative display.
 */

/**
 * A new chapter discovered at the source for a followed WEB series (`GET /api/v1/updates`,
 * newest first). [bookId] is non-null once the chapter has been downloaded → open the local reader;
 * otherwise stream it via [providerId] + [chapterId].
 */
@Serializable
data class UpdateDto(
    val id: String,
    val seriesId: String? = null,
    val seriesName: String = "",
    val coverUrl: String? = null,
    val providerId: String? = null,
    val chapterId: String? = null,
    val chapterName: String? = null,
    val foundDate: String? = null,
    val bookId: String? = null,
)

/**
 * One reading-history entry (`GET /api/v1/history`, most recent first). [kind] is `BOOK` (a
 * downloaded book → open the local reader with [bookId]) or `SOURCE` (a streamed chapter → re-open
 * the source reader with [providerId] + [chapterId]).
 */
@Serializable
data class HistoryEntryDto(
    val kind: String = "BOOK",
    val bookId: String? = null,
    val seriesId: String? = null,
    val sourceSeriesId: String? = null,
    val providerId: String? = null,
    val chapterId: String? = null,
    val title: String? = null,
    val seriesName: String = "",
    val coverUrl: String? = null,
    val completed: Boolean = false,
    val page: Int = 0,
    val readDate: String? = null,
) {
    val isBook: Boolean get() = kind == "BOOK"
}

/**
 * A content-source download job (`GET /api/v1/downloads`, newest first). [state] is one of
 * QUEUED / RUNNING / PAUSED / COMPLETED / FAILED / CANCELLED; [progress] is `0.0..1.0`.
 */
@Serializable
data class DownloadJobDto(
    val id: String,
    val providerId: String? = null,
    val libraryId: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val chapterName: String? = null,
    val state: String = "QUEUED",
    val progress: Double = 0.0,
    val message: String? = null,
    val createdDate: String? = null,
    val bookId: String? = null,
) {
    val isActive: Boolean get() = state == "QUEUED" || state == "RUNNING" || state == "PAUSED"
    val isFailed: Boolean get() = state == "FAILED"
    val isPaused: Boolean get() = state == "PAUSED"
}

/** `POST /api/v1/downloads/clear` → number of finished jobs removed. */
@Serializable
data class ClearedDto(val cleared: Int = 0)

/** `POST /api/v1/downloads/retry-failed` → number of failed jobs re-queued. */
@Serializable
data class RetriedDto(val retried: Int = 0)

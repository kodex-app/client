package app.kodex.client.network

import kotlinx.serialization.Serializable

/** A library (LOCAL folder scan or WEB content-source library). */
@Serializable
data class LibraryDto(
    val id: String,
    val name: String = "",
    val type: String = "LOCAL",
    val mediaKind: String? = null,
    val root: String? = null,
    val contentSourceId: String? = null,
    val lastRefreshedDate: String? = null,
    // Refresh schedule + scan settings, so the edit form can seed itself from the server's own values
    // instead of guessing defaults and silently overwriting whatever was configured elsewhere.
    val refreshInterval: String = "EVERY_6H",
    val refreshOnStartup: Boolean = false,
    val scanForceModifiedTime: Boolean = false,
    val scanCbx: Boolean = true,
    val scanPdf: Boolean = true,
    val scanEpub: Boolean = true,
    val scanDirectoryExclusions: Set<String> = emptySet(),
    val specialFolders: Set<String> = emptySet(),
    val autoDownload: Boolean = false,
) {
    val isWeb: Boolean get() = type == "WEB"

    /**
     * The media kind this library holds. A WEB library holds exactly one kind, and the server treats a
     * null/blank one (libraries created before mediaKind existed) as COMIC — mirror that here so
     * "which libraries can this take?" never offers a shelf the server will reject.
     */
    val kind: String get() = mediaKind?.takeIf { it.isNotBlank() } ?: MEDIA_KIND_COMIC
}

/** Page-image content (manga, comics, webtoons) — `MediaKind.COMIC` on the server. */
const val MEDIA_KIND_COMIC = "COMIC"

/** Reflowable text content (web/light novels, ebooks) — `MediaKind.BOOK` on the server. */
const val MEDIA_KIND_BOOK = "BOOK"

/** An installed content source (plugin) shown in Browse. */
@Serializable
data class SourceDescriptor(
    val id: String,
    val displayName: String = "",
    val supportsLatest: Boolean = false,
    val website: String? = null,
    val adultContent: Boolean = false,
    val language: String? = null,
    val kind: String = MEDIA_KIND_COMIC,
) {
    /** The kind of WEB library this source's series belong in (blank = the COMIC default). */
    val mediaKind: String get() = kind.takeIf { it.isNotBlank() } ?: MEDIA_KIND_COMIC
}

/** One series returned by a content source's search/browse feed or its detail endpoint. */
@Serializable
data class SourceSearchResult(
    val providerId: String? = null,
    val externalId: String = "",
    val title: String = "",
    val description: String? = null,
    val coverUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String> = emptyList(),
    val status: String = "UNKNOWN",
)

/** One page of a content-source feed (Tachiyomi/Mihon MangasPage). */
@Serializable
data class SeriesPage(
    val items: List<SourceSearchResult> = emptyList(),
    val hasNextPage: Boolean = false,
)

/** A chapter from a content source's live chapter list. */
@Serializable
data class SourceChapter(
    val externalId: String = "",
    val name: String = "",
    val number: Double? = null,
    val scanlator: String? = null,
    val releaseDate: String? = null,
    /** Free-form provider metadata; `volume` groups chapters under a section header. */
    val attributes: Map<String, String> = emptyMap(),
) {
    val volume: String? get() = attributes["volume"]?.takeIf { it.isNotBlank() }
}

/** Resolves a source series to its followed local series (present only when followed). */
@Serializable
data class FollowedSeriesRef(
    val seriesId: String,
    val libraryId: String,
)

/** Body of `POST /v1/libraries/{id}/web-series` (follow a source series into a WEB library). */
@Serializable
data class FollowWebSeriesRequest(
    val providerId: String,
    val externalId: String,
    val categoryIds: List<String>? = null,
)

/** Body of `.../web-series/{id}/download` — null [chapterIds] downloads all missing chapters. */
@Serializable
data class DownloadWebSeriesRequest(
    val chapterIds: List<String>? = null,
)

/** Result of `DELETE .../web-series/{id}/download` — how many downloaded chapters were removed. */
@Serializable
data class RemovedDownloadsDto(
    val removed: Int = 0,
)

/** Body of `POST /series/{id}/chapters/mark-read` — mark the given WEB chapters read/unread. */
@Serializable
data class MarkChaptersRequest(
    val chapterIds: List<String>,
    val read: Boolean,
)

/** `GET /content-sources/{id}/pages?chapterId=` — page count for a streamed chapter. */
@Serializable
data class PageCountDto(val pageCount: Int = 0)

/** Body of `PUT /content-sources/{id}/progress` — records streamed read progress (drives History). */
@Serializable
data class SaveSourceProgressRequest(
    val seriesId: String? = null,
    val sourceSeriesId: String? = null,
    val sourceSeriesName: String? = null,
    val sourceCoverUrl: String? = null,
    val chapterId: String,
    val chapterName: String? = null,
    val page: Int,
    val completed: Boolean,
)

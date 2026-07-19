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
) {
    val isWeb: Boolean get() = type == "WEB"
}

/** An installed content source (plugin) shown in Browse. */
@Serializable
data class SourceDescriptor(
    val id: String,
    val displayName: String = "",
    val supportsLatest: Boolean = false,
    val website: String? = null,
    val adultContent: Boolean = false,
    val language: String? = null,
    val kind: String = "COMIC",
)

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
)

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

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

/** One series returned by a content source's search/browse feed. */
@Serializable
data class SourceSearchResult(
    val providerId: String? = null,
    val externalId: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val author: String? = null,
    val status: String = "UNKNOWN",
)

/** One page of a content-source feed (Tachiyomi/Mihon MangasPage). */
@Serializable
data class SeriesPage(
    val items: List<SourceSearchResult> = emptyList(),
    val hasNextPage: Boolean = false,
)

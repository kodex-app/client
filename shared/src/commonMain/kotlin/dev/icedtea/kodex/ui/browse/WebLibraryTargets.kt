package dev.icedtea.kodex.ui.browse

import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.MEDIA_KIND_BOOK

/**
 * The WEB libraries a source's series may be followed into. A WEB library holds exactly one media kind
 * and the server rejects a follow whose source kind differs, so a comic source must never be offered
 * (or silently handed) the "Web (Books)" shelf, and vice versa.
 *
 * `/libraries/web` is only consulted as a fallback for an empty list: it always answers with the COMIC
 * shelf, auto-creating it, so it is kept for the kind it actually matches. An empty result means the user
 * owns no shelf of that kind — the caller says so instead of adding to the wrong one.
 */
suspend fun KodexApi.webLibraryTargets(baseUrl: String, apiKey: String, kind: String): List<LibraryDto> {
    val owned = runCatching { libraries(baseUrl, apiKey) }.getOrDefault(emptyList())
        .filter { it.isWeb && it.kind == kind }
    if (owned.isNotEmpty()) return owned
    val implicit = runCatching { webLibrary(baseUrl, apiKey) }.getOrNull()
    return if (implicit != null && implicit.kind == kind) listOf(implicit) else emptyList()
}

/** Lower-case label for a media kind, for messages like "No book web library to add to." */
fun mediaKindLabel(kind: String): String = if (kind == MEDIA_KIND_BOOK) "book" else "comic"

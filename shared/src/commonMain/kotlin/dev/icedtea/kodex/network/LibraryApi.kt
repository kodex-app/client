package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

// Libraries, series listing/filtering, labels and categories. Extensions on [KodexApi]; see that file for the auth model.

// ── Libraries ─────────────────────────────────────────────────────────────────────────────────

suspend fun KodexApi.libraries(baseUrl: String, apiKey: String): List<LibraryDto> =
    client.get("$baseUrl/api/v1/libraries") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/**
 * Series within one library (backs the Libraries drill-down). [sort] is a Spring sort expr
 * ("title,asc" | "createdDate,desc" | "lastModifiedDate,desc"); [readingStatus] optionally filters
 * to NOT_STARTED / IN_PROGRESS / COMPLETED.
 */
suspend fun KodexApi.seriesInLibrary(
    baseUrl: String,
    apiKey: String,
    libraryId: String,
    sort: String = "title,asc",
    readingStatus: String? = null,
): List<SeriesDto> =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("libraryId", libraryId)
        parameter("size", LIBRARY_SERIES_SIZE)
        parameter("sort", sort)
        if (readingStatus != null) parameter("readingStatus", readingStatus)
    }.body<PageResponse<SeriesDto>>().content

/** Queue a library refresh (filesystem scan for LOCAL, content-source update for WEB). [deep] also re-analyzes. */
suspend fun KodexApi.refreshLibrary(baseUrl: String, apiKey: String, libraryId: String, deep: Boolean = false) {
    client.post("$baseUrl/api/v1/libraries/$libraryId/refresh") {
        header(HEADER_API_KEY, apiKey)
        if (deep) parameter("deep", true)
    }
}

/**
 * The full series query behind the Library grid (with grouping filters) and faceted Search. Every
 * list param maps to a repeated query parameter; nulls/empties are omitted.
 */
suspend fun KodexApi.querySeries(
    baseUrl: String,
    apiKey: String,
    libraryId: String? = null,
    search: String? = null,
    sort: String = "title,asc",
    genres: List<String> = emptyList(),
    statuses: List<String> = emptyList(),
    readingStatuses: List<String> = emptyList(),
    languages: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    labelIds: List<String> = emptyList(),
    sources: List<String> = emptyList(),
    categoryIds: List<String> = emptyList(),
    readingStatusExcludes: List<String> = emptyList(),
    statusExcludes: List<String> = emptyList(),
    downloaded: Boolean? = null,
    size: Int = LIBRARY_SERIES_SIZE,
): List<SeriesDto> =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("size", size)
        parameter("sort", sort)
        if (libraryId != null) parameter("libraryId", libraryId)
        if (!search.isNullOrBlank()) parameter("search", search)
        genres.forEach { parameter("genre", it) }
        statuses.forEach { parameter("status", it) }
        readingStatuses.forEach { parameter("readingStatus", it) }
        languages.forEach { parameter("language", it) }
        tags.forEach { parameter("tag", it) }
        labelIds.forEach { parameter("labelId", it) }
        sources.forEach { parameter("source", it) }
        categoryIds.forEach { parameter("categoryId", it) }
        readingStatusExcludes.forEach { parameter("readingStatusExclude", it) }
        statusExcludes.forEach { parameter("statusExclude", it) }
        if (downloaded != null) parameter("downloaded", downloaded)
    }.body<PageResponse<SeriesDto>>().content

/**
 * The Libraries tab's tile: the [size] most recently touched series of a library, plus the library's
 * total. One request covers both the cover mosaic and the count — the count alone is
 * [seriesCountInLibrary], which is the same query with `size = 1` and the content thrown away.
 */
suspend fun KodexApi.libraryPreview(
    baseUrl: String,
    apiKey: String,
    libraryId: String,
    size: Int = 4,
): PageResponse<SeriesDto> =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("libraryId", libraryId)
        parameter("size", size)
        parameter("sort", "lastModifiedDate,desc")
    }.body()

/**
 * How many series a library holds. Asks for a single row and reads the page total, so it stays a
 * cheap count rather than pulling the whole library down to size it.
 */
suspend fun KodexApi.seriesCountInLibrary(baseUrl: String, apiKey: String, libraryId: String): Long =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("libraryId", libraryId)
        parameter("size", 1)
    }.body<PageResponse<SeriesDto>>().totalElements

/**
 * Live per-group counts for the Library grouping tabs. [groupBy] is status | source.
 *
 * [categoryId] scopes the counts to one category, so the tabs agree with the grid when the
 * category chip filter is narrowing it (grouping and the category filter combine).
 */
suspend fun KodexApi.seriesGroups(
    baseUrl: String,
    apiKey: String,
    groupBy: String,
    libraryId: String? = null,
    categoryId: String? = null,
): List<SeriesGroupCount> =
    client.get("$baseUrl/api/v1/series/groups") {
        header(HEADER_API_KEY, apiKey)
        parameter("groupBy", groupBy)
        if (libraryId != null) parameter("libraryId", libraryId)
        if (categoryId != null) parameter("categoryId", categoryId)
    }.body()

/** Sub-series of a parent series (LOCAL nested libraries). */
suspend fun KodexApi.subSeries(baseUrl: String, apiKey: String, parentId: String): List<SeriesDto> =
    client.get("$baseUrl/api/v1/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("parentId", parentId)
        parameter("size", 200)
        parameter("sort", "title,asc")
    }.body<PageResponse<SeriesDto>>().content

/** Re-analyze every book in a series. */
suspend fun KodexApi.analyzeSeries(baseUrl: String, apiKey: String, seriesId: String) {
    client.post("$baseUrl/api/v1/series/$seriesId/analyze") { header(HEADER_API_KEY, apiKey) }
}

// ── Facet vocab + labels + categories ─────────────────────────────────────────────────────────

suspend fun KodexApi.seriesGenres(baseUrl: String, apiKey: String): List<String> =
    client.get("$baseUrl/api/v1/series/genres") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.seriesTags(baseUrl: String, apiKey: String): List<String> =
    client.get("$baseUrl/api/v1/series/tags") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.seriesLanguages(baseUrl: String, apiKey: String): List<String> =
    client.get("$baseUrl/api/v1/series/languages") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.labels(baseUrl: String, apiKey: String): List<LabelDto> =
    client.get("$baseUrl/api/v1/labels") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.categories(baseUrl: String, apiKey: String): List<CategoryDto> =
    client.get("$baseUrl/api/v1/categories") { header(HEADER_API_KEY, apiKey) }.body()

/** Bulk add/remove categories across series. */
suspend fun KodexApi.assignCategories(baseUrl: String, apiKey: String, seriesIds: List<String>, add: List<String>, remove: List<String>) {
    client.post("$baseUrl/api/v1/categories/assign") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(AssignCategoriesRequest(seriesIds, add, remove))
    }
}

// ── Libraries CRUD (Phase 3) ──────────────────────────────────────────────────────────────────

suspend fun KodexApi.createLibrary(baseUrl: String, apiKey: String, request: CreateLibraryRequest): LibraryDto =
    client.post("$baseUrl/api/v1/libraries") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

suspend fun KodexApi.updateLibrary(baseUrl: String, apiKey: String, libraryId: String, request: UpdateLibraryRequest): LibraryDto =
    client.patch("$baseUrl/api/v1/libraries/$libraryId") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

suspend fun KodexApi.deleteLibrary(baseUrl: String, apiKey: String, libraryId: String, deleteFiles: Boolean = false) {
    client.delete("$baseUrl/api/v1/libraries/$libraryId") {
        header(HEADER_API_KEY, apiKey)
        parameter("deleteFiles", deleteFiles)
    }
}

suspend fun KodexApi.analyzeLibrary(baseUrl: String, apiKey: String, libraryId: String) {
    client.post("$baseUrl/api/v1/libraries/$libraryId/analyze") { header(HEADER_API_KEY, apiKey) }
}

/** Admin folder picker: list directories under [path] (null = roots). */
suspend fun KodexApi.listDirectory(baseUrl: String, apiKey: String, path: String?): DirectoryListing =
    client.get("$baseUrl/api/v1/filesystem") {
        header(HEADER_API_KEY, apiKey)
        if (path != null) parameter("path", path)
    }.body()

// ── Labels CRUD (Phase 3) ─────────────────────────────────────────────────────────────────────

suspend fun KodexApi.createLabel(baseUrl: String, apiKey: String, name: String): LabelDto =
    client.post("$baseUrl/api/v1/labels") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(LabelRequest(name))
    }.body()

suspend fun KodexApi.renameLabel(baseUrl: String, apiKey: String, labelId: String, name: String): LabelDto =
    client.patch("$baseUrl/api/v1/labels/$labelId") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(LabelRequest(name))
    }.body()

suspend fun KodexApi.deleteLabel(baseUrl: String, apiKey: String, labelId: String) {
    client.delete("$baseUrl/api/v1/labels/$labelId") { header(HEADER_API_KEY, apiKey) }
}

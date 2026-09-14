package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

// Content sources: browsing, following into WEB libraries, reading straight from a source, migration. Extensions on [KodexApi]; see that file for the auth model.

// ── Browse (content sources) ──────────────────────────────────────────────────────────────────

suspend fun KodexApi.contentSources(baseUrl: String, apiKey: String): List<SourceDescriptor> =
    client.get("$baseUrl/api/v1/content-sources") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** A source's default filter list (rendered in the filter sheet, then posted back with a search). */
suspend fun KodexApi.sourceFilters(baseUrl: String, apiKey: String, sourceId: String): FilterListDto =
    client.get("$baseUrl/api/v1/content-sources/$sourceId/filters") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** Search a source. [page] is 1-based; [filters] carries the user-edited filter list (may be empty). */
suspend fun KodexApi.sourceSearch(
    baseUrl: String,
    apiKey: String,
    sourceId: String,
    query: String,
    page: Int,
    filters: FilterListDto = FilterListDto(),
): SeriesPage =
    client.post("$baseUrl/api/v1/content-sources/$sourceId/search") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(SourceSearchRequest(query = query, page = page, filters = filters))
    }.body()

/** A source's browse feed. [feed] is "popular" or "latest"; pages are 1-based. */
suspend fun KodexApi.sourceFeed(
    baseUrl: String,
    apiKey: String,
    sourceId: String,
    feed: String,
    page: Int,
): SeriesPage =
    client.get("$baseUrl/api/v1/content-sources/$sourceId/$feed") {
        header(HEADER_API_KEY, apiKey)
        parameter("page", page)
    }.body()

// ── Source series detail (Browse drill-down) ──────────────────────────────────────────────────

suspend fun KodexApi.sourceSeries(baseUrl: String, apiKey: String, providerId: String, externalId: String): SourceSearchResult =
    client.get("$baseUrl/api/v1/content-sources/$providerId/series") {
        header(HEADER_API_KEY, apiKey)
        parameter("id", externalId)
    }.body()

suspend fun KodexApi.sourceChapters(baseUrl: String, apiKey: String, providerId: String, externalId: String): List<SourceChapter> =
    client.get("$baseUrl/api/v1/content-sources/$providerId/chapters") {
        header(HEADER_API_KEY, apiKey)
        parameter("seriesId", externalId)
    }.body()

/** The followed-series link for a source series, or null (404) when it isn't followed. */
suspend fun KodexApi.followedSeriesRef(baseUrl: String, apiKey: String, providerId: String, externalId: String): FollowedSeriesRef? =
    try {
        client.get("$baseUrl/api/v1/content-sources/$providerId/followed-series") {
            header(HEADER_API_KEY, apiKey)
            parameter("id", externalId)
        }.body()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

/** Move series to another library of the same kind (WEB↔WEB / LOCAL↔LOCAL). */
suspend fun KodexApi.moveSeries(baseUrl: String, apiKey: String, seriesIds: List<String>, targetLibraryId: String) {
    client.post("$baseUrl/api/v1/series/move") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MoveSeriesRequest(seriesIds, targetLibraryId))
    }
}

/** External ids from a source already followed into one of the user's libraries — the "in library" marks in Browse. */
suspend fun KodexApi.followedExternalIds(baseUrl: String, apiKey: String, providerId: String): List<String> =
    client.get("$baseUrl/api/v1/content-sources/$providerId/followed") {
        header(HEADER_API_KEY, apiKey)
    }.body()

/** The user's WEB library (auto-created server-side), used as the follow target. */
suspend fun KodexApi.webLibrary(baseUrl: String, apiKey: String): LibraryDto =
    client.get("$baseUrl/api/v1/libraries/web") {
        header(HEADER_API_KEY, apiKey)
    }.body()

suspend fun KodexApi.followWebSeries(baseUrl: String, apiKey: String, libraryId: String, providerId: String, externalId: String) {
    client.post("$baseUrl/api/v1/libraries/$libraryId/web-series") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(FollowWebSeriesRequest(providerId, externalId))
    }
}

/** Download chapters of a followed series (null = all missing). */
suspend fun KodexApi.downloadWebSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, chapterIds: List<String>? = null) {
    client.post("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/download") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(DownloadWebSeriesRequest(chapterIds))
    }
}

/**
 * Deletes every downloaded chapter of a followed series, keeping the follow. Read chapters stay read
 * (the server carries their progress over to the streamed records) and the chapters go back to
 * streaming from the source. Returns how many were removed.
 */
suspend fun KodexApi.removeWebSeriesDownloads(baseUrl: String, apiKey: String, libraryId: String, seriesId: String): Int =
    client.delete("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/download") {
        header(HEADER_API_KEY, apiKey)
    }.body<RemovedDownloadsDto>().removed

suspend fun KodexApi.unfollowWebSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, deleteFiles: Boolean) {
    client.delete("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId") {
        header(HEADER_API_KEY, apiKey)
        parameter("deleteFiles", deleteFiles)
    }
}

// ── Read-from-source (streamed chapter, no download) ──────────────────────────────────────────

/** Page count for a chapter streamed live from its source (0 if the source can't fetch it). */
suspend fun KodexApi.sourceChapterPageCount(baseUrl: String, apiKey: String, providerId: String, chapterId: String): Int =
    client.get("$baseUrl/api/v1/content-sources/$providerId/pages") {
        header(HEADER_API_KEY, apiKey)
        parameter("chapterId", chapterId)
    }.body<PageCountDto>().pageCount

/** Saved progress for a streamed chapter, or null (the endpoint returns an empty body when none). */
suspend fun KodexApi.sourceProgress(baseUrl: String, apiKey: String, providerId: String, chapterId: String): ReadProgressDto? {
    val text = client.get("$baseUrl/api/v1/content-sources/$providerId/progress") {
        header(HEADER_API_KEY, apiKey)
        parameter("chapterId", chapterId)
    }.bodyAsText()
    return if (text.isBlank()) null else runCatching { json.decodeFromString<ReadProgressDto>(text) }.getOrNull()
}

/**
 * Every chapter of a Browse (not-followed) source series the user has progress for, keyed by
 * chapter external id — drives the read marks and the resume action on the source series page.
 */
suspend fun KodexApi.sourceSeriesProgress(
    baseUrl: String,
    apiKey: String,
    providerId: String,
    sourceSeriesId: String,
): Map<String, ReadProgressDto> =
    client.get("$baseUrl/api/v1/content-sources/$providerId/series-progress") {
        header(HEADER_API_KEY, apiKey)
        parameter("sourceSeriesId", sourceSeriesId)
    }.body()

/**
 * Records streamed read progress (also drives History). [seriesId] is set for library WEB series;
 * Browse reads instead pass [sourceSeriesId] plus the cached [sourceSeriesName]/[sourceCoverUrl] so
 * History can render the entry without another source call.
 */
suspend fun KodexApi.saveSourceProgress(
    baseUrl: String,
    apiKey: String,
    providerId: String,
    chapterId: String,
    page: Int,
    completed: Boolean,
    seriesId: String? = null,
    chapterName: String? = null,
    sourceSeriesId: String? = null,
    sourceSeriesName: String? = null,
    sourceCoverUrl: String? = null,
) {
    client.put("$baseUrl/api/v1/content-sources/$providerId/progress") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(
            SaveSourceProgressRequest(
                seriesId = seriesId,
                sourceSeriesId = sourceSeriesId,
                sourceSeriesName = sourceSeriesName,
                sourceCoverUrl = sourceCoverUrl,
                chapterId = chapterId,
                chapterName = chapterName,
                page = page,
                completed = completed,
            ),
        )
    }
}

// ── Migration (Phase 3) ───────────────────────────────────────────────────────────────────────

suspend fun KodexApi.migrationCandidates(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, providerId: String, query: String?): List<SourceSearchResult> =
    client.get("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/migration-candidates") {
        header(HEADER_API_KEY, apiKey)
        parameter("providerId", providerId)
        if (!query.isNullOrBlank()) parameter("query", query)
    }.body()

suspend fun KodexApi.migrateSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, request: MigrateRequest): MigrationResultDto =
    client.post("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/migrate") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

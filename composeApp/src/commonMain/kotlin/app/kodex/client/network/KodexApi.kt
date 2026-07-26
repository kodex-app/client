package app.kodex.client.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Thin Kodex REST client. [baseUrl] is the normalized server root (no trailing slash); every call
 * takes it explicitly so one shared [client] serves any number of servers.
 *
 * Auth model (verified against the server's openapi.json):
 *  - Mint a key once with Basic auth: `POST /api/v1/api-keys` → [CreatedApiKeyDto.key].
 *  - Thereafter send that key as the `X-API-Key` header (this is the header-capable-client path;
 *    the cookie/session `/api/login/api-key` route is for the web UI).
 */
class KodexApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun createApiKey(
        baseUrl: String,
        email: String,
        password: String,
        comment: String,
    ): CreatedApiKeyDto {
        val basic = Base64.encode("$email:$password".encodeToByteArray())
        return client.post("$baseUrl/api/v1/api-keys") {
            header(HttpHeaders.Authorization, "Basic $basic")
            contentType(ContentType.Application.Json)
            setBody(CreateApiKeyRequest(comment))
        }.body()
    }

    suspend fun getMe(baseUrl: String, apiKey: String): UserDto =
        client.get("$baseUrl/api/v1/users/me") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    // ── Home rows ────────────────────────────────────────────────────────────────────────────────

    /** In-progress books — "Continue reading". */
    suspend fun keepReading(baseUrl: String, apiKey: String): List<BookDto> =
        client.get("$baseUrl/api/v1/books/keep-reading") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** Most recently added books. */
    suspend fun recentBooks(baseUrl: String, apiKey: String): List<BookDto> =
        client.get("$baseUrl/api/v1/books") {
            header(HEADER_API_KEY, apiKey)
            parameter("sort", "createdDate,desc")
            parameter("size", HOME_ROW_SIZE)
        }.body<PageResponse<BookDto>>().content

    /** Most recently added series. */
    suspend fun recentSeries(baseUrl: String, apiKey: String): List<SeriesDto> =
        series(baseUrl, apiKey, sort = "createdDate,desc")

    /** Series whose content changed most recently. */
    suspend fun recentlyUpdatedSeries(baseUrl: String, apiKey: String): List<SeriesDto> =
        series(baseUrl, apiKey, sort = "lastModifiedDate,desc")

    private suspend fun series(baseUrl: String, apiKey: String, sort: String): List<SeriesDto> =
        client.get("$baseUrl/api/v1/series") {
            header(HEADER_API_KEY, apiKey)
            parameter("sort", sort)
            parameter("size", HOME_ROW_SIZE)
        }.body<PageResponse<SeriesDto>>().content

    // ── Global search ────────────────────────────────────────────────────────────────────────────

    /** Full-text series search (library mode of the web's global search). */
    suspend fun searchSeries(baseUrl: String, apiKey: String, query: String): List<SeriesDto> =
        client.get("$baseUrl/api/v1/series") {
            header(HEADER_API_KEY, apiKey)
            parameter("search", query)
            parameter("size", SEARCH_SIZE)
            parameter("sort", "name,asc")
        }.body<PageResponse<SeriesDto>>().content

    /** Full-text book search. */
    suspend fun searchBooks(baseUrl: String, apiKey: String, query: String): List<BookDto> =
        client.get("$baseUrl/api/v1/books") {
            header(HEADER_API_KEY, apiKey)
            parameter("search", query)
            parameter("size", SEARCH_SIZE)
        }.body<PageResponse<BookDto>>().content

    // ── Libraries ────────────────────────────────────────────────────────────────────────────────

    suspend fun libraries(baseUrl: String, apiKey: String): List<LibraryDto> =
        client.get("$baseUrl/api/v1/libraries") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /**
     * Series within one library (backs the Libraries drill-down). [sort] is a Spring sort expr
     * ("title,asc" | "createdDate,desc" | "lastModifiedDate,desc"); [readingStatus] optionally filters
     * to NOT_STARTED / IN_PROGRESS / COMPLETED.
     */
    suspend fun seriesInLibrary(
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
    suspend fun refreshLibrary(baseUrl: String, apiKey: String, libraryId: String, deep: Boolean = false) {
        client.post("$baseUrl/api/v1/libraries/$libraryId/refresh") {
            header(HEADER_API_KEY, apiKey)
            if (deep) parameter("deep", true)
        }
    }

    /**
     * The full series query behind the Library grid (with grouping filters) and faceted Search. Every
     * list param maps to a repeated query parameter; nulls/empties are omitted.
     */
    suspend fun querySeries(
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
        }.body<PageResponse<SeriesDto>>().content

    /** Live per-group counts for the Library grouping tabs. [groupBy] is status | source | category. */
    suspend fun seriesGroups(baseUrl: String, apiKey: String, groupBy: String, libraryId: String? = null): List<SeriesGroupCount> =
        client.get("$baseUrl/api/v1/series/groups") {
            header(HEADER_API_KEY, apiKey)
            parameter("groupBy", groupBy)
            if (libraryId != null) parameter("libraryId", libraryId)
        }.body()

    /** Sub-series of a parent series (LOCAL nested libraries). */
    suspend fun subSeries(baseUrl: String, apiKey: String, parentId: String): List<SeriesDto> =
        client.get("$baseUrl/api/v1/series") {
            header(HEADER_API_KEY, apiKey)
            parameter("parentId", parentId)
            parameter("size", 200)
            parameter("sort", "title,asc")
        }.body<PageResponse<SeriesDto>>().content

    /** Re-analyze every book in a series. */
    suspend fun analyzeSeries(baseUrl: String, apiKey: String, seriesId: String) {
        client.post("$baseUrl/api/v1/series/$seriesId/analyze") { header(HEADER_API_KEY, apiKey) }
    }

    // ── Facet vocab + labels + categories ────────────────────────────────────────────────────────

    suspend fun seriesGenres(baseUrl: String, apiKey: String): List<String> =
        client.get("$baseUrl/api/v1/series/genres") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun seriesTags(baseUrl: String, apiKey: String): List<String> =
        client.get("$baseUrl/api/v1/series/tags") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun seriesLanguages(baseUrl: String, apiKey: String): List<String> =
        client.get("$baseUrl/api/v1/series/languages") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun labels(baseUrl: String, apiKey: String): List<LabelDto> =
        client.get("$baseUrl/api/v1/labels") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun categories(baseUrl: String, apiKey: String): List<CategoryDto> =
        client.get("$baseUrl/api/v1/categories") { header(HEADER_API_KEY, apiKey) }.body()

    // ── Bookmarks ────────────────────────────────────────────────────────────────────────────────

    suspend fun bookBookmarks(baseUrl: String, apiKey: String, bookId: String): List<BookmarkDto> =
        client.get("$baseUrl/api/v1/books/$bookId/bookmarks") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun addBookmark(baseUrl: String, apiKey: String, bookId: String, page: Int, label: String?): BookmarkDto =
        client.post("$baseUrl/api/v1/books/$bookId/bookmarks") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(CreateBookmarkRequest(page = page, label = label))
        }.body()

    suspend fun deleteBookmark(baseUrl: String, apiKey: String, bookId: String, bookmarkId: String) {
        client.delete("$baseUrl/api/v1/books/$bookId/bookmarks/$bookmarkId") { header(HEADER_API_KEY, apiKey) }
    }

    suspend fun seriesBookmarks(baseUrl: String, apiKey: String, seriesId: String): List<SeriesBookmarkDto> =
        client.get("$baseUrl/api/v1/series/$seriesId/bookmarks") { header(HEADER_API_KEY, apiKey) }.body()

    // ── Series metadata edit (partial) ─────────────────────────────────────────────────────────────

    suspend fun updateSeriesMetadata(baseUrl: String, apiKey: String, seriesId: String, patch: UpdateSeriesMetadataRequest): SeriesDetailDto =
        client.patch("$baseUrl/api/v1/series/$seriesId/metadata") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(patch)
        }.body()

    // ── Libraries CRUD (Phase 3) ─────────────────────────────────────────────────────────────────

    suspend fun createLibrary(baseUrl: String, apiKey: String, request: CreateLibraryRequest): LibraryDto =
        client.post("$baseUrl/api/v1/libraries") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateLibrary(baseUrl: String, apiKey: String, libraryId: String, request: UpdateLibraryRequest): LibraryDto =
        client.patch("$baseUrl/api/v1/libraries/$libraryId") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteLibrary(baseUrl: String, apiKey: String, libraryId: String, deleteFiles: Boolean = false) {
        client.delete("$baseUrl/api/v1/libraries/$libraryId") {
            header(HEADER_API_KEY, apiKey)
            parameter("deleteFiles", deleteFiles)
        }
    }

    suspend fun analyzeLibrary(baseUrl: String, apiKey: String, libraryId: String) {
        client.post("$baseUrl/api/v1/libraries/$libraryId/analyze") { header(HEADER_API_KEY, apiKey) }
    }

    /** Admin folder picker: list directories under [path] (null = roots). */
    suspend fun listDirectory(baseUrl: String, apiKey: String, path: String?): DirectoryListing =
        client.get("$baseUrl/api/v1/filesystem") {
            header(HEADER_API_KEY, apiKey)
            if (path != null) parameter("path", path)
        }.body()

    // ── Labels CRUD (Phase 3) ────────────────────────────────────────────────────────────────────

    suspend fun createLabel(baseUrl: String, apiKey: String, name: String): LabelDto =
        client.post("$baseUrl/api/v1/labels") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(LabelRequest(name))
        }.body()

    suspend fun renameLabel(baseUrl: String, apiKey: String, labelId: String, name: String): LabelDto =
        client.patch("$baseUrl/api/v1/labels/$labelId") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(LabelRequest(name))
        }.body()

    suspend fun deleteLabel(baseUrl: String, apiKey: String, labelId: String) {
        client.delete("$baseUrl/api/v1/labels/$labelId") { header(HEADER_API_KEY, apiKey) }
    }

    // ── Plugins (Phase 3) ────────────────────────────────────────────────────────────────────────

    suspend fun installedPlugins(baseUrl: String, apiKey: String): List<InstalledPluginDto> =
        client.get("$baseUrl/api/v1/plugins") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun availablePlugins(baseUrl: String, apiKey: String): List<AvailablePluginDto> =
        client.get("$baseUrl/api/v1/plugins/available") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun refreshAvailablePlugins(baseUrl: String, apiKey: String): List<AvailablePluginDto> =
        client.post("$baseUrl/api/v1/plugins/refresh-available") { header(HEADER_API_KEY, apiKey) }.body()

    suspend fun installPlugin(baseUrl: String, apiKey: String, pluginId: String, version: String): List<InstalledPluginDto> =
        client.post("$baseUrl/api/v1/plugins/install") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(InstallRequest(pluginId, version))
        }.body()

    suspend fun uninstallPlugin(baseUrl: String, apiKey: String, pluginId: String) {
        client.delete("$baseUrl/api/v1/plugins/$pluginId") { header(HEADER_API_KEY, apiKey) }
    }

    /** [action] is "enable", "disable", or "update". */
    suspend fun pluginAction(baseUrl: String, apiKey: String, pluginId: String, action: String) {
        client.post("$baseUrl/api/v1/plugins/$pluginId/$action") { header(HEADER_API_KEY, apiKey) }
    }

    suspend fun checkPluginUpdates(baseUrl: String, apiKey: String): PluginUpdateStatusDto =
        client.post("$baseUrl/api/v1/plugins/check-updates") { header(HEADER_API_KEY, apiKey) }.body()

    // ── Migration (Phase 3) ──────────────────────────────────────────────────────────────────────

    suspend fun migrationCandidates(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, providerId: String, query: String?): List<SourceSearchResult> =
        client.get("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/migration-candidates") {
            header(HEADER_API_KEY, apiKey)
            parameter("providerId", providerId)
            if (!query.isNullOrBlank()) parameter("query", query)
        }.body()

    suspend fun migrateSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, request: MigrateRequest): MigrationResultDto =
        client.post("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/migrate") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // ── Series / book detail ─────────────────────────────────────────────────────────────────────

    suspend fun seriesDetail(baseUrl: String, apiKey: String, seriesId: String): SeriesDetailDto =
        client.get("$baseUrl/api/v1/series/$seriesId") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** All books in a series, ordered by number (LOCAL series). */
    suspend fun seriesBooks(baseUrl: String, apiKey: String, seriesId: String): List<BookDto> =
        client.get("$baseUrl/api/v1/series/$seriesId/books") {
            header(HEADER_API_KEY, apiKey)
            parameter("size", SERIES_BOOKS_SIZE)
            parameter("sort", "number,asc")
        }.body<PageResponse<BookDto>>().content

    /** A WEB series' tracked chapters (with read/downloaded state). */
    suspend fun seriesChapters(baseUrl: String, apiKey: String, seriesId: String): List<SeriesChapterDto> =
        client.get("$baseUrl/api/v1/series/$seriesId/chapters") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** Mark the whole series read or unread (LOCAL books + WEB chapters). */
    suspend fun markSeriesRead(baseUrl: String, apiKey: String, seriesId: String, read: Boolean) {
        client.post("$baseUrl/api/v1/series/$seriesId/mark-read") {
            header(HEADER_API_KEY, apiKey)
            parameter("read", read)
        }
    }

    /** Re-fetch a WEB series' chapter list from its source (discovers new chapters). */
    suspend fun refreshSeriesChapters(baseUrl: String, apiKey: String, seriesId: String) {
        client.post("$baseUrl/api/v1/series/$seriesId/chapters/refresh") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    /** Mark specific WEB chapters read/unread for the current user. */
    suspend fun markChaptersRead(baseUrl: String, apiKey: String, seriesId: String, chapterIds: List<String>, read: Boolean) {
        client.post("$baseUrl/api/v1/series/$seriesId/chapters/mark-read") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(MarkChaptersRequest(chapterIds, read))
        }
    }

    /** Re-run metadata providers for the series (updates title/summary/genres/etc.). */
    suspend fun refreshSeriesMetadata(baseUrl: String, apiKey: String, seriesId: String) {
        client.post("$baseUrl/api/v1/series/$seriesId/metadata/refresh") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    suspend fun book(baseUrl: String, apiKey: String, bookId: String): BookDto =
        client.get("$baseUrl/api/v1/books/$bookId") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** Partial-update a book's metadata (only non-null fields are applied server-side). */
    suspend fun updateBookMetadata(baseUrl: String, apiKey: String, bookId: String, patch: UpdateBookMetadataRequest): BookDto =
        client.patch("$baseUrl/api/v1/books/$bookId/metadata") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(patch)
        }.body()

    /** Re-run content analysis for a book (recomputes media type / page count / thumbnail). */
    suspend fun analyzeBook(baseUrl: String, apiKey: String, bookId: String) {
        client.post("$baseUrl/api/v1/books/$bookId/analyze") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    /** Delete a book (and its file). */
    suspend fun deleteBook(baseUrl: String, apiKey: String, bookId: String) {
        client.delete("$baseUrl/api/v1/books/$bookId") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    /** Mark a book fully read (progress at last page, completed). */
    suspend fun markBookRead(baseUrl: String, apiKey: String, book: BookDto) {
        client.patch("$baseUrl/api/v1/books/${book.id}/read-progress") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(ReadProgressDto(page = maxOf(1, book.pageCount), completed = true))
        }
    }

    /** Clear a book's read progress (mark unread). */
    suspend fun markBookUnread(baseUrl: String, apiKey: String, bookId: String) {
        client.delete("$baseUrl/api/v1/books/$bookId/read-progress") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    /** Persist reader position: [page] is 1-based; [completed] is set at the last page. */
    suspend fun saveReadProgress(baseUrl: String, apiKey: String, bookId: String, page: Int, completed: Boolean) {
        client.patch("$baseUrl/api/v1/books/$bookId/read-progress") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(ReadProgressDto(page = page, completed = completed))
        }
    }

    // ── Browse (content sources) ──────────────────────────────────────────────────────────────────

    suspend fun contentSources(baseUrl: String, apiKey: String): List<SourceDescriptor> =
        client.get("$baseUrl/api/v1/content-sources") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** A source's default filter list (rendered in the filter sheet, then posted back with a search). */
    suspend fun sourceFilters(baseUrl: String, apiKey: String, sourceId: String): FilterListDto =
        client.get("$baseUrl/api/v1/content-sources/$sourceId/filters") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** Search a source. [page] is 1-based; [filters] carries the user-edited filter list (may be empty). */
    suspend fun sourceSearch(
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
    suspend fun sourceFeed(
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

    // ── Source series detail (Browse drill-down) ─────────────────────────────────────────────────

    suspend fun sourceSeries(baseUrl: String, apiKey: String, providerId: String, externalId: String): SourceSearchResult =
        client.get("$baseUrl/api/v1/content-sources/$providerId/series") {
            header(HEADER_API_KEY, apiKey)
            parameter("id", externalId)
        }.body()

    suspend fun sourceChapters(baseUrl: String, apiKey: String, providerId: String, externalId: String): List<SourceChapter> =
        client.get("$baseUrl/api/v1/content-sources/$providerId/chapters") {
            header(HEADER_API_KEY, apiKey)
            parameter("seriesId", externalId)
        }.body()

    /** The followed-series link for a source series, or null (404) when it isn't followed. */
    suspend fun followedSeriesRef(baseUrl: String, apiKey: String, providerId: String, externalId: String): FollowedSeriesRef? =
        try {
            client.get("$baseUrl/api/v1/content-sources/$providerId/followed-series") {
                header(HEADER_API_KEY, apiKey)
                parameter("id", externalId)
            }.body()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }

    /** The user's WEB library (auto-created server-side), used as the follow target. */
    suspend fun webLibrary(baseUrl: String, apiKey: String): LibraryDto =
        client.get("$baseUrl/api/v1/libraries/web") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    suspend fun followWebSeries(baseUrl: String, apiKey: String, libraryId: String, providerId: String, externalId: String) {
        client.post("$baseUrl/api/v1/libraries/$libraryId/web-series") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(FollowWebSeriesRequest(providerId, externalId))
        }
    }

    /** Download chapters of a followed series (null = all missing). */
    suspend fun downloadWebSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, chapterIds: List<String>? = null) {
        client.post("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId/download") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(DownloadWebSeriesRequest(chapterIds))
        }
    }

    suspend fun unfollowWebSeries(baseUrl: String, apiKey: String, libraryId: String, seriesId: String, deleteFiles: Boolean) {
        client.delete("$baseUrl/api/v1/libraries/$libraryId/web-series/$seriesId") {
            header(HEADER_API_KEY, apiKey)
            parameter("deleteFiles", deleteFiles)
        }
    }

    // ── Read-from-source (streamed chapter, no download) ─────────────────────────────────────────

    /** Page count for a chapter streamed live from its source (0 if the source can't fetch it). */
    suspend fun sourceChapterPageCount(baseUrl: String, apiKey: String, providerId: String, chapterId: String): Int =
        client.get("$baseUrl/api/v1/content-sources/$providerId/pages") {
            header(HEADER_API_KEY, apiKey)
            parameter("chapterId", chapterId)
        }.body<PageCountDto>().pageCount

    /** Saved progress for a streamed chapter, or null (the endpoint returns an empty body when none). */
    suspend fun sourceProgress(baseUrl: String, apiKey: String, providerId: String, chapterId: String): ReadProgressDto? {
        val text = client.get("$baseUrl/api/v1/content-sources/$providerId/progress") {
            header(HEADER_API_KEY, apiKey)
            parameter("chapterId", chapterId)
        }.bodyAsText()
        return if (text.isBlank()) null else runCatching { json.decodeFromString<ReadProgressDto>(text) }.getOrNull()
    }

    /** Records streamed read progress (also drives History). [seriesId] is set for library WEB series. */
    suspend fun saveSourceProgress(
        baseUrl: String,
        apiKey: String,
        providerId: String,
        chapterId: String,
        page: Int,
        completed: Boolean,
        seriesId: String? = null,
        chapterName: String? = null,
    ) {
        client.put("$baseUrl/api/v1/content-sources/$providerId/progress") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(SaveSourceProgressRequest(seriesId = seriesId, chapterId = chapterId, chapterName = chapterName, page = page, completed = completed))
        }
    }

    // ── Recents: Updates + History ───────────────────────────────────────────────────────────────

    /** One page of the Mihon-style Updates feed (new source chapters for followed WEB series). */
    suspend fun updates(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<UpdateDto> =
        client.get("$baseUrl/api/v1/updates") {
            header(HEADER_API_KEY, apiKey)
            parameter("page", page)
            parameter("size", size)
        }.body()

    /** One page of reading history (downloaded-book + streamed-source progress merged, newest first). */
    suspend fun history(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<HistoryEntryDto> =
        client.get("$baseUrl/api/v1/history") {
            header(HEADER_API_KEY, apiKey)
            parameter("page", page)
            parameter("size", size)
        }.body()

    /** Clears history within an optional `[from, to]` ISO-8601 window (omit both to clear everything). */
    suspend fun clearHistory(baseUrl: String, apiKey: String, from: String? = null, to: String? = null) {
        client.delete("$baseUrl/api/v1/history") {
            header(HEADER_API_KEY, apiKey)
            if (from != null) parameter("from", from)
            if (to != null) parameter("to", to)
        }
    }

    // ── Downloads ────────────────────────────────────────────────────────────────────────────────

    suspend fun downloads(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<DownloadJobDto> =
        client.get("$baseUrl/api/v1/downloads") {
            header(HEADER_API_KEY, apiKey)
            parameter("page", page)
            parameter("size", size)
        }.body()

    /** Per-job action: [action] is one of "cancel", "pause", "resume", "retry" (all 204). */
    suspend fun downloadAction(baseUrl: String, apiKey: String, jobId: String, action: String) {
        client.post("$baseUrl/api/v1/downloads/$jobId/$action") {
            header(HEADER_API_KEY, apiKey)
        }
    }

    suspend fun cancelAllDownloads(baseUrl: String, apiKey: String) {
        client.post("$baseUrl/api/v1/downloads/cancel-all") { header(HEADER_API_KEY, apiKey) }
    }

    suspend fun clearFinishedDownloads(baseUrl: String, apiKey: String): Int =
        client.post("$baseUrl/api/v1/downloads/clear") { header(HEADER_API_KEY, apiKey) }.body<ClearedDto>().cleared

    suspend fun retryFailedDownloads(baseUrl: String, apiKey: String): Int =
        client.post("$baseUrl/api/v1/downloads/retry-failed") { header(HEADER_API_KEY, apiKey) }.body<RetriedDto>().retried

    // ── Per-user settings (reader prefs live here) ───────────────────────────────────────────────

    /** All generic per-user settings as a JSON object (e.g. `reader.comic`, `reader.comic.series.<id>`). */
    suspend fun userSettings(baseUrl: String, apiKey: String): JsonObject {
        val text = client.get("$baseUrl/api/v1/users/me/settings") {
            header(HEADER_API_KEY, apiKey)
        }.bodyAsText()
        return if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
    }

    /** Upsert one opaque JSON setting (pass a JSON `null` value to clear it). */
    suspend fun saveUserSetting(baseUrl: String, apiKey: String, key: String, value: JsonElement) {
        client.put("$baseUrl/api/v1/users/me/settings/${key.encodeURLQueryComponent()}") {
            header(HEADER_API_KEY, apiKey)
            contentType(ContentType.Application.Json)
            setBody(value)
        }
    }

    private companion object {
        const val HEADER_API_KEY = "X-API-Key"
        const val HOME_ROW_SIZE = 20
        const val SEARCH_SIZE = 50
        const val LIBRARY_SERIES_SIZE = 200
        const val SERIES_BOOKS_SIZE = 1000
        const val PAGE_SIZE = 50
    }
}

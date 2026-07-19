package app.kodex.client.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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

    /** Series within one library, title-sorted (backs the Libraries drill-down grid). */
    suspend fun seriesInLibrary(baseUrl: String, apiKey: String, libraryId: String): List<SeriesDto> =
        client.get("$baseUrl/api/v1/series") {
            header(HEADER_API_KEY, apiKey)
            parameter("libraryId", libraryId)
            parameter("size", LIBRARY_SERIES_SIZE)
            parameter("sort", "title,asc")
        }.body<PageResponse<SeriesDto>>().content

    // ── Series / book detail ─────────────────────────────────────────────────────────────────────

    suspend fun seriesDetail(baseUrl: String, apiKey: String, seriesId: String): SeriesDetailDto =
        client.get("$baseUrl/api/v1/series/$seriesId") {
            header(HEADER_API_KEY, apiKey)
        }.body()

    /** All books in a series, ordered by number. */
    suspend fun seriesBooks(baseUrl: String, apiKey: String, seriesId: String): List<BookDto> =
        client.get("$baseUrl/api/v1/series/$seriesId/books") {
            header(HEADER_API_KEY, apiKey)
            parameter("size", SERIES_BOOKS_SIZE)
            parameter("sort", "number,asc")
        }.body<PageResponse<BookDto>>().content

    suspend fun book(baseUrl: String, apiKey: String, bookId: String): BookDto =
        client.get("$baseUrl/api/v1/books/$bookId") {
            header(HEADER_API_KEY, apiKey)
        }.body()

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

    // ── Browse (content sources) ──────────────────────────────────────────────────────────────────

    suspend fun contentSources(baseUrl: String, apiKey: String): List<SourceDescriptor> =
        client.get("$baseUrl/api/v1/content-sources") {
            header(HEADER_API_KEY, apiKey)
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

    private companion object {
        const val HEADER_API_KEY = "X-API-Key"
        const val HOME_ROW_SIZE = 20
        const val SEARCH_SIZE = 50
        const val LIBRARY_SERIES_SIZE = 200
        const val SERIES_BOOKS_SIZE = 1000
    }
}

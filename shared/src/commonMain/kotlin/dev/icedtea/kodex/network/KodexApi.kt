package dev.icedtea.kodex.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The auth header every authenticated call carries. */
const val HEADER_API_KEY = "X-API-Key"
internal const val HOME_ROW_SIZE = 20
internal const val SEARCH_SIZE = 50
internal const val LIBRARY_SERIES_SIZE = 200
internal const val SERIES_BOOKS_SIZE = 1000
internal const val PAGE_SIZE = 50

/**
 * Thin Kodex REST client. [baseUrl] is the normalized server root (no trailing slash); every call
 * takes it explicitly so one shared [client] serves any number of servers.
 *
 * Only sign-in lives here. The rest of the surface is extension functions grouped by screen area —
 * HomeApi, LibraryApi, SeriesApi, BrowseApi, ExtensionsApi, RecentsApi, ReaderApi, AdminApi — all in
 * this package, sharing the [client] and [json] below.
 *
 * Auth model (verified against the server's openapi.json):
 *  - Mint a key once with Basic auth: `POST /api/v1/api-keys` → [CreatedApiKeyDto.key].
 *  - Thereafter send that key as the `X-API-Key` header (this is the header-capable-client path;
 *    the cookie/session `/api/login/api-key` route is for the web UI).
 */
class KodexApi(internal val client: HttpClient) {

    internal val json = Json { ignoreUnknownKeys = true }

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
}

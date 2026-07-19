package app.kodex.client.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
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

    private companion object {
        const val HEADER_API_KEY = "X-API-Key"
    }
}

package app.kodex.client.data.model

import kotlinx.serialization.Serializable

/**
 * A saved connection to one Kodex server. The app supports several of these at once; the login
 * screen lists them and the most recently used one is selected by default on launch.
 *
 * [apiKey] is the raw `X-API-Key` value minted once via `POST /api/v1/api-keys` (Basic auth) and
 * sent as a header on every subsequent request — Kodex has no query-param auth. We persist it so
 * the user signs in per device, not per launch.
 */
@Serializable
data class ServerConnection(
    val id: String,
    val label: String,
    val baseUrl: String,
    val email: String,
    val apiKey: String,
    val lastUsedAt: Long = 0L,
) {
    /** Host (+ port) shown under the label in the picker, e.g. `media.example.com`. */
    val displayHost: String
        get() = baseUrl
            .substringAfter("://", baseUrl)
            .removeSuffix("/")
}

package dev.icedtea.kodex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Small alias so screens read a StateFlow without importing the flow-interop overload each time. */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsState()

/**
 * Turns transport/HTTP failures into one short, user-facing line.
 *
 * Nothing raw ever reaches the screen: Ktor's own exception message is the whole request line plus
 * the response body, so a reverse proxy answering 502 with an HTML page ends up printed as markup in
 * the middle of the UI. What a reader can act on is the *kind* of failure — the server is down, the
 * session expired, the network is unreachable — so that is all this says.
 *
 * Set [signIn] on the login screen, where a few codes mean something more specific than they do
 * anywhere else (401 is a typo'd password, 404 is an address that isn't a Kodex server at all).
 */
fun Throwable.friendlyMessage(signIn: Boolean = false): String = when (this) {
    is ResponseException -> httpMessage(response.status.value, signIn)

    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
        "The server took too long to respond. Check your connection and try again."

    else -> "Couldn't reach the server. Check the address and your connection."
}

/** One line per HTTP status: what happened, then the code for anyone reporting it. */
private fun httpMessage(code: Int, signIn: Boolean): String = when (code) {
    400 -> "The server rejected the request (400)."
    401 -> if (signIn) "Wrong email or password." else "Your session has expired (401). Sign in again."
    403 -> if (signIn) "This account isn't allowed to sign in." else "You don't have access to this (403)."
    404 -> if (signIn) "That doesn't look like a Kodex server." else "Not found on the server (404)."
    408 -> "The request timed out (408). Try again."
    409 -> "That conflicts with something already on the server (409)."
    413 -> "That's too large for the server to accept (413)."
    429 -> "Too many requests (429). Wait a moment and try again."
    500 -> "Server error (500). Something went wrong on the server."
    502 -> "Bad gateway (502). The server isn't answering right now."
    503 -> "The server is unavailable (503). It may be restarting or under maintenance."
    504 -> "Gateway timeout (504). The server took too long to answer."
    in 500..599 -> "Server error ($code)."
    else -> "Request failed ($code)."
}

private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * The technical truth about a failed call: HTTP status plus the server's RFC-7807 `detail` and `code`
 * when it sent a problem document, else the transport failure itself.
 *
 * [friendlyMessage] is tuned for a reader who just wants to know whether to retry — it collapses
 * everything into one plain line, which is exactly wrong on a screen whose whole job is to say *why*
 * a source didn't load. Even here the body is stripped of markup first, since an error page from
 * something in front of the server is HTML, not a message.
 */
suspend fun Throwable.serverErrorDetail(): String = when (this) {
    is ResponseException -> {
        // The default response validator saves the call before throwing, so the body is re-readable.
        val body = runCatching { response.bodyAsText() }.getOrDefault("").asPlainText()
        val problem = runCatching { problemJson.parseToJsonElement(body).jsonObject }.getOrNull()
        fun field(name: String) = problem?.get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val detail = field("detail") ?: field("title") ?: body.take(300).ifBlank { null }
        buildString {
            append("HTTP ${response.status.value} ${response.status.description}")
            if (detail != null) append(" — ").append(detail)
            field("code")?.let { append(" [").append(it).append("]") }
        }
    }

    else -> friendlyMessage()
}

private val tagRe = Regex("<[^>]*>")
private val spaceRe = Regex("""\s+""")

/** Response body as one readable line: markup dropped, whitespace collapsed. */
private fun String.asPlainText(): String =
    replace(tagRe, " ").replace(spaceRe, " ").trim()

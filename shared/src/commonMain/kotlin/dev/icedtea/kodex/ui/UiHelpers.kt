package dev.icedtea.kodex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import io.ktor.client.plugins.ClientRequestException
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

/** Turns transport/HTTP failures into a short, user-facing line for the login screen. */
fun Throwable.friendlyMessage(): String = when (this) {
    is ClientRequestException -> when (response.status.value) {
        401 -> "Wrong email or password."
        403 -> "This account isn't allowed to sign in."
        404 -> "That doesn't look like a Kodex server."
        else -> message
    }

    else -> message ?: "Couldn't reach the server. Check the address and your connection."
}

private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * The technical truth about a failed call: HTTP status plus the server's RFC-7807 `detail` and `code`
 * when it sent a problem document, else the transport failure itself.
 *
 * [friendlyMessage] is tuned for the login screen — it rewrites 404 into "not a Kodex server" and
 * hides everything else behind "couldn't reach the server", which is exactly wrong on a screen whose
 * whole job is to say *why* a source didn't load.
 */
suspend fun Throwable.serverErrorDetail(): String = when (this) {
    is ResponseException -> {
        // The default response validator saves the call before throwing, so the body is re-readable.
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val problem = runCatching { problemJson.parseToJsonElement(body).jsonObject }.getOrNull()
        fun field(name: String) = problem?.get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val detail = field("detail") ?: field("title") ?: body.trim().take(300).ifBlank { null } ?: message
        val code = field("code")
        buildString {
            append("HTTP ${response.status.value} ${response.status.description}")
            if (detail != null) append(" — ").append(detail)
            if (code != null) append(" [").append(code).append("]")
        }
    }

    else -> listOfNotNull(this::class.simpleName, message).joinToString(": ")
        .ifBlank { "Couldn't reach the server. Check the address and your connection." }
}

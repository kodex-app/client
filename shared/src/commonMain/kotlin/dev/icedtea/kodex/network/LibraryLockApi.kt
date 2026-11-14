package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Library PIN lock (`/api/v1/users/me/library-lock`). The PIN and the locked libraries live on the
// server, shared with the web UI and every other device; an unlock returns a bearer token that goes in
// [HEADER_LIBRARY_UNLOCK] — added to every request by the client plugin in AppGraph, not per call.

/** Unlock-token header; without it the server hides locked libraries from every aggregated list. */
const val HEADER_LIBRARY_UNLOCK = "X-Kodex-Library-Unlock"

private const val LOCK_PATH = "/api/v1/users/me/library-lock"

@Serializable
data class LibraryLockStatusDto(
    val enabled: Boolean = false,
    val lockedLibraryIds: List<String> = emptyList(),
    val relockAfterMinutes: Int = 5,
    /** The PIN was proven on this device (its token is valid) — needed to edit the settings. */
    val unlocked: Boolean = true,
    /** The locked libraries this device's token has opened; the rest stay hidden. */
    val unlockedLibraryIds: List<String> = emptyList(),
    /** The PIN also guards the whole app: this client locks every time it returns from the background. */
    val appLock: Boolean = false,
    /** Web UI only: minutes without input before its lock screen. */
    val appLockIdleMinutes: Int = 5,
)

@Serializable
data class UnlockTokenDto(val token: String, val expiresAt: String? = null)

@Serializable
private data class EnableLockRequest(val pin: String, val lockedLibraryIds: List<String>, val relockAfterMinutes: Int)

@Serializable
data class UpdateLockRequest(
    val pin: String? = null,
    val newPin: String? = null,
    val lockedLibraryIds: List<String>? = null,
    val relockAfterMinutes: Int? = null,
    val appLock: Boolean? = null,
    val appLockIdleMinutes: Int? = null,
)

@Serializable
private data class PinBody(val pin: String? = null)

@Serializable
private data class UnlockBody(val pin: String, val libraryId: String? = null)

suspend fun KodexApi.libraryLockStatus(baseUrl: String, apiKey: String): LibraryLockStatusDto =
    client.get("$baseUrl$LOCK_PATH") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.enableLibraryLock(baseUrl: String, apiKey: String, pin: String, ids: List<String>, relockAfterMinutes: Int): LibraryLockStatusDto =
    client.post("$baseUrl$LOCK_PATH") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(EnableLockRequest(pin, ids, relockAfterMinutes))
    }.body()

/** Returns the re-issued token when the PIN changed, else null (204). */
suspend fun KodexApi.updateLibraryLock(baseUrl: String, apiKey: String, patch: UpdateLockRequest): UnlockTokenDto? {
    val res = client.patch("$baseUrl$LOCK_PATH") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(patch)
    }
    return if (res.status == HttpStatusCode.NoContent) null else res.body()
}

suspend fun KodexApi.disableLibraryLock(baseUrl: String, apiKey: String, pin: String?) {
    client.post("$baseUrl$LOCK_PATH/disable") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(PinBody(pin))
    }
}

/**
 * Proves the PIN and opens [libraryId] (nothing — a bare proof, for the settings — when null). The device's
 * current token, attached by the client plugin, is extended and returned; without one a new token is issued.
 */
suspend fun KodexApi.unlockLibraries(baseUrl: String, apiKey: String, pin: String, libraryId: String?): UnlockTokenDto =
    client.post("$baseUrl$LOCK_PATH/unlock") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(UnlockBody(pin, libraryId))
    }.body()

/** Locks [libraryId] again for [token], or revokes the whole token when null. */
suspend fun KodexApi.lockLibraries(baseUrl: String, apiKey: String, token: String, libraryId: String? = null) {
    client.post("$baseUrl$LOCK_PATH/lock") {
        header(HEADER_API_KEY, apiKey)
        header(HEADER_LIBRARY_UNLOCK, token)
        if (libraryId != null) url.parameters.append("libraryId", libraryId)
    }
}

/** A failed PIN check, decoded from the server's problem response. */
sealed interface PinFailure {
    data class Wrong(val remainingAttempts: Int?) : PinFailure
    data class Throttled(val retryAfterSeconds: Long) : PinFailure
}

private val problemJson = Json { ignoreUnknownKeys = true }

/** Maps a `LIBRARY_PIN_INVALID` / `LIBRARY_PIN_THROTTLED` error; null for anything else. */
suspend fun Throwable.pinFailure(): PinFailure? {
    val res = (this as? ResponseException)?.response ?: return null
    val body = runCatching { problemJson.parseToJsonElement(res.bodyAsText()).jsonObject }.getOrNull() ?: return null
    val params = body["parameters"] as? JsonObject
    return when (body["code"]?.jsonPrimitive?.contentOrNull) {
        "LIBRARY_PIN_INVALID" -> PinFailure.Wrong(params?.get("remainingAttempts")?.jsonPrimitive?.intOrNull)
        "LIBRARY_PIN_THROTTLED" -> PinFailure.Throttled(params?.get("retryAfterSeconds")?.jsonPrimitive?.longOrNull ?: 30)
        else -> null
    }
}

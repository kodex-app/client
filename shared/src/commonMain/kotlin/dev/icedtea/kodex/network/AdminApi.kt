package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

// Server administration: users, own account security, server actions and tasks, backup, network, logs. Extensions on [KodexApi]; see that file for the auth model.

// ── Server administration ─────────────────────────────────────────────────────────────────────
// Admin-only endpoints behind More → Server. Every one of these 403s for a non-admin, so the UI
// gates the section on the role rather than relying on the failure.

suspend fun KodexApi.users(baseUrl: String, apiKey: String): List<UserDto> =
    client.get("$baseUrl/api/v1/users") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.createUser(baseUrl: String, apiKey: String, request: CreateUserRequest): UserDto =
    client.post("$baseUrl/api/v1/users") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

suspend fun KodexApi.deleteUser(baseUrl: String, apiKey: String, id: String) {
    client.delete("$baseUrl/api/v1/users/$id") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.updateUserLimits(baseUrl: String, apiKey: String, id: String, request: UpdateUserLimitsRequest): UserDto =
    client.patch("$baseUrl/api/v1/users/$id/limits") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

/** Admin reset of another user's password — no current password needed. */
suspend fun KodexApi.resetUserPassword(baseUrl: String, apiKey: String, id: String, newPassword: String) {
    client.put("$baseUrl/api/v1/users/$id/password") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(ResetPasswordRequest(newPassword))
    }
}

/** Clears another user's second factor, so they can enroll again (e.g. lost authenticator). */
suspend fun KodexApi.resetUserTotp(baseUrl: String, apiKey: String, id: String) {
    client.delete("$baseUrl/api/v1/users/$id/totp") { header(HEADER_API_KEY, apiKey) }
}

/** The signed-in user changing their own password; verifies [currentPassword]. */
suspend fun KodexApi.changeOwnPassword(baseUrl: String, apiKey: String, currentPassword: String, newPassword: String) {
    client.post("$baseUrl/api/v1/users/me/password") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(ChangePasswordRequest(currentPassword, newPassword))
    }
}

// ── Second factor (own account) ───────────────────────────────────────────────────────────────

/** Starts enrollment: returns the secret to show as a QR/otpauth URI. Not active until confirmed. */
suspend fun KodexApi.totpEnroll(baseUrl: String, apiKey: String): TotpEnrollmentDto =
    client.post("$baseUrl/api/v1/users/me/totp/enroll") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.totpActivate(baseUrl: String, apiKey: String, code: String) {
    client.post("$baseUrl/api/v1/users/me/totp/activate") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(TotpCodeRequest(code))
    }
}

suspend fun KodexApi.totpDisable(baseUrl: String, apiKey: String, code: String) {
    client.post("$baseUrl/api/v1/users/me/totp/disable") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(TotpCodeRequest(code))
    }
}

// ── Server-wide actions and the task queue ────────────────────────────────────────────────────

/** Rescans every library; [deep] re-reads files already known. Returns { libraries: n }. */
suspend fun KodexApi.refreshAllLibraries(baseUrl: String, apiKey: String, deep: Boolean): Int =
    client.post("$baseUrl/api/v1/admin/refresh-all") {
        header(HEADER_API_KEY, apiKey)
        parameter("deep", deep)
    }.body<Map<String, Int>>().values.firstOrNull() ?: 0

/** Cancels every queued/running task. Returns how many were cancelled. */
suspend fun KodexApi.cancelAllTasks(baseUrl: String, apiKey: String): Int =
    client.post("$baseUrl/api/v1/admin/tasks/cancel-all") {
        header(HEADER_API_KEY, apiKey)
    }.body<Map<String, Int>>().values.firstOrNull() ?: 0

suspend fun KodexApi.shutdownServer(baseUrl: String, apiKey: String) {
    client.post("$baseUrl/api/v1/admin/shutdown") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.tasks(baseUrl: String, apiKey: String, page: Int = 0, size: Int = PAGE_SIZE): List<TaskDto> =
    client.get("$baseUrl/api/v1/tasks") {
        header(HEADER_API_KEY, apiKey)
        parameter("page", page)
        parameter("size", size)
    }.body<PageResponse<TaskDto>>().content

// ── Backup ────────────────────────────────────────────────────────────────────────────────────
// The client works with backups the server already holds. Uploading an archive to restore, and
// downloading one to the device, both need a file picker this app does not have — the stored-file
// routes cover the same ground without one.

suspend fun KodexApi.backupFiles(baseUrl: String, apiKey: String): List<BackupFileDto> =
    client.get("$baseUrl/api/v1/admin/backup/files") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.deleteBackupFile(baseUrl: String, apiKey: String, name: String) {
    client.delete("$baseUrl/api/v1/admin/backup/files/${name.encodeURLPathPart()}") {
        header(HEADER_API_KEY, apiKey)
    }
}

/** Stages a restore from a backup the server holds. The server needs a restart to apply it. */
suspend fun KodexApi.restoreStoredBackup(baseUrl: String, apiKey: String, name: String, password: String?): RestoreResultDto =
    client.post("$baseUrl/api/v1/admin/backup/files/${name.encodeURLPathPart()}/restore") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(RestoreStoredRequest(password?.takeIf { it.isNotBlank() }))
    }.body()

suspend fun KodexApi.backupSettings(baseUrl: String, apiKey: String): BackupSettingsDto =
    client.get("$baseUrl/api/v1/admin/backup/settings") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.saveBackupSettings(baseUrl: String, apiKey: String, request: BackupSettingsRequest): BackupSettingsDto =
    client.put("$baseUrl/api/v1/admin/backup/settings") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

// ── Network ───────────────────────────────────────────────────────────────────────────────────

suspend fun KodexApi.networkSettings(baseUrl: String, apiKey: String): NetworkSettingsDto =
    client.get("$baseUrl/api/v1/server/network") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.saveNetworkSettings(baseUrl: String, apiKey: String, request: NetworkSettingsRequest): NetworkSettingsDto =
    client.put("$baseUrl/api/v1/server/network") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

// ── Logs ──────────────────────────────────────────────────────────────────────────────────────

/** The recent in-memory buffer. The live `/stream` SSE route is a separate connection. */
suspend fun KodexApi.recentLogs(baseUrl: String, apiKey: String): List<LogEntryDto> =
    client.get("$baseUrl/api/v1/server/logs") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.debugMode(baseUrl: String, apiKey: String): Boolean =
    client.get("$baseUrl/api/v1/server/logs/debug") { header(HEADER_API_KEY, apiKey) }.body<DebugModeDto>().enabled

suspend fun KodexApi.setDebugMode(baseUrl: String, apiKey: String, enabled: Boolean): Boolean =
    client.put("$baseUrl/api/v1/server/logs/debug") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(DebugModeDto(enabled))
    }.body<DebugModeDto>().enabled

package dev.icedtea.kodex.network

import kotlinx.serialization.Serializable

/** Body of `POST /api/v1/api-keys`. The comment labels the key in the server's key list. */
@Serializable
data class CreateApiKeyRequest(val comment: String)

/** Response of `POST /api/v1/api-keys` — the only time the raw [key] is ever returned. */
@Serializable
data class CreatedApiKeyDto(
    val id: String,
    val comment: String? = null,
    val key: String,
    val createdDate: String? = null,
)

/** `GET /api/v1/users/me` — used to validate a key and drive role-gated UI. */
@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val roles: Set<String> = emptySet(),
    val webLibraryLimit: Int = 0,
    val webLibraryItemLimit: Int = 0,
    val allowAdultContent: Boolean = false,
    val totpEnabled: Boolean = false,
) {
    val isAdmin: Boolean get() = "ADMIN" in roles
    val isManager: Boolean get() = "MANAGER" in roles
}

// ── Server administration (admin-only screens under More → Server) ───────────────────────────────

@Serializable
data class CreateUserRequest(val email: String, val password: String, val roles: Set<String>)

/** Null fields are left unchanged by the server, so each control can be saved on its own. */
@Serializable
data class UpdateUserLimitsRequest(
    val webLibraryLimit: Int? = null,
    val webLibraryItemLimit: Int? = null,
    val allowAdultContent: Boolean? = null,
)

@Serializable
data class ResetPasswordRequest(val newPassword: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** The shared secret plus the `otpauth://` URI an authenticator app scans. */
@Serializable
data class TotpEnrollmentDto(val secret: String, val otpauthUri: String)

@Serializable
data class TotpCodeRequest(val code: String)

/** One entry in the background task queue. [payload] is an opaque per-type JSON blob. */
@Serializable
data class TaskDto(
    val id: String,
    val type: String,
    val payload: String? = null,
    val status: String,
    val message: String? = null,
    val createdDate: String? = null,
    val lastModifiedDate: String? = null,
)


// ── Backup ───────────────────────────────────────────────────────────────────────────────────────

/** One backup archive kept on the server. [size] is bytes; [createdAt] an ISO instant. */
@Serializable
data class BackupFileDto(val name: String, val size: Long, val createdAt: String? = null)

/** Scheduled auto-backup config. [passwordSet] reports whether one is stored, never the value. */
@Serializable
data class BackupSettingsDto(
    val enabled: Boolean = false,
    val frequency: String = "WEEKLY",
    val customIntervalHours: Int = 24,
    val includeThumbnails: Boolean = false,
    val keepCount: Int = 5,
    val passwordSet: Boolean = false,
)

/** [password] null keeps the stored one, "" clears it — matching the server's contract. */
@Serializable
data class BackupSettingsRequest(
    val enabled: Boolean,
    val frequency: String,
    val customIntervalHours: Int,
    val includeThumbnails: Boolean,
    val keepCount: Int,
    val password: String? = null,
)

@Serializable
data class RestoreStoredRequest(val password: String? = null)

/** What a staged restore contained; [restartRequired] is always true today. */
@Serializable
data class RestoreResultDto(
    val restartRequired: Boolean = true,
    val backupCreatedAt: String? = null,
    val includedThumbnails: Boolean = false,
    val includedFonts: Boolean = false,
    val includedPlugins: Boolean = false,
)

// ── Network ──────────────────────────────────────────────────────────────────────────────────────

/** Outbound network config. [proxyPasswordSet] reports storage, never the value. */
@Serializable
data class NetworkSettingsDto(
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUsername: String = "",
    val proxyPasswordSet: Boolean = false,
    val dohEnabled: Boolean = false,
    val dohUrl: String = "",
    val cloudflareSolverEnabled: Boolean = false,
    val cloudflareSolverUrl: String = "",
    val cloudflareSolverTimeoutSeconds: Int = 60,
    /** Mihon WebView: a remote browser (browserless DevTools endpoint) instead of the server's Chromium. */
    val remoteBrowserEnabled: Boolean = false,
    val remoteBrowserUrl: String = "",
    /** The Chromium/Chrome/Edge found on the server (used when the remote browser is off), or null. */
    val embeddedBrowser: String? = null,
    /** Live state of the shared headless browser. */
    val browser: BrowserStatusDto = BrowserStatusDto(),
)

/** [proxyPassword] null keeps the stored one, "" clears it. */
@Serializable
data class NetworkSettingsRequest(
    val proxyEnabled: Boolean,
    val proxyType: String,
    val proxyHost: String,
    val proxyPort: Int,
    val proxyUsername: String,
    val proxyPassword: String? = null,
    val dohEnabled: Boolean,
    val dohUrl: String,
    val cloudflareSolverEnabled: Boolean,
    val cloudflareSolverUrl: String,
    val cloudflareSolverTimeoutSeconds: Int,
    val remoteBrowserEnabled: Boolean = false,
    val remoteBrowserUrl: String = "",
)

// ── Logs ─────────────────────────────────────────────────────────────────────────────────────────

/** One log line. [timestamp] is epoch millis; [throwable] is a rendered stack trace when present. */
@Serializable
data class LogEntryDto(
    val timestamp: Long = 0,
    val level: String = "INFO",
    val logger: String = "",
    val thread: String = "",
    val message: String = "",
    val throwable: String? = null,
)

@Serializable
data class DebugModeDto(val enabled: Boolean)


// ── Content-source configuration ─────────────────────────────────────────────────────────────────

/** One configurable field. [type] is STRING | SECRET | BOOLEAN | INTEGER | ENUM. */
@Serializable
data class ConfigFieldDto(
    val key: String,
    val label: String = "",
    val type: String = "STRING",
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<String> = emptyList(),
    val value: String = "",
    /** For SECRET fields: a value is stored. The value itself is never sent. */
    val secretSet: Boolean = false,
)

@Serializable
data class SourceConfigDto(
    val providerId: String = "",
    val displayName: String = "",
    val fields: List<ConfigFieldDto> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val secretsSet: List<String> = emptyList(),
)

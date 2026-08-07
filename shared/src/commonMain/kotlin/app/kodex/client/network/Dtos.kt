package app.kodex.client.network

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
) {
    val isAdmin: Boolean get() = "ADMIN" in roles
    val isManager: Boolean get() = "MANAGER" in roles
}

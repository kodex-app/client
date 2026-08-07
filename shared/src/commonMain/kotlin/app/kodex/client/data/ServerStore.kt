package app.kodex.client.data

import app.kodex.client.data.model.ServerConnection
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the set of saved [ServerConnection]s plus which one was used last. Backed by
 * multiplatform-settings (SharedPreferences / NSUserDefaults / java.util.prefs), so it survives
 * relaunch on every platform.
 *
 * NOTE: API keys are stored in plaintext prefs for now. A later pass should move [ServerConnection.apiKey]
 * into the platform keystore/keychain — tracked as a follow-up.
 */
class ServerStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun getServers(): List<ServerConnection> =
        settings.getStringOrNull(KEY_SERVERS)
            ?.let { runCatching { json.decodeFromString<List<ServerConnection>>(it) }.getOrNull() }
            ?.sortedByDescending { it.lastUsedAt }
            ?: emptyList()

    fun upsert(server: ServerConnection) {
        val next = getServers().filterNot { it.id == server.id } + server
        settings.putString(KEY_SERVERS, json.encodeToString(next))
        settings.putString(KEY_LAST_USED, server.id)
    }

    fun remove(id: String) {
        val next = getServers().filterNot { it.id == id }
        settings.putString(KEY_SERVERS, json.encodeToString(next))
        if (settings.getStringOrNull(KEY_LAST_USED) == id) settings.remove(KEY_LAST_USED)
    }

    fun lastUsedServer(): ServerConnection? {
        val id = settings.getStringOrNull(KEY_LAST_USED) ?: return null
        return getServers().firstOrNull { it.id == id }
    }

    private companion object {
        const val KEY_SERVERS = "servers.v1"
        const val KEY_LAST_USED = "servers.lastUsed"
    }
}

package app.kodex.client.auth

import app.kodex.client.data.ServerStore
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.KodexApi
import app.kodex.client.network.UserDto
import app.kodex.client.platform.nowMillis
import app.kodex.client.util.normalizeBaseUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Owns auth/session state across the whole app: the saved servers, which one is active, and the
 * current user. The UI observes the [StateFlow]s; a non-null [activeServer] means "show the main
 * app", null means "show login".
 */
class SessionManager(
    private val store: ServerStore,
    private val api: KodexApi,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _servers = MutableStateFlow(store.getServers())
    val servers: StateFlow<List<ServerConnection>> = _servers.asStateFlow()

    private val _activeServer = MutableStateFlow<ServerConnection?>(null)
    val activeServer: StateFlow<ServerConnection?> = _activeServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    /** On launch, open straight into the most recently used server (optimistically, offline-friendly). */
    fun bootstrap() {
        val last = store.lastUsedServer() ?: return
        _activeServer.value = last
        scope.launch {
            runCatching { api.getMe(last.baseUrl, last.apiKey) }
                .onSuccess { _currentUser.value = it }
        }
    }

    /** Add a brand-new server: mint an API key with the given credentials, validate it, persist. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun addServer(
        label: String,
        rawUrl: String,
        email: String,
        password: String,
    ): Result<ServerConnection> = runCatching {
        val base = normalizeBaseUrl(rawUrl)
        require(base.isNotEmpty()) { "Enter a server address" }
        require(email.isNotBlank() && password.isNotBlank()) { "Enter your email and password" }

        val created = api.createApiKey(base, email, password, comment = "Kodex mobile")
        val me = api.getMe(base, created.key)

        val server = ServerConnection(
            id = Uuid.random().toString(),
            label = label.ifBlank { base.substringAfter("://").substringBefore("/") },
            baseUrl = base,
            email = me.email,
            apiKey = created.key,
            lastUsedAt = nowMillis(),
        )
        store.upsert(server)
        _servers.value = store.getServers()
        _activeServer.value = server
        _currentUser.value = me
        server
    }

    /** Sign in to an already-saved server, validating its stored key first. */
    suspend fun selectServer(server: ServerConnection): Result<Unit> = runCatching {
        val me = api.getMe(server.baseUrl, server.apiKey)
        val touched = server.copy(lastUsedAt = nowMillis())
        store.upsert(touched)
        _servers.value = store.getServers()
        _activeServer.value = touched
        _currentUser.value = me
    }

    /** Leave the active server (back to login) but keep it saved. */
    fun signOut() {
        _activeServer.value = null
        _currentUser.value = null
    }

    fun removeServer(server: ServerConnection) {
        store.remove(server.id)
        _servers.value = store.getServers()
        if (_activeServer.value?.id == server.id) signOut()
    }
}

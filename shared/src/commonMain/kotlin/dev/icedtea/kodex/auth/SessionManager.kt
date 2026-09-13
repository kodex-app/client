package dev.icedtea.kodex.auth

import dev.icedtea.kodex.data.ServerStore
import dev.icedtea.kodex.data.model.ServerConnection
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.UserDto
import dev.icedtea.kodex.platform.nowMillis
import dev.icedtea.kodex.util.normalizeBaseUrl
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

    /**
     * The saved connection whose API key the server just rejected. Set by [keyRejected] when any
     * call to the active server comes back 401 — the key was revoked, or the server was reset — at
     * which point the app has already dropped to the login screen. That screen reads this to open
     * straight into a "sign in again" prompt for the connection instead of a generic picker whose
     * every entry would fail the same way. Cleared by [reauthenticate] or [dismissExpired].
     */
    private val _expiredServer = MutableStateFlow<ServerConnection?>(null)
    val expiredServer: StateFlow<ServerConnection?> = _expiredServer.asStateFlow()

    /**
     * Run a fire-and-forget background task on the session's own scope. Used for reading-progress saves,
     * which must complete even when the screen that triggered them (the reader) is being disposed — a
     * `LaunchedEffect`/`rememberCoroutineScope` coroutine would be cancelled on dispose and drop the write.
     */
    fun persistDetached(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }

    /** On launch, open straight into the most recently used server (optimistically, offline-friendly). */
    fun bootstrap() {
        val last = store.lastUsedServer() ?: return
        _activeServer.value = last
        scope.launch {
            runCatching { api.getMe(last.baseUrl, last.apiKey) }
                .onSuccess { _currentUser.value = it }
        }
    }

    /**
     * Re-read the signed-in user. Screens that change something stored on the user record (the
     * second-factor flag, limits) call this so the cached [currentUser] stops disagreeing with the
     * server. Throws on failure so the caller can report it rather than silently showing stale state.
     */
    suspend fun refreshCurrentUser() {
        val server = _activeServer.value ?: return
        _currentUser.value = api.getMe(server.baseUrl, server.apiKey)
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

    /**
     * Edit the active connection in place, keeping its id so nothing keyed to this server is lost.
     *
     * The stored API key is only valid for the server that minted it, so a changed [rawUrl] forces a
     * re-authentication — [password] is required in that case. Supplying a password without moving
     * the address just mints a fresh key (useful when the old one was revoked server-side). Leaving
     * it blank keeps the existing key and merely re-validates it, so a rename costs one request.
     */
    suspend fun updateActiveServer(
        label: String,
        rawUrl: String,
        email: String,
        password: String,
    ): Result<ServerConnection> = runCatching {
        val current = requireNotNull(_activeServer.value) { "No server is signed in" }
        val base = normalizeBaseUrl(rawUrl)
        require(base.isNotEmpty()) { "Enter a server address" }

        val moved = base != current.baseUrl
        require(!moved || password.isNotBlank()) {
            "Changing the server address needs your password — the saved key only works on the old server."
        }
        require(password.isBlank() || email.isNotBlank()) { "Enter the email to sign in with" }

        val key = if (moved || password.isNotBlank()) {
            api.createApiKey(base, email, password, comment = "Kodex mobile").key
        } else {
            current.apiKey
        }
        val me = api.getMe(base, key)

        val updated = current.copy(
            label = label.ifBlank { base.substringAfter("://").substringBefore("/") },
            baseUrl = base,
            email = me.email,
            apiKey = key,
            lastUsedAt = nowMillis(),
        )
        store.upsert(updated)
        _servers.value = store.getServers()
        _activeServer.value = updated
        _currentUser.value = me
        updated
    }

    /**
     * The server answered a request carrying [apiKey] with 401. If that is the active connection's
     * key, the session is dead: no retry will succeed, so sign out now and remember which connection
     * needs a fresh password. Wired to the HTTP client's validator, so it fires for every screen —
     * nothing has to check status codes itself. Ignored for any other key (a stale 401 from a
     * connection already replaced, or a Basic-auth login attempt, which carries no API key at all).
     */
    fun keyRejected(apiKey: String) {
        val server = _activeServer.value ?: return
        if (server.apiKey != apiKey) return
        signOut()
        _expiredServer.value = server
    }

    /**
     * Mint a fresh key for a saved connection whose stored one no longer works, keeping its id so
     * everything keyed to the connection survives. [email] defaults to the saved one but may be
     * changed — the account may have been recreated under another address after a server reset.
     */
    suspend fun reauthenticate(server: ServerConnection, email: String, password: String): Result<ServerConnection> =
        runCatching {
            require(email.isNotBlank() && password.isNotBlank()) { "Enter your email and password" }
            val created = api.createApiKey(server.baseUrl, email, password, comment = "Kodex mobile")
            val me = api.getMe(server.baseUrl, created.key)
            val renewed = server.copy(email = me.email, apiKey = created.key, lastUsedAt = nowMillis())
            store.upsert(renewed)
            _servers.value = store.getServers()
            _expiredServer.value = null
            _activeServer.value = renewed
            _currentUser.value = me
            renewed
        }

    /** The user backed out of the "sign in again" prompt; show the plain server picker. */
    fun dismissExpired() {
        _expiredServer.value = null
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
        if (_expiredServer.value?.id == server.id) dismissExpired()
    }
}

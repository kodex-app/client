package dev.icedtea.kodex.data

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryLockStatusDto
import dev.icedtea.kodex.network.UpdateLockRequest
import dev.icedtea.kodex.network.disableLibraryLock
import dev.icedtea.kodex.network.enableLibraryLock
import dev.icedtea.kodex.network.libraryLockStatus
import dev.icedtea.kodex.network.lockLibraries
import dev.icedtea.kodex.network.unlockLibraries
import dev.icedtea.kodex.network.updateLibraryLock
import dev.icedtea.kodex.platform.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Library PIN lock for the active server — the same server-side setting the web UI edits, so the PIN and
 * the set of locked libraries follow the user to every device.
 *
 * *Being unlocked* is per device, per library and in memory only: [unlock] for a library gets a bearer
 * token that the HTTP client plugin (AppGraph) attaches to every request to that server, and that opens
 * only that library (unlocking another one extends the same token). The server leaves every locked library
 * the token hasn't opened out of Home, search, history, updates and library-wide lists; opening one
 * directly goes through `LibraryLockGate`, which asks for the PIN.
 *
 * Relocks everything when the app has been in the background for `relockAfterMinutes` (0 = as soon as it
 * leaves the foreground), on app restart, on server switch, and on "Lock now"; one library with [lockLibrary].
 *
 * App lock (`status.appLock`): the same PIN guards the whole app. [appLocked] is true from app start and
 * again every time the app leaves the foreground, until [unlockApp]; `AppLockScreen` (App.kt) covers
 * everything meanwhile. Whether it is on is remembered per server in [hints], so a cold start can lock
 * before the status request returns.
 */
class LibraryLockStore(
    private val api: KodexApi,
    private val scope: CoroutineScope,
    private val hints: Settings = Settings(),
) {

    private val _status = MutableStateFlow<LibraryLockStatusDto?>(null)
    val status: StateFlow<LibraryLockStatusDto?> = _status.asStateFlow()

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    /**
     * Bumped whenever what the server will return changes (unlocked, relocked, libraries added/removed),
     * so screens showing aggregated lists can reload.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** The whole app is behind the lock screen. */
    private val _appLocked = MutableStateFlow(false)
    val appLocked: StateFlow<Boolean> = _appLocked.asStateFlow()
    private var appUnlocked = false

    @Volatile private var baseUrl: String? = null
    @Volatile private var apiKey: String? = null
    @Volatile private var currentToken: String? = null
    private var backgroundedAt: Long? = null

    /** For the HTTP plugin: the unlock token to send with a request to [url], if any. */
    fun headerFor(url: String): String? {
        val base = baseUrl ?: return null
        val t = currentToken ?: return null
        return if (url.startsWith(base)) t else null
    }

    fun bind(baseUrl: String, apiKey: String) {
        this.baseUrl = baseUrl
        this.apiKey = apiKey
        setToken(null)
        _status.value = null
        appUnlocked = false
        syncAppLocked()
        scope.launch { refresh() }
    }

    fun clear() {
        baseUrl = null
        apiKey = null
        setToken(null)
        _status.value = null
        appUnlocked = false
        _appLocked.value = false
    }

    private fun hintKey(base: String) = "appLock:$base"

    private val appLockOn: Boolean
        get() {
            val base = baseUrl ?: return false
            return _status.value?.let { it.enabled && it.appLock } ?: hints.getBoolean(hintKey(base), false)
        }

    private fun syncAppLocked() {
        _appLocked.value = appLockOn && !appUnlocked
    }

    /** Unlocks the lock screen: a bare proof of the PIN, so the locked libraries stay locked. */
    suspend fun unlockApp(pin: String) {
        unlock(pin, null)
        appUnlocked = true
        syncAppLocked()
    }

    val enabled: Boolean get() = _status.value?.enabled == true
    val unlocked: Boolean get() = !enabled || currentToken != null

    /** True when [libraryId] is PIN-locked and this device hasn't opened it. */
    fun isLocked(libraryId: String?): Boolean = isLocked(libraryId, _status.value, currentToken)

    /** Whether [libraryId] is in the lock set at all, opened or not. */
    fun isProtected(libraryId: String?): Boolean =
        libraryId != null && enabled && libraryId in (_status.value?.lockedLibraryIds ?: emptyList())

    /** Re-reads the lock config (another device may have changed it). Never throws. */
    suspend fun refresh() {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        val s = runCatching { api.libraryLockStatus(b, k) }.getOrNull() ?: return
        val before = _status.value
        _status.value = s
        hints.putBoolean(hintKey(b), s.enabled && s.appLock)
        syncAppLocked()
        // The server forgot our token (restart, PIN changed elsewhere, expiry): locked again.
        if (currentToken != null && s.enabled && !s.unlocked) setToken(null)
        else if (before != null && (before.lockedLibraryIds != s.lockedLibraryIds || before.unlockedLibraryIds != s.unlockedLibraryIds)) bump()
    }

    /** Proves the PIN and opens [libraryId] (only the proof, for the settings, when null). */
    suspend fun unlock(pin: String, libraryId: String? = null) {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        val t = api.unlockLibraries(b, k, pin, libraryId).token
        currentToken = t
        _token.value = t
        refresh()
        bump()
    }

    /** Locks one opened library again on this device; the others stay open. */
    suspend fun lockLibrary(libraryId: String) {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        val t = currentToken ?: return
        runCatching { api.lockLibraries(b, k, t, libraryId) }
        refresh()
        bump()
    }

    fun lockNow() {
        val t = currentToken ?: return
        val b = baseUrl
        val k = apiKey
        setToken(null)
        if (b != null && k != null) scope.launch { runCatching { api.lockLibraries(b, k, t) } }
    }

    suspend fun enable(pin: String, ids: List<String>, relockAfterMinutes: Int) {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        _status.value = api.enableLibraryLock(b, k, pin, ids, relockAfterMinutes)
        unlock(pin) // setting the PIN proves it: keep the settings editable; the libraries themselves lock now
    }

    /** Proof is this device's unlock token (attached by the plugin), or [UpdateLockRequest.pin]. */
    suspend fun update(patch: UpdateLockRequest) {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        // Turning the app lock on from the (unlocked) settings screen must not lock that screen at once.
        if (patch.appLock == true) appUnlocked = true
        api.updateLibraryLock(b, k, patch)?.let { currentToken = it.token; _token.value = it.token }
        refresh()
        bump()
    }

    suspend fun setLibraryLocked(libraryId: String, locked: Boolean) {
        val ids = (_status.value?.lockedLibraryIds ?: emptyList()).toMutableList()
        if (locked) { if (libraryId !in ids) ids += libraryId } else ids -= libraryId
        update(UpdateLockRequest(lockedLibraryIds = ids))
    }

    suspend fun disable() {
        val b = baseUrl ?: return
        val k = apiKey ?: return
        api.disableLibraryLock(b, k, null)
        setToken(null)
        refresh()
        bump()
    }

    // ---- app lifecycle (App.kt) ----

    fun onBackground() {
        backgroundedAt = nowMillis()
        if (appLockOn) {
            // Leaving the app locks it, and every library with it.
            appUnlocked = false
            syncAppLocked()
            lockNow()
            return
        }
        if (enabled && _status.value?.relockAfterMinutes == 0) lockNow()
    }

    fun onForeground() {
        val away = backgroundedAt?.let { nowMillis() - it } ?: 0L
        backgroundedAt = null
        val relock = _status.value?.relockAfterMinutes ?: return
        if (currentToken != null && away >= relock * 60_000L) lockNow()
        scope.launch { refresh() }
    }

    private fun setToken(next: String?) {
        if (currentToken == next) return
        currentToken = next
        _token.value = next
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }
}

/** [LibraryLockStore.isLocked] over a given status/token, for composables reading the flows. */
fun isLocked(libraryId: String?, status: LibraryLockStatusDto?, token: String?): Boolean =
    libraryId != null && status?.enabled == true && libraryId in status.lockedLibraryIds &&
        (token == null || libraryId !in status.unlockedLibraryIds)

val LocalLibraryLock = staticCompositionLocalOf<LibraryLockStore> { error("LibraryLockStore not provided") }

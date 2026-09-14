package dev.icedtea.kodex.data

import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.saveUserSetting
import dev.icedtea.kodex.network.userSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-user Browse source preferences — favourite sources, recently-opened sources and the language
 * groups the user has hidden — persisted **server-side** in the generic user-settings store (keys
 * `browse.favorites` / `browse.recents` / `browse.hiddenLanguages`), so they follow the user across
 * devices and stay in step with the web UI, which reads and writes the very same keys. Loaded when
 * the active server binds; writes are optimistic (update the flow immediately, then PUT).
 *
 * Also carries the Extensions screens' language filter (`extensions.hiddenLanguages`), shared by the
 * Mihon and LNReader lists exactly as the web's Extensions page shares it between its two tabs. It is
 * deliberately a separate setting from the Browse one: hiding a language while browsing sources and
 * hiding it while installing extensions are different choices.
 */
class SourcePrefsStore(private val api: KodexApi, private val scope: CoroutineScope) {

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    /** Hidden language groups, as raw language tags — `""` is the multi-language bucket (web parity). */
    private val _hiddenLanguages = MutableStateFlow<Set<String>>(emptySet())
    val hiddenLanguages: StateFlow<Set<String>> = _hiddenLanguages.asStateFlow()

    /** Languages hidden on the Mihon / LNReader extension lists — raw repository codes, `"all"` = multi-language. */
    private val _extensionHiddenLanguages = MutableStateFlow<Set<String>>(emptySet())
    val extensionHiddenLanguages: StateFlow<Set<String>> = _extensionHiddenLanguages.asStateFlow()

    private var baseUrl: String? = null
    private var apiKey: String? = null

    /** Load prefs for a server (call on sign-in / active-server change). */
    fun bind(baseUrl: String, apiKey: String) {
        this.baseUrl = baseUrl; this.apiKey = apiKey
        scope.launch {
            val settings = runCatching { api.userSettings(baseUrl, apiKey) }.getOrNull() ?: JsonObject(emptyMap())
            _favorites.value = settings[FAVORITES_KEY].asStringList()
            _recents.value = settings[RECENTS_KEY].asStringList()
            _hiddenLanguages.value = settings[HIDDEN_LANGUAGES_KEY].asStringList().toSet()
            _extensionHiddenLanguages.value = settings[EXTENSION_HIDDEN_LANGUAGES_KEY].asStringList().toSet()
        }
    }

    fun clear() {
        baseUrl = null; apiKey = null
        _favorites.value = emptyList(); _recents.value = emptyList()
        _hiddenLanguages.value = emptySet()
        _extensionHiddenLanguages.value = emptySet()
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    fun toggleFavorite(id: String) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        val next = if (id in _favorites.value) _favorites.value - id else _favorites.value + id
        _favorites.value = next
        save(b, k, FAVORITES_KEY, next)
    }

    /** Record that a source was just opened — moves it to the front (capped). */
    fun recordRecent(id: String) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        val next = (listOf(id) + _recents.value.filter { it != id }).take(RECENTS_MAX)
        _recents.value = next
        save(b, k, RECENTS_KEY, next)
    }

    /** Show/hide one language group; `key` is the raw tag (`""` = multi-language). */
    fun toggleHiddenLanguage(key: String) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        val next = if (key in _hiddenLanguages.value) _hiddenLanguages.value - key else _hiddenLanguages.value + key
        _hiddenLanguages.value = next
        save(b, k, HIDDEN_LANGUAGES_KEY, next.toList())
    }

    /** Replace the whole hidden set — backs the menu's "All" (empty) / "None" (every key) shortcuts. */
    fun setHiddenLanguages(keys: Collection<String>) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        _hiddenLanguages.value = keys.toSet()
        save(b, k, HIDDEN_LANGUAGES_KEY, keys.toList())
    }

    /** Show/hide one language on the extension lists; `key` is the raw code (`"all"` = multi-language). */
    fun toggleExtensionHiddenLanguage(key: String) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        val cur = _extensionHiddenLanguages.value
        val next = if (key in cur) cur - key else cur + key
        _extensionHiddenLanguages.value = next
        save(b, k, EXTENSION_HIDDEN_LANGUAGES_KEY, next.toList())
    }

    /** Replace the extension lists' hidden set — the menu's "All" (empty) / "None" (every key) shortcuts. */
    fun setExtensionHiddenLanguages(keys: Collection<String>) {
        val b = baseUrl ?: return; val k = apiKey ?: return
        _extensionHiddenLanguages.value = keys.toSet()
        save(b, k, EXTENSION_HIDDEN_LANGUAGES_KEY, keys.toList())
    }

    private fun save(baseUrl: String, apiKey: String, key: String, value: List<String>) {
        scope.launch {
            runCatching { api.saveUserSetting(baseUrl, apiKey, key, JsonArray(value.map { JsonPrimitive(it) })) }
        }
    }

    private fun JsonElement?.asStringList(): List<String> =
        (this as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    private companion object {
        const val FAVORITES_KEY = "browse.favorites"
        const val RECENTS_KEY = "browse.recents"
        const val HIDDEN_LANGUAGES_KEY = "browse.hiddenLanguages"
        const val EXTENSION_HIDDEN_LANGUAGES_KEY = "extensions.hiddenLanguages"
        const val RECENTS_MAX = 4
    }
}

package app.kodex.client.data

import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Per-user library view preferences: a custom display order, which libraries are hidden from the
 * Libraries tab, and which are kept off the Home screen's rows.
 *
 * Stored as the single opaque user setting the web UI already uses (`nav.libraries`), so the two
 * clients stay in sync and neither touches the shared Library records — this is purely a view
 * preference. Libraries missing from [order] keep their server order and sort to the end, so a newly
 * added library always shows up.
 */
@Serializable
data class LibraryNavPrefs(
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val hiddenFromHome: List<String> = emptyList(),
    /**
     * Libraries the web UI hides from *every* aggregated list. No client screen honours this yet, but it
     * is modelled so a save from here round-trips it: this whole preference is written back as one
     * object, so a facet the class doesn't know about is a facet the next toggle silently deletes.
     */
    val hiddenEverywhere: List<String> = emptyList(),
) {
    fun isHidden(id: String) = id in hidden
    fun isHiddenFromHome(id: String) = id in hiddenFromHome

    /** Toggling one facet must not drop the others, so each edit returns a full copy. */
    fun withHidden(id: String, hide: Boolean) =
        copy(hidden = hidden.toggled(id, hide))

    fun withHiddenFromHome(id: String, hide: Boolean) =
        copy(hiddenFromHome = hiddenFromHome.toggled(id, hide))

    fun withOrder(ids: List<String>) = copy(order = ids)
}

private fun List<String>.toggled(id: String, present: Boolean): List<String> =
    if (present) (this + id).distinct() else filterNot { it == id }

const val LIBRARY_NAV_PREF_KEY = "nav.libraries"

// encodeDefaults so an empty facet is still written as `[]` rather than dropped — the stored object
// keeps the same shape the web UI writes, which makes the setting readable by eye and by either client.
private val prefsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Drops items belonging to a library the user hid from Home. An item with no library id is kept:
 * better to show something unattributed than to silently swallow it.
 *
 * Shared by the Home rows and each row's "See all", which is the whole point — the two screens show
 * the same lists, so a library hidden from one has to be hidden from the other.
 */
fun <T> List<T>.visibleOnHome(prefs: LibraryNavPrefs, libraryIdOf: (T) -> String?): List<T> {
    val hidden = prefs.hiddenFromHome.toSet()
    return if (hidden.isEmpty()) this else filterNot { libraryIdOf(it) in hidden }
}

/** Sorts by the saved order; anything not in it keeps server order at the end. */
fun List<LibraryDto>.orderedBy(prefs: LibraryNavPrefs): List<LibraryDto> {
    val rank = prefs.order.withIndex().associate { (i, id) -> id to i }
    return sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}

/** Reads the preference, falling back to defaults when it's absent or malformed. */
fun parseLibraryNavPrefs(settings: JsonObject): LibraryNavPrefs =
    (settings[LIBRARY_NAV_PREF_KEY] as? JsonObject)
        ?.let { runCatching { prefsJson.decodeFromJsonElement<LibraryNavPrefs>(it) }.getOrNull() }
        ?: LibraryNavPrefs()

suspend fun loadLibraryNavPrefs(api: KodexApi, baseUrl: String, apiKey: String): LibraryNavPrefs =
    parseLibraryNavPrefs(runCatching { api.userSettings(baseUrl, apiKey) }.getOrElse { JsonObject(emptyMap()) })

suspend fun saveLibraryNavPrefs(api: KodexApi, baseUrl: String, apiKey: String, prefs: LibraryNavPrefs) {
    api.saveUserSetting(baseUrl, apiKey, LIBRARY_NAV_PREF_KEY, prefsJson.encodeToJsonElement(prefs))
}

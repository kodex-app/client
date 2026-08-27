package dev.icedtea.kodex.ui.reader

import dev.icedtea.kodex.network.KodexApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull

// Reader preference values (mirrors the web ImageReader's Prefs), stored as strings for stable JSON.
const val MODE_AUTO = "auto"
const val MODE_PAGED = "paged"
const val MODE_CONTINUOUS = "continuous"
const val DIR_LTR = "ltr"
const val DIR_RTL = "rtl"
const val ZOOM_HEIGHT = "height"
const val ZOOM_WIDTH = "width"
const val ZOOM_ORIGINAL = "original"
const val SPREAD_SINGLE = "single"
const val SPREAD_DOUBLE = "double"
const val BG_WHITE = "white"
const val BG_GRAY = "gray"
const val BG_BLACK = "black"

const val SCROLL_SPEED_DEFAULT = 40
const val SCROLL_SPEED_MIN = 10
const val SCROLL_SPEED_MAX = 300

const val PRELOAD_DEFAULT = 5
const val PRELOAD_MAX = 20
val PRELOAD_OPTIONS = listOf(0, 1, 5, 10, 20)
private const val PRELOAD_KEY = "reader.preloadCount"

@Serializable
data class ReaderPrefs(
    val mode: String = MODE_AUTO,
    val direction: String = DIR_LTR,
    val zoom: String = ZOOM_HEIGHT,
    val spread: String = SPREAD_SINGLE,
    val bg: String = BG_GRAY,
    val tapToTurn: Boolean = false,
    val scrollSpeed: Int = SCROLL_SPEED_DEFAULT,
) {
    val isDouble: Boolean get() = spread == SPREAD_DOUBLE
    val isRtl: Boolean get() = direction == DIR_RTL
}

/** Effective prefs, the resolved default (for "reset"), and the global preload-page count. */
data class ResolvedPrefs(val effective: ReaderPrefs, val default: ReaderPrefs, val preload: Int)

private val prefsJson = Json { ignoreUnknownKeys = true }

/** Built-in defaults: comics = auto + fit-height; PDFs = continuous + fit-width (matches the web). */
fun defaultReaderPrefs(kind: String): ReaderPrefs =
    if (kind == "pdf") ReaderPrefs(mode = MODE_CONTINUOUS, zoom = ZOOM_WIDTH) else ReaderPrefs()

private fun coerce(p: ReaderPrefs, kind: String): ReaderPrefs {
    val d = defaultReaderPrefs(kind)
    fun pick(v: String, allowed: List<String>, fb: String) = if (v in allowed) v else fb
    return p.copy(
        mode = pick(p.mode, listOf(MODE_AUTO, MODE_PAGED, MODE_CONTINUOUS), d.mode),
        direction = pick(p.direction, listOf(DIR_LTR, DIR_RTL), d.direction),
        zoom = pick(p.zoom, listOf(ZOOM_HEIGHT, ZOOM_WIDTH, ZOOM_ORIGINAL), d.zoom),
        spread = pick(p.spread, listOf(SPREAD_SINGLE, SPREAD_DOUBLE), d.spread),
        bg = pick(p.bg, listOf(BG_WHITE, BG_GRAY, BG_BLACK), d.bg),
        scrollSpeed = p.scrollSpeed.coerceIn(SCROLL_SPEED_MIN, SCROLL_SPEED_MAX),
    )
}

/** Global preload count (how many pages ahead to prefetch), clamped to [0, PRELOAD_MAX]. */
fun parsePreloadCount(settings: JsonObject): Int =
    (settings[PRELOAD_KEY] as? JsonPrimitive)?.intOrNull?.coerceIn(0, PRELOAD_MAX) ?: PRELOAD_DEFAULT

suspend fun savePreloadCount(api: KodexApi, baseUrl: String, apiKey: String, count: Int) {
    api.saveUserSetting(baseUrl, apiKey, PRELOAD_KEY, JsonPrimitive(count.coerceIn(0, PRELOAD_MAX)))
}

private fun defaultKey(kind: String) = "reader.$kind"
private fun seriesKey(kind: String, seriesId: String) = "reader.$kind.series.$seriesId"

private fun JsonObject.prefsAt(key: String, kind: String): ReaderPrefs? =
    (this[key] as? JsonObject)?.let { runCatching { coerce(prefsJson.decodeFromJsonElement(it), kind) }.getOrNull() }

/** Resolve prefs: this series' override → the user's `reader.<kind>` default → built-in. */
suspend fun resolveReaderPrefs(api: KodexApi, baseUrl: String, apiKey: String, kind: String, seriesId: String?): ResolvedPrefs {
    val settings = runCatching { api.userSettings(baseUrl, apiKey) }.getOrElse { JsonObject(emptyMap()) }
    val default = settings.prefsAt(defaultKey(kind), kind) ?: defaultReaderPrefs(kind)
    val override = seriesId?.let { settings.prefsAt(seriesKey(kind, it), kind) }
    return ResolvedPrefs(effective = override ?: default, default = default, preload = parsePreloadCount(settings))
}

/** Save the current prefs where this reader stores overrides (series override, or the default if none). */
suspend fun saveReaderOverride(api: KodexApi, baseUrl: String, apiKey: String, kind: String, seriesId: String?, prefs: ReaderPrefs) {
    val key = if (seriesId != null) seriesKey(kind, seriesId) else defaultKey(kind)
    api.saveUserSetting(baseUrl, apiKey, key, prefsJson.encodeToJsonElement(prefs))
}

/** The user's default prefs for a kind, parsed from an already-fetched settings object (no request). */
fun parseReaderDefault(settings: JsonObject, kind: String): ReaderPrefs =
    settings.prefsAt(defaultKey(kind), kind) ?: defaultReaderPrefs(kind)

/** Save the current prefs as the user-wide default for this kind. */
suspend fun saveReaderDefault(api: KodexApi, baseUrl: String, apiKey: String, kind: String, prefs: ReaderPrefs) {
    api.saveUserSetting(baseUrl, apiKey, defaultKey(kind), prefsJson.encodeToJsonElement(prefs))
}

/** Clear this series' override so it follows the user default again. */
suspend fun resetReaderOverride(api: KodexApi, baseUrl: String, apiKey: String, kind: String, seriesId: String?) {
    if (seriesId != null) api.saveUserSetting(baseUrl, apiKey, seriesKey(kind, seriesId), JsonNull)
    else api.saveUserSetting(baseUrl, apiKey, defaultKey(kind), prefsJson.encodeToJsonElement(defaultReaderPrefs(kind)))
}

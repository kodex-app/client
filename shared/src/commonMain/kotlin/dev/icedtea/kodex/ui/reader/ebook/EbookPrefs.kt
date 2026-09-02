package dev.icedtea.kodex.ui.reader.ebook

import dev.icedtea.kodex.network.KodexApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

// Values are the web reader's, verbatim: both clients read and write the same `reader.epub` settings,
// so a book configured in the browser opens the same way in the app (and the other way round).
const val FLOW_PAGINATED = "paginated"
const val FLOW_SCROLLED = "scrolled"
const val THEME_LIGHT = "light"
const val THEME_SEPIA = "sepia"
const val THEME_DARK = "dark"

/**
 * Follow the app's appearance: dark app → the dark page, light app → sepia rather than white, which
 * is the paper-like counterpart of a light UI and the gentler of the two on a lit screen.
 *
 * The app's own value — the web reader has no such mode, so it is resolved to a real theme before
 * ever reaching the page (see [forPage]). A book set to Auto here and opened in the browser falls
 * back to that reader's default rather than following anything.
 */
const val THEME_AUTO = "auto"
const val COLUMNS_AUTO = "auto"
const val COLUMNS_ONE = "one"
const val COLUMNS_TWO = "two"
const val ALIGN_AUTO = "auto"
const val ALIGN_LEFT = "left"
const val ALIGN_JUSTIFY = "justify"

/**
 * Page-turn animations, as `reader.js` names them. Not part of [EbookPrefs]: this one is the app's
 * own (the web reader has no such choice) and is stored device-side by `AppSettings.ebookPageAnim`.
 */
const val PAGE_ANIM_SLIDE = "slide"
const val PAGE_ANIM_FLIP = "flip"
const val PAGE_ANIM_NONE = "none"

val EBOOK_ANIMS = listOf(PAGE_ANIM_SLIDE, PAGE_ANIM_FLIP, PAGE_ANIM_NONE)

/**
 * `publisher` keeps the book's own fonts; otherwise `bundled:<id>` (an OFL face the server ships) or
 * `custom:<fontId>` (one the user uploaded). Both are fetched from the server — see `reader.js`.
 */
const val FONT_PUBLISHER = "publisher"

/**
 * The generic system stacks the reader used to offer. `reader.js` still renders them so a pref written
 * by an older client keeps working, and the picker shows one only while it is the current choice.
 */
val LEGACY_FONT_STACKS = mapOf("serif" to "Serif", "sans" to "Sans", "mono" to "Mono")

const val FONT_SIZE_MIN = 70
const val FONT_SIZE_MAX = 250
const val LINE_HEIGHT_MIN = 80
const val LINE_HEIGHT_MAX = 220
const val MARGIN_MIN = 0
const val MARGIN_MAX = 96

/** Display settings for a reflowable ebook. Serialized as-is into the user-settings store. */
@Serializable
data class EbookPrefs(
    val flow: String = FLOW_PAGINATED,
    val theme: String = THEME_LIGHT,
    val fontFamily: String = FONT_PUBLISHER,
    val columns: String = COLUMNS_AUTO,
    /** Percent of the publisher's base size. */
    val fontSize: Int = 100,
    /** Percent, where 100 = 1.5×. */
    val lineHeight: Int = 100,
    /** Page side margin, px. */
    val margin: Int = 24,
    val textAlign: String = ALIGN_AUTO,
    /** Paragraph indent in em; null keeps the publisher's own indentation. */
    val indent: Double? = null,
)

/** Effective prefs plus the resolved default, which "reset to default" falls back to. */
data class ResolvedEbookPrefs(val effective: EbookPrefs, val default: EbookPrefs)

/** The theme actually rendered: [THEME_AUTO] resolved against whether the app is drawing dark. */
fun EbookPrefs.resolvedTheme(dark: Boolean): String =
    if (theme == THEME_AUTO) (if (dark) THEME_DARK else THEME_SEPIA) else theme

/**
 * These prefs as the page should receive them. `reader.js` knows three themes and would fall through
 * to its default on a fourth, so [THEME_AUTO] is resolved here — the stored value keeps saying
 * "auto", and only what goes over the wire is a concrete theme.
 */
fun EbookPrefs.forPage(dark: Boolean): EbookPrefs =
    if (theme == THEME_AUTO) copy(theme = resolvedTheme(dark)) else this

private const val DEFAULT_KEY = "reader.epub"
private fun seriesKey(seriesId: String) = "reader.epub.series.$seriesId"

private val prefsJson = Json { ignoreUnknownKeys = true }

/** Clamps a loosely-typed stored value back into range — the web writes these too. */
private fun coerce(p: EbookPrefs): EbookPrefs {
    val d = EbookPrefs()
    fun pick(v: String, allowed: List<String>, fb: String) = if (v in allowed) v else fb
    return p.copy(
        flow = pick(p.flow, listOf(FLOW_PAGINATED, FLOW_SCROLLED), d.flow),
        theme = pick(p.theme, listOf(THEME_AUTO, THEME_LIGHT, THEME_SEPIA, THEME_DARK), d.theme),
        fontFamily = p.fontFamily.ifBlank { d.fontFamily },
        columns = pick(p.columns, listOf(COLUMNS_AUTO, COLUMNS_ONE, COLUMNS_TWO), d.columns),
        fontSize = p.fontSize.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX),
        lineHeight = p.lineHeight.coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX),
        margin = p.margin.coerceIn(MARGIN_MIN, MARGIN_MAX),
        textAlign = pick(p.textAlign, listOf(ALIGN_AUTO, ALIGN_LEFT, ALIGN_JUSTIFY), d.textAlign),
        indent = p.indent?.coerceIn(0.0, 4.0),
    )
}

private fun JsonObject.prefsAt(key: String): EbookPrefs? =
    (this[key] as? JsonObject)?.let { runCatching { coerce(prefsJson.decodeFromJsonElement(it)) }.getOrNull() }

/** Resolve prefs: this series' override → the user's `reader.epub` default → built-in. */
suspend fun resolveEbookPrefs(api: KodexApi, baseUrl: String, apiKey: String, seriesId: String?): ResolvedEbookPrefs {
    val settings = runCatching { api.userSettings(baseUrl, apiKey) }.getOrElse { JsonObject(emptyMap()) }
    val default = settings.prefsAt(DEFAULT_KEY) ?: EbookPrefs()
    val override = seriesId?.let { settings.prefsAt(seriesKey(it)) }
    return ResolvedEbookPrefs(effective = override ?: default, default = default)
}

/**
 * Persist an edit. With a series this writes that series' override — never the shared default, or one
 * book's choices would silently apply to every other book. A series-less read (a Browse preview with
 * nothing to scope to) has nowhere safe to store an override, so it stays in memory for the session.
 */
suspend fun saveEbookOverride(api: KodexApi, baseUrl: String, apiKey: String, seriesId: String?, prefs: EbookPrefs) {
    if (seriesId == null) return
    api.saveUserSetting(baseUrl, apiKey, seriesKey(seriesId), prefsJson.encodeToJsonElement(prefs))
}

/** Save the current prefs as the user-wide default (used by series without an override of their own). */
suspend fun saveEbookDefault(api: KodexApi, baseUrl: String, apiKey: String, prefs: EbookPrefs) {
    api.saveUserSetting(baseUrl, apiKey, DEFAULT_KEY, prefsJson.encodeToJsonElement(prefs))
}

/** Drop this series' override so it follows the user default again, without re-creating one. */
suspend fun resetEbookOverride(api: KodexApi, baseUrl: String, apiKey: String, seriesId: String?) {
    if (seriesId != null) api.saveUserSetting(baseUrl, apiKey, seriesKey(seriesId), JsonNull)
    else api.saveUserSetting(baseUrl, apiKey, DEFAULT_KEY, prefsJson.encodeToJsonElement(EbookPrefs()))
}

internal fun EbookPrefs.toJson(): String = prefsJson.encodeToString(EbookPrefs.serializer(), this)

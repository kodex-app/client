package dev.icedtea.kodex.ui.reader.ebook

import dev.icedtea.kodex.network.BundledFontDto
import dev.icedtea.kodex.network.CustomFontDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.reader.settingsKeyPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.math.roundToInt

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

/**
 * Display settings for a reflowable ebook. Serialized as-is into the user-settings store.
 *
 * A field added here needs adding to [fromJson] too — reads go field by field, not through the
 * generated decoder, so one this file does not read is one that silently stays at its default.
 */
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
/** Same escaping as the image reader's keys — see [settingsKeyPart]. */
private fun seriesKey(seriesId: String) = "reader.epub.series.${settingsKeyPart(seriesId)}"

/**
 * Reads tolerate unknown fields (the web may know one this client does not); writes spell every field
 * out. Without `encodeDefaults` a prefs object identical to the built-ins serialized to `{}`, which is
 * indistinguishable from having stored nothing — and the reader below now reads it as exactly that.
 * A complete object is also what the web stores, so both clients leave the same shape behind.
 */
private val prefsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** The fields [fromJson] knows. One of them present is what makes a stored object prefs at all. */
private val EBOOK_PREF_KEYS =
    listOf("flow", "theme", "fontFamily", "columns", "fontSize", "lineHeight", "margin", "textAlign", "indent")

/**
 * Read prefs out of a stored object one field at a time, each falling back to its own default.
 *
 * Decoding the object as a whole held every field hostage to every other: one value of the wrong type
 * — from an older client, a hand-edited setting, anything writing to this open JSON store — threw, the
 * whole override was thrown away, and the reader silently ran on defaults. Worse, the next edit then
 * wrote those defaults back over an object that had been almost entirely fine, turning a display
 * problem into lost settings. Field-wise, a bad value costs exactly itself.
 *
 * This is also the rule the web reader applies to these same objects (`coercePrefs` in
 * FoliateEpubReader.vue), so both clients now degrade the same way. It is a little more forgiving
 * about numbers: one stored as a string still reads, since there is only one thing it can mean.
 */
private fun fromJson(o: JsonObject): EbookPrefs {
    val d = EbookPrefs()
    fun str(key: String): String? = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun num(key: String): Double? = (o[key] as? JsonPrimitive)?.doubleOrNull
    fun pick(v: String?, allowed: List<String>, fallback: String) = if (v != null && v in allowed) v else fallback
    fun int(key: String, min: Int, max: Int, fallback: Int) = num(key)?.roundToInt()?.coerceIn(min, max) ?: fallback
    return EbookPrefs(
        flow = pick(str("flow"), listOf(FLOW_PAGINATED, FLOW_SCROLLED), d.flow),
        theme = pick(str("theme"), listOf(THEME_AUTO, THEME_LIGHT, THEME_SEPIA, THEME_DARK), d.theme),
        fontFamily = str("fontFamily")?.takeIf { it.isNotBlank() } ?: d.fontFamily,
        columns = pick(str("columns"), listOf(COLUMNS_AUTO, COLUMNS_ONE, COLUMNS_TWO), d.columns),
        fontSize = int("fontSize", FONT_SIZE_MIN, FONT_SIZE_MAX, d.fontSize),
        lineHeight = int("lineHeight", LINE_HEIGHT_MIN, LINE_HEIGHT_MAX, d.lineHeight),
        margin = int("margin", MARGIN_MIN, MARGIN_MAX, d.margin),
        textAlign = pick(str("textAlign"), listOf(ALIGN_AUTO, ALIGN_LEFT, ALIGN_JUSTIFY), d.textAlign),
        // Absent, null, or unreadable all mean the same thing here: keep the publisher's indentation.
        indent = num("indent")?.coerceIn(0.0, 4.0),
    )
}

private fun JsonObject.prefsAt(key: String): EbookPrefs? {
    val stored = this[key] as? JsonObject ?: return null
    // Nothing recognisable in it — treat it as absent rather than as an override of all-defaults, so
    // the read falls through to the user default instead of pinning this series to the built-ins.
    if (EBOOK_PREF_KEYS.none { it in stored }) return null
    return fromJson(stored)
}

/** Resolve prefs: this series' override → the user's `reader.epub` default → built-in. */
suspend fun resolveEbookPrefs(api: KodexApi, baseUrl: String, apiKey: String, seriesId: String?): ResolvedEbookPrefs {
    val settings = runCatching { api.userSettings(baseUrl, apiKey) }.getOrElse { JsonObject(emptyMap()) }
    return parseEbookPrefs(settings, seriesId)
}

/** The resolution itself, against an already-fetched settings object: no request, and testable. */
internal fun parseEbookPrefs(settings: JsonObject, seriesId: String?): ResolvedEbookPrefs {
    val default = settings.prefsAt(DEFAULT_KEY) ?: EbookPrefs()
    val override = seriesId?.let { settings.prefsAt(seriesKey(it)) }
    return ResolvedEbookPrefs(effective = override ?: default, default = default)
}

/**
 * Persist an edit. With a series this writes that series' override — never the shared default, or one
 * book's choices would silently apply to every other book.
 *
 * Without one (a standalone book, which has no series id at all) it writes the user default instead of
 * dropping the write, the way the image reader's `saveReaderOverride` always has. Returning early here
 * meant a standalone book's settings were never stored anywhere: they held for as long as the reader
 * stayed open and were gone the moment it was reopened, resolved back to the default the write never
 * reached. There is nothing riskier about it either — with no series to scope to, that default *is*
 * this book's setting.
 */
suspend fun saveEbookOverride(api: KodexApi, baseUrl: String, apiKey: String, seriesId: String?, prefs: EbookPrefs) {
    val key = if (seriesId != null) seriesKey(seriesId) else DEFAULT_KEY
    api.saveUserSetting(baseUrl, apiKey, key, prefsJson.encodeToJsonElement(prefs))
}

/** The user's default ebook prefs, parsed from an already-fetched settings object (no request). */
fun parseEbookDefault(settings: JsonObject): EbookPrefs = parseEbookPrefs(settings, seriesId = null).default

/**
 * What the font picker offers, in the order the web reader offers it: the book's own fonts, the faces
 * the server ships, then the user's uploads. Built here rather than at each picker so the reader and
 * the defaults screen cannot end up listing different fonts.
 *
 * [current] is included when it names one of the legacy `serif`/`sans`/`mono` stacks — no longer
 * offered, still rendered, and shown only while it is what the book is set to, so the picker has a
 * selection to point at.
 */
fun ebookFontOptions(
    current: String,
    bundled: List<BundledFontDto>,
    custom: List<CustomFontDto>,
): List<Pair<String, String>> = buildList {
    add(FONT_PUBLISHER to "Publisher")
    bundled.forEach { add("bundled:${it.id}" to it.family.ifBlank { it.id }) }
    custom.forEach { add("custom:${it.id}" to it.family.ifBlank { "Custom" }) }
    LEGACY_FONT_STACKS[current]?.let { add(current to it) }
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

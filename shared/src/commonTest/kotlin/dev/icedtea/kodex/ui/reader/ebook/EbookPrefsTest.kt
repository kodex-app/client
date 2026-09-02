package dev.icedtea.kodex.ui.reader.ebook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stored settings object is an open JSON store: the web reader writes it, this app writes it, and
 * nothing stops anything else from writing it. So what matters here is not the happy path (that one
 * never broke) but what a *partly* bad object does — one field of the wrong type used to take the
 * whole object down with it, silently resetting every setting for that series.
 */
class EbookPrefsTest {

    private fun settings(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val seriesId = "S1"
    private val seriesKey = "reader.epub.series.S1"

    @Test
    fun readsAStoredOverride() {
        val s = settings(
            """
            {"$seriesKey": {
              "flow": "scrolled", "theme": "sepia", "fontFamily": "bundled:lora", "columns": "two",
              "fontSize": 130, "lineHeight": 140, "margin": 40, "textAlign": "justify", "indent": 1.0
            }}
            """,
        )
        val p = parseEbookPrefs(s, seriesId).effective
        assertEquals(FLOW_SCROLLED, p.flow)
        assertEquals(THEME_SEPIA, p.theme)
        assertEquals("bundled:lora", p.fontFamily)
        assertEquals(COLUMNS_TWO, p.columns)
        assertEquals(130, p.fontSize)
        assertEquals(140, p.lineHeight)
        assertEquals(40, p.margin)
        assertEquals(ALIGN_JUSTIFY, p.textAlign)
        assertEquals(1.0, p.indent)
    }

    /** The regression this parser exists for: a bad field costs itself and nothing else. */
    @Test
    fun oneBadFieldDoesNotDiscardTheRest() {
        val s = settings(
            """
            {"$seriesKey": {
              "theme": null, "margin": {}, "fontSize": "not a number",
              "flow": "scrolled", "lineHeight": 140, "indent": 2.0
            }}
            """,
        )
        val p = parseEbookPrefs(s, seriesId).effective
        // The unreadable three fall back on their own...
        assertEquals(EbookPrefs().theme, p.theme)
        assertEquals(EbookPrefs().margin, p.margin)
        assertEquals(EbookPrefs().fontSize, p.fontSize)
        // ...while everything readable survives.
        assertEquals(FLOW_SCROLLED, p.flow)
        assertEquals(140, p.lineHeight)
        assertEquals(2.0, p.indent)
    }

    @Test
    fun readsNumbersWrittenLoosely() {
        val s = settings("""{"$seriesKey": {"fontSize": "120", "lineHeight": 130.0, "margin": 24.4}}""")
        val p = parseEbookPrefs(s, seriesId).effective
        assertEquals(120, p.fontSize)
        assertEquals(130, p.lineHeight)
        assertEquals(24, p.margin)
    }

    @Test
    fun clampsOutOfRangeValues() {
        val s = settings("""{"$seriesKey": {"fontSize": 9000, "lineHeight": 1, "margin": -20, "indent": 9.0}}""")
        val p = parseEbookPrefs(s, seriesId).effective
        assertEquals(FONT_SIZE_MAX, p.fontSize)
        assertEquals(LINE_HEIGHT_MIN, p.lineHeight)
        assertEquals(MARGIN_MIN, p.margin)
        assertEquals(4.0, p.indent)
    }

    @Test
    fun unknownValuesFallBackPerField() {
        val s = settings("""{"$seriesKey": {"theme": "neon", "flow": "sideways", "textAlign": "middle"}}""")
        val p = parseEbookPrefs(s, seriesId).effective
        assertEquals(EbookPrefs().theme, p.theme)
        assertEquals(EbookPrefs().flow, p.flow)
        assertEquals(EbookPrefs().textAlign, p.textAlign)
    }

    @Test
    fun acceptsTheAppsOwnAutoTheme() {
        val s = settings("""{"$seriesKey": {"theme": "auto"}}""")
        assertEquals(THEME_AUTO, parseEbookPrefs(s, seriesId).effective.theme)
    }

    @Test
    fun indentIsNullUnlessItIsAReadableNumber() {
        assertNull(parseEbookPrefs(settings("""{"$seriesKey": {"theme": "dark"}}"""), seriesId).effective.indent)
        assertNull(parseEbookPrefs(settings("""{"$seriesKey": {"indent": null}}"""), seriesId).effective.indent)
        assertNull(parseEbookPrefs(settings("""{"$seriesKey": {"indent": "auto"}}"""), seriesId).effective.indent)
        assertEquals(0.0, parseEbookPrefs(settings("""{"$seriesKey": {"indent": 0}}"""), seriesId).effective.indent)
    }

    @Test
    fun overrideBeatsDefaultWhichBeatsBuiltIn() {
        val both = settings(
            """{"reader.epub": {"fontSize": 110}, "$seriesKey": {"fontSize": 150}}""",
        )
        assertEquals(150, parseEbookPrefs(both, seriesId).effective.fontSize)
        assertEquals(110, parseEbookPrefs(both, seriesId).default.fontSize)
        // A series with no override of its own follows the user default.
        assertEquals(110, parseEbookPrefs(both, "OTHER").effective.fontSize)
        // Nothing stored at all: the built-ins.
        assertEquals(EbookPrefs(), parseEbookPrefs(settings("{}"), seriesId).effective)
    }

    /** An object that says nothing is not an override of all-defaults — it is no override. */
    @Test
    fun anEmptyOrUnrecognisableObjectReadsAsAbsent() {
        val empty = settings("""{"reader.epub": {"fontSize": 110}, "$seriesKey": {}}""")
        assertEquals(110, parseEbookPrefs(empty, seriesId).effective.fontSize)
        val junk = settings("""{"reader.epub": {"fontSize": 110}, "$seriesKey": {"colour": "red"}}""")
        assertEquals(110, parseEbookPrefs(junk, seriesId).effective.fontSize)
        // ...and neither is a value that isn't an object at all (this is what a cleared override is).
        val cleared = settings("""{"reader.epub": {"fontSize": 110}, "$seriesKey": null}""")
        assertEquals(110, parseEbookPrefs(cleared, seriesId).effective.fontSize)
    }

    /** A Browse read's series id is a URL path; the key it lands under has no slash left in it. */
    @Test
    fun sourceSeriesIdsResolveUnderAnEscapedKey() {
        val id = "src:1234:/truyen/abc"
        val s = settings("""{"reader.epub.series.src:1234:~truyen~abc": {"fontSize": 160}}""")
        assertEquals(160, parseEbookPrefs(s, id).effective.fontSize)
    }

    /** What this client writes has to be what the web reader (and this parser) can read back. */
    @Test
    fun writesEveryFieldSoNothingIsAmbiguous() {
        val written = Json.parseToJsonElement(EbookPrefs().toJson()).jsonObject
        listOf("flow", "theme", "fontFamily", "columns", "fontSize", "lineHeight", "margin", "textAlign", "indent")
            .forEach { assertTrue(it in written, "expected `$it` in the written object: $written") }
    }
}

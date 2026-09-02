package dev.icedtea.kodex.ui.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** The image reader's half of the same store — see EbookPrefsTest for what these are guarding. */
class ReaderPrefsTest {

    private fun settings(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val seriesId = "S1"
    private val comicKey = "reader.comic.series.S1"

    @Test
    fun readsAStoredOverride() {
        val s = settings(
            """
            {"$comicKey": {
              "mode": "continuous", "direction": "rtl", "zoom": "width", "spread": "double",
              "bg": "black", "tapToTurn": true, "scrollSpeed": 80
            }}
            """,
        )
        val p = parseReaderPrefs(s, "comic", seriesId).effective
        assertEquals(MODE_CONTINUOUS, p.mode)
        assertEquals(DIR_RTL, p.direction)
        assertEquals(ZOOM_WIDTH, p.zoom)
        assertEquals(SPREAD_DOUBLE, p.spread)
        assertEquals(BG_BLACK, p.bg)
        assertEquals(true, p.tapToTurn)
        assertEquals(80, p.scrollSpeed)
    }

    @Test
    fun oneBadFieldDoesNotDiscardTheRest() {
        val s = settings(
            """{"$comicKey": {"bg": null, "spread": ["double"], "mode": "paged", "tapToTurn": true}}""",
        )
        val p = parseReaderPrefs(s, "comic", seriesId).effective
        assertEquals(ReaderPrefs().bg, p.bg)
        assertEquals(ReaderPrefs().spread, p.spread)
        assertEquals(MODE_PAGED, p.mode)
        assertEquals(true, p.tapToTurn)
    }

    @Test
    fun readsLooselyWrittenBooleansAndNumbers() {
        val s = settings("""{"$comicKey": {"tapToTurn": "true", "scrollSpeed": 45.6}}""")
        val p = parseReaderPrefs(s, "comic", seriesId).effective
        assertEquals(true, p.tapToTurn)
        assertEquals(46, p.scrollSpeed)
    }

    @Test
    fun clampsScrollSpeed() {
        val fast = settings("""{"$comicKey": {"scrollSpeed": 9000}}""")
        assertEquals(SCROLL_SPEED_MAX, parseReaderPrefs(fast, "comic", seriesId).effective.scrollSpeed)
        val slow = settings("""{"$comicKey": {"scrollSpeed": 0}}""")
        assertEquals(SCROLL_SPEED_MIN, parseReaderPrefs(slow, "comic", seriesId).effective.scrollSpeed)
    }

    /** Each kind falls back to its own built-ins: PDFs open continuous and fit-width, comics do not. */
    @Test
    fun fallsBackPerKind() {
        val none = settings("{}")
        assertEquals(defaultReaderPrefs("comic"), parseReaderPrefs(none, "comic", seriesId).effective)
        assertEquals(MODE_CONTINUOUS, parseReaderPrefs(none, "pdf", seriesId).effective.mode)
        assertEquals(ZOOM_WIDTH, parseReaderPrefs(none, "pdf", seriesId).effective.zoom)
        // A field the stored object doesn't mention keeps the kind's default rather than the class's.
        val partial = settings("""{"reader.pdf": {"bg": "black"}}""")
        val p = parseReaderPrefs(partial, "pdf", null).effective
        assertEquals(BG_BLACK, p.bg)
        assertEquals(MODE_CONTINUOUS, p.mode)
    }

    @Test
    fun overrideBeatsDefaultWhichBeatsBuiltIn() {
        val both = settings("""{"reader.comic": {"bg": "white"}, "$comicKey": {"bg": "black"}}""")
        assertEquals(BG_BLACK, parseReaderPrefs(both, "comic", seriesId).effective.bg)
        assertEquals(BG_WHITE, parseReaderPrefs(both, "comic", seriesId).default.bg)
        assertEquals(BG_WHITE, parseReaderPrefs(both, "comic", "OTHER").effective.bg)
    }

    @Test
    fun anEmptyOrUnrecognisableObjectReadsAsAbsent() {
        val empty = settings("""{"reader.comic": {"bg": "white"}, "$comicKey": {}}""")
        assertEquals(BG_WHITE, parseReaderPrefs(empty, "comic", seriesId).effective.bg)
        val junk = settings("""{"reader.comic": {"bg": "white"}, "$comicKey": {"colour": "red"}}""")
        assertEquals(BG_WHITE, parseReaderPrefs(junk, "comic", seriesId).effective.bg)
    }

    @Test
    fun sourceSeriesIdsResolveUnderAnEscapedKey() {
        val id = "src:1234:/manga/abc"
        val s = settings("""{"reader.comic.series.src:1234:~manga~abc": {"bg": "black"}}""")
        assertEquals(BG_BLACK, parseReaderPrefs(s, "comic", id).effective.bg)
        assertEquals("src:1234:~manga~abc", settingsKeyPart(id))
        // Every id that could already be stored maps to itself, so old keys keep resolving.
        assertEquals("01JABCDEF", settingsKeyPart("01JABCDEF"))
    }

    @Test
    fun readsThePreloadCount() {
        assertEquals(PRELOAD_DEFAULT, parseReaderPrefs(settings("{}"), "comic", null).preload)
        assertEquals(10, parseReaderPrefs(settings("""{"reader.preloadCount": 10}"""), "comic", null).preload)
        assertEquals(PRELOAD_MAX, parseReaderPrefs(settings("""{"reader.preloadCount": 99}"""), "comic", null).preload)
        assertEquals(PRELOAD_DEFAULT, parseReaderPrefs(settings("""{"reader.preloadCount": "many"}"""), "comic", null).preload)
    }
}

package dev.icedtea.kodex.ui.catalog

import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.saveUserSetting
import dev.icedtea.kodex.network.userSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A series' list sort, persisted in the per-user settings store so it survives leaving the series and
 * restarting the app.
 *
 * Source-backed lists use the web UI's key (`series.chapterSort.<provider>.<sourceSeriesId>`, keyed by
 * source identity), so the choice is shared with the web and between a series in Browse and the same
 * series once added to the library. Local book lists have no web counterpart and are keyed by series id.
 */
data class SeriesSortPref(val key: SeriesSort, val desc: Boolean)

/** Key for a source-backed chapter list — must match `seriesChapterSortKey` in kodex-web. */
fun sourceSeriesSortKey(providerId: String, sourceSeriesId: String): String =
    "series.chapterSort.$providerId.${sourceSeriesId.replace('/', '~')}"

/** Key for a library series: the source key when it follows one, else a per-series book-sort key. */
fun librarySeriesSortKey(seriesId: String, providerId: String?, sourceSeriesId: String?): String =
    if (providerId != null && sourceSeriesId != null) sourceSeriesSortKey(providerId, sourceSeriesId)
    else "series.bookSort.${seriesId.replace('/', '~')}"

/** The stored sort for [settingKey], else (source lists only) the user's global chapter-sort default. */
suspend fun KodexApi.loadSeriesSort(baseUrl: String, apiKey: String, settingKey: String): SeriesSortPref? {
    val settings = userSettings(baseUrl, apiKey)
    fun str(k: String) = runCatching { settings[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
    val raw = str(settingKey) ?: str("series.chapterSort").takeIf { settingKey.startsWith("series.chapterSort.") }
    return raw?.let(::parseSeriesSort)
}

suspend fun KodexApi.saveSeriesSort(baseUrl: String, apiKey: String, settingKey: String, pref: SeriesSortPref) {
    val field = when (pref.key) {
        SeriesSort.NUMBER -> "number"
        SeriesSort.DATE -> "releaseDate"
        SeriesSort.SOURCE -> "source"
    }
    saveUserSetting(baseUrl, apiKey, settingKey, JsonPrimitive("$field,${if (pref.desc) "desc" else "asc"}"))
}

/** Same `field,dir` format the web writes; an unknown field is ignored rather than guessed at. */
private fun parseSeriesSort(raw: String): SeriesSortPref? {
    val parts = raw.split(",")
    val key = when (parts.getOrNull(0)) {
        "number" -> SeriesSort.NUMBER
        "releaseDate" -> SeriesSort.DATE
        "source" -> SeriesSort.SOURCE
        else -> return null
    }
    return SeriesSortPref(key, parts.getOrNull(1) != "asc")
}

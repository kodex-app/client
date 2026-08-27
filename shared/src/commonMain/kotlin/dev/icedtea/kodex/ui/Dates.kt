package dev.icedtea.kodex.ui

import dev.icedtea.kodex.platform.nowMillis

/**
 * Tiny UTC date helpers for the day-grouped feeds (Updates / History). kotlinx-datetime isn't on the
 * classpath, so we parse the server's ISO-8601 instants (`2026-07-25T03:38:11.156Z`) by hand — enough
 * for day keys, Today/Yesterday labels, and coarse relative times.
 */

/** The UTC calendar day of an ISO instant as `yyyy-MM-dd`, or "" if unparseable. Used as a group key. */
fun isoDayKey(iso: String?): String {
    if (iso == null || iso.length < 10) return ""
    return iso.substring(0, 10)
}

/** Epoch millis (UTC) of an ISO instant, or null. Sub-second precision is dropped. */
fun isoEpochMillis(iso: String?): Long? {
    if (iso == null || iso.length < 19) return null
    return runCatching {
        val year = iso.substring(0, 4).toInt()
        val month = iso.substring(5, 7).toInt()
        val day = iso.substring(8, 10).toInt()
        val hour = iso.substring(11, 13).toInt()
        val min = iso.substring(14, 16).toInt()
        val sec = iso.substring(17, 19).toInt()
        (daysFromCivil(year, month, day) * 86_400L + hour * 3600L + min * 60L + sec) * 1000L
    }.getOrNull()
}

/** A human day header for a `yyyy-MM-dd` key: "Today", "Yesterday", or e.g. "Jul 25, 2026". */
fun dayLabel(dayKey: String): String {
    if (dayKey.length < 10) return dayKey
    val todayEpochDay = nowMillis() / 86_400_000L
    val keyEpochDay = runCatching {
        daysFromCivil(dayKey.substring(0, 4).toInt(), dayKey.substring(5, 7).toInt(), dayKey.substring(8, 10).toInt())
    }.getOrNull() ?: return dayKey
    return when (todayEpochDay - keyEpochDay) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> {
            val month = MONTHS.getOrElse(dayKey.substring(5, 7).toInt() - 1) { "" }
            "$month ${dayKey.substring(8, 10).trimStart('0')}, ${dayKey.substring(0, 4)}"
        }
    }
}

/** A coarse relative label like "Just now", "5m ago", "3h ago", "2d ago" (falls back to the day). */
fun relativeTime(iso: String?): String {
    val millis = isoEpochMillis(iso) ?: return isoDayKey(iso)
    val diff = nowMillis() - millis
    return when {
        // Sentence case: every caller puts this first in its line, next to labels like "Finished".
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> dayLabel(isoDayKey(iso))
    }
}

/** The current instant as an ISO-8601 UTC string (`yyyy-MM-ddTHH:mm:ssZ`) — for history clear windows. */
fun nowIsoUtc(): String = epochMillisToIso(nowMillis())

/** Start-of-day (UTC) N days ago, as an ISO instant — e.g. `clearFrom = daysAgoIsoUtc(7)`. */
fun daysAgoIsoUtc(days: Int): String {
    val epochDay = nowMillis() / 86_400_000L - days
    return epochMillisToIso(epochDay * 86_400_000L)
}

/**
 * Whole-day ISO bounds for a picked date. The Material date picker hands back UTC midnight, so the
 * window is widened to that day's full span before it becomes an inclusive [from, to] for the API.
 */
fun isoAtStartOfDay(millis: Long): String = epochMillisToIso(millis - (millis.mod(DAY_MS)))

fun isoAtEndOfDay(millis: Long): String = epochMillisToIso(millis - (millis.mod(DAY_MS)) + DAY_MS - 1)

private const val DAY_MS = 86_400_000L

private fun epochMillisToIso(millis: Long): String {
    val days = millis.floorDiv(86_400_000L)
    var rem = millis.mod(86_400_000L) / 1000L
    val hour = rem / 3600L; rem %= 3600L
    val min = rem / 60L; val sec = rem % 60L
    val (y, m, d) = civilFromDays(days)
    fun p(n: Long) = n.toString().padStart(2, '0')
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "$y-${p(m)}-${p(d)}T${p(hour)}:${p(min)}:${p(sec)}Z"
}

// Howard Hinnant's civil <-> days algorithms (days since 1970-01-01).
private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = (yy - era * 400).toLong()
    val doy = ((153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1).toLong()
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146097L + doe - 719468L
}

private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

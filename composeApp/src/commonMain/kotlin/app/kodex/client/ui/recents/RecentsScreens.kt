package app.kodex.client.ui.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.HistoryEntryDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.UpdateDto
import app.kodex.client.ui.PagedList
import app.kodex.client.ui.catalog.DayHeader
import app.kodex.client.ui.catalog.MediaRow
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.dayLabel
import app.kodex.client.ui.daysAgoIsoUtc
import app.kodex.client.ui.isoDayKey
import app.kodex.client.ui.main.OpenBrowseReader
import app.kodex.client.ui.main.OpenSourceReader
import app.kodex.client.ui.main.SourceSeriesContext
import app.kodex.client.ui.nowIsoUtc
import app.kodex.client.ui.relativeTime
import kotlinx.coroutines.launch

/**
 * Updates — new source chapters for followed WEB series, grouped by discovery day. A downloaded
 * chapter opens the local reader; otherwise it streams live from its source.
 */
@Composable
fun UpdatesList(
    session: SessionManager,
    api: KodexApi,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenSeries: (String) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    val current = server ?: return
    val baseUrl = current.baseUrl
    val apiKey = current.apiKey

    val paged = app.kodex.client.ui.rememberPagedList(current.id) { page ->
        api.updates(baseUrl, apiKey, page)
    }

    // Live: new chapters arrive when a WEB library finishes updating or books are imported.
    app.kodex.client.ui.OnServerEvent(
        app.kodex.client.network.ServerEvent.LIBRARY_SCAN_COMPLETED,
        app.kodex.client.network.ServerEvent.BOOK_ADDED,
    ) { paged.silentRefresh() }

    PagedList(paged, emptyText = "No updates yet.\nFollow a series in Browse to see new chapters here.") { items ->
        val groups = groupByDayThenSeries(
            items,
            dayKeyOf = { isoDayKey(it.foundDate) },
            // Followed series have an id; a series without one still groups by its name.
            seriesKeyOf = { it.seriesId ?: it.seriesName },
            labelOf = { it.seriesName },
        )
        groupedByDayAndSeries(
            groups,
            header = { group ->
                val first = group.items.first()
                MediaRow(
                    coverUrl = sourceCoverUrl(baseUrl, first.providerId ?: "", first.coverUrl),
                    apiKey = apiKey,
                    title = group.label,
                    subtitle = null,
                    caption = if (group.items.size > 1) "${group.items.size} new chapters" else null,
                    onClick = first.seriesId?.let { sid -> { onOpenSeries(sid) } },
                    onCoverClick = first.seriesId?.let { sid -> { onOpenSeries(sid) } },
                )
            },
        ) { u ->
            ChapterSubRow(
                title = u.chapterName ?: "New chapter",
                caption = relativeTime(u.foundDate) + (if (u.bookId != null) " · downloaded" else ""),
                onClick = {
                    when {
                        u.bookId != null -> onOpenReader(u.bookId!!)
                        u.providerId != null && u.chapterId != null ->
                            onOpenSourceReader(u.providerId!!, u.chapterId!!, u.seriesId, u.chapterName)
                        else -> Unit
                    }
                },
            )
        }
    }
}

/**
 * History — everything read across all libraries, newest first, grouped by day. A `BOOK` entry
 * re-opens the local book reader; a `SOURCE` entry re-opens the streaming reader.
 */
@Composable
fun HistoryList(
    session: SessionManager,
    api: KodexApi,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader = { _, _, _ -> },
    onOpenSeries: (String) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    val current = server ?: return
    val baseUrl = current.baseUrl
    val apiKey = current.apiKey
    val scope = rememberCoroutineScope()
    val snackbar = app.kodex.client.ui.rememberSnackbar()
    var menuOpen by remember { mutableStateOf(false) }

    val paged = app.kodex.client.ui.rememberPagedList(current.id) { page ->
        api.history(baseUrl, apiKey, page)
    }

    fun clear(from: String?, to: String?, label: String) {
        scope.launch {
            runCatching { api.clearHistory(baseUrl, apiKey, from, to) }.fold(
                onSuccess = { paged.refresh(); snackbar?.show(label) },
                onFailure = { snackbar?.show("Couldn't clear history. Please try again.") },
            )
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "History options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { androidx.compose.material3.Text("Clear today") }, onClick = {
                    menuOpen = false; clear(daysAgoIsoUtc(0), nowIsoUtc(), "Today's history cleared")
                })
                DropdownMenuItem(text = { androidx.compose.material3.Text("Clear last 7 days") }, onClick = {
                    menuOpen = false; clear(daysAgoIsoUtc(7), nowIsoUtc(), "Last 7 days cleared")
                })
                DropdownMenuItem(text = { androidx.compose.material3.Text("Clear all history") }, onClick = {
                    menuOpen = false; clear(null, null, "History cleared")
                })
            }
        }

        PagedList(paged, emptyText = "No reading history yet.") { items ->
            val groups = groupByDayThenSeries(
                items,
                dayKeyOf = { isoDayKey(it.readDate) },
                // Library series first, then the source series a Browse read was attributed to.
                seriesKeyOf = { it.seriesId ?: it.sourceSeriesId ?: it.seriesName },
                labelOf = { it.seriesName.ifBlank { it.title.orEmpty() } },
            )
            groupedByDayAndSeries(
                groups,
                header = { group ->
                    val first = group.items.first()
                    MediaRow(
                        coverUrl = historyCover(baseUrl, first),
                        apiKey = apiKey,
                        title = group.label,
                        subtitle = null,
                        caption = if (group.items.size > 1) "${group.items.size} chapters" else null,
                        onClick = first.seriesId?.let { sid -> { onOpenSeries(sid) } },
                        onCoverClick = first.seriesId?.let { sid -> { onOpenSeries(sid) } },
                    )
                },
            ) { h ->
                ChapterSubRow(
                    title = h.title?.takeIf { it.isNotBlank() } ?: h.seriesName,
                    caption = "${relativeTime(h.readDate)} · ${if (h.completed) "Finished" else "Page ${h.page}"}",
                    onClick = { openHistoryEntry(h, onOpenReader, onOpenSourceReader, onOpenBrowseReader) },
                )
            }
        }
    }
}

/** Cover for a history entry: a local book's own cover, else its series', else the source's. */
private fun historyCover(baseUrl: String, h: HistoryEntryDto): String = when {
    h.isBook && h.bookId != null -> bookCoverUrl(baseUrl, h.bookId!!)
    h.seriesId != null && h.coverUrl.isNullOrBlank() -> seriesCoverUrl(baseUrl, h.seriesId!!, null)
    else -> sourceCoverUrl(baseUrl, h.providerId ?: "", h.coverUrl)
}

/** Re-opens whatever a history entry points at: a local book, a followed chapter, or a Browse read. */
private fun openHistoryEntry(
    h: HistoryEntryDto,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader,
) {
    val provider = h.providerId
    val chapter = h.chapterId
    val sourceSeries = h.sourceSeriesId
    when {
        h.isBook && h.bookId != null -> onOpenReader(h.bookId!!)
        provider == null || chapter == null -> Unit
        // Read while browsing (no local series): re-open with the source series' identity so the
        // reader can rebuild its chapter navigation from the source's live list.
        h.seriesId == null && sourceSeries != null ->
            onOpenBrowseReader(
                SourceSeriesContext(provider, sourceSeries, h.seriesName.ifBlank { h.title.orEmpty() }, h.coverUrl),
                chapter,
                h.title,
            )
        else -> onOpenSourceReader(provider, chapter, h.seriesId, h.title)
    }
}

/** One series' items within a single day, kept in feed order. */
private class SeriesGroup<T>(val day: String, val label: String, val items: List<T>)

/**
 * Buckets a newest-first feed by day, then by series within each day. Both levels keep
 * first-appearance order, so the feed still reads newest-first — series are only pulled together, not
 * re-sorted. Grouping runs over the whole accumulated list, so a series split across a page boundary
 * still lands in one group once the later page arrives.
 */
private fun <T> groupByDayThenSeries(
    items: List<T>,
    dayKeyOf: (T) -> String,
    seriesKeyOf: (T) -> String,
    labelOf: (T) -> String,
): List<SeriesGroup<T>> {
    val byDay = LinkedHashMap<String, LinkedHashMap<String, MutableList<T>>>()
    for (item in items) {
        byDay.getOrPut(dayKeyOf(item)) { LinkedHashMap() }
            .getOrPut(seriesKeyOf(item)) { mutableListOf() }
            .add(item)
    }
    return buildList {
        byDay.forEach { (day, series) ->
            series.values.forEach { group -> add(SeriesGroup(day, labelOf(group.first()), group)) }
        }
    }
}

/**
 * Emits day → series sections: a sticky [DayHeader] when the day changes, then a [header] row per
 * series carrying the cover, then a compact [row] per item beneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
private inline fun <T> LazyListScope.groupedByDayAndSeries(
    groups: List<SeriesGroup<T>>,
    crossinline header: @Composable (SeriesGroup<T>) -> Unit,
    crossinline row: @Composable (T) -> Unit,
) {
    var lastDay: String? = null
    groups.forEachIndexed { gi, group ->
        if (group.day != lastDay) {
            lastDay = group.day
            stickyHeader(key = "day-${group.day}-$gi") { DayHeader(dayLabel(group.day), Modifier.fillMaxWidth()) }
        }
        item(key = "series-$gi") { header(group) }
        group.items.forEachIndexed { i, item -> item(key = "row-$gi-$i") { row(item) } }
    }
}

/**
 * A chapter under a series header. Text-only and indented to line up with the header's title column,
 * so the series cover isn't repeated once per chapter.
 */
@Composable
private fun ChapterSubRow(title: String, caption: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 72.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

package app.kodex.client.ui.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        groupedByDay(items, dayKeyOf = { isoDayKey(it.foundDate) }) { u ->
            val open = {
                when {
                    u.bookId != null -> onOpenReader(u.bookId!!)
                    u.providerId != null && u.chapterId != null ->
                        onOpenSourceReader(u.providerId!!, u.chapterId!!, u.seriesId, u.chapterName)
                    else -> Unit
                }
            }
            MediaRow(
                coverUrl = sourceCoverUrl(baseUrl, u.providerId ?: "", u.coverUrl),
                apiKey = apiKey,
                title = u.seriesName,
                subtitle = u.chapterName ?: "New chapter",
                caption = relativeTime(u.foundDate) + (if (u.bookId != null) " · downloaded" else ""),
                onClick = open,
                onCoverClick = u.seriesId?.let { sid -> { onOpenSeries(sid) } },
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
            groupedByDay(items, dayKeyOf = { isoDayKey(it.readDate) }) { h ->
                HistoryRow(baseUrl, apiKey, h, onOpenReader, onOpenSourceReader, onOpenBrowseReader, onOpenSeries)
            }
        }
    }
}

@Composable
private fun HistoryRow(
    baseUrl: String,
    apiKey: String,
    h: HistoryEntryDto,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader,
    onOpenSeries: (String) -> Unit,
) {
    val cover = when {
        h.isBook && h.bookId != null -> bookCoverUrl(baseUrl, h.bookId!!)
        h.seriesId != null && h.coverUrl.isNullOrBlank() -> seriesCoverUrl(baseUrl, h.seriesId!!, null)
        else -> sourceCoverUrl(baseUrl, h.providerId ?: "", h.coverUrl)
    }
    val open = {
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
    val progress = if (h.completed) "Finished" else "Page ${h.page}"
    MediaRow(
        coverUrl = cover,
        apiKey = apiKey,
        title = h.seriesName.ifBlank { h.title ?: "" },
        subtitle = h.title ?: "",
        caption = "${relativeTime(h.readDate)} · $progress",
        onClick = open,
        onCoverClick = h.seriesId?.let { sid -> { onOpenSeries(sid) } },
    )
}

/**
 * Emits [items] into a LazyColumn as day-grouped sections: a sticky [DayHeader] whenever the day key
 * changes, followed by [row] for each item. Items are assumed already sorted newest-first.
 */
@OptIn(ExperimentalFoundationApi::class)
private inline fun <T> LazyListScope.groupedByDay(
    items: List<T>,
    crossinline dayKeyOf: (T) -> String,
    crossinline row: @Composable (T) -> Unit,
) {
    var lastDay: String? = null
    items.forEachIndexed { index, item ->
        val day = dayKeyOf(item)
        if (day != lastDay) {
            lastDay = day
            stickyHeader(key = "day-$day-$index") { DayHeader(dayLabel(day), Modifier.fillMaxWidth()) }
        }
        item(key = "row-$index") { row(item) }
    }
}

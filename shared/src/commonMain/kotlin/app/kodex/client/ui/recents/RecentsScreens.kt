package app.kodex.client.ui.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
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
import app.kodex.client.ui.isoAtEndOfDay
import app.kodex.client.ui.isoAtStartOfDay
import app.kodex.client.ui.isoDayKey
import app.kodex.client.ui.isoEpochMillis
import app.kodex.client.ui.main.OpenBrowseReader
import app.kodex.client.ui.main.OpenSourceReader
import app.kodex.client.ui.main.SourceSeriesContext
import app.kodex.client.ui.nav.retain
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

    val paged = app.kodex.client.ui.rememberPagedList(current.id, retainKey = "updates") { page ->
        api.updates(baseUrl, apiKey, page)
    }
    // Retained with the rows, so opening a chapter and coming back lands where you left off.
    val listState = retain("updates:scroll") { LazyListState() }

    // Bumped alongside the feed so the "last updated" line below re-reads with it.
    var lastCheckReload by remember { mutableIntStateOf(0) }

    // Live: new chapters arrive when a WEB library finishes updating or books are imported.
    app.kodex.client.ui.OnServerEvent(
        app.kodex.client.network.ServerEvent.LIBRARY_SCAN_COMPLETED,
        app.kodex.client.network.ServerEvent.BOOK_ADDED,
    ) { paged.silentRefresh(); lastCheckReload++ }

    // When the server last checked the sources: the newest refresh stamp across the WEB libraries —
    // updates only ever come from those. It sits above the feed whether or not the feed has anything in
    // it, because "nothing new since 20m ago" and "never checked" are very different answers to why a
    // day looks empty.
    var lastRefreshed by remember(current.id) { mutableStateOf<String?>(null) }
    var anyWebLibrary by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id, lastCheckReload) {
        val webLibraries = runCatching { api.libraries(baseUrl, apiKey) }.getOrDefault(emptyList()).filter { it.isWeb }
        anyWebLibrary = webLibraries.isNotEmpty()
        lastRefreshed = webLibraries.mapNotNull { it.lastRefreshedDate }.maxByOrNull { isoEpochMillis(it) ?: 0L }
    }

    val collapsed = rememberCollapsedGroups("updates")
    Column(Modifier.fillMaxSize()) {
        if (anyWebLibrary) {
            Text(
                lastRefreshed?.let { "Last updated · ${relativeTime(it)}" } ?: "Not checked for updates yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PagedList(
            paged,
            emptyText = "No updates yet.\nFollow a series in Browse to see new chapters here.",
            modifier = Modifier.weight(1f),
            listState = listState,
        ) { items ->
            val groups = groupByDayThenSeries(
                items,
                dayKeyOf = { isoDayKey(it.foundDate) },
                // Followed series have an id; a series without one still groups by its name.
                seriesKeyOf = { it.seriesId ?: it.seriesName },
                labelOf = { it.seriesName },
            )
            groupedByDayAndSeries(
                groups,
                collapsed = collapsed,
                // A series folds shut by default: the newest chapter is already on the header line, so the
                // whole day's series fit on screen and only a series you actually want the backlog of costs
                // a tap.
                seriesCollapsedByDefault = true,
                header = { group ->
                    // Feed order is newest-first, so the first item is the latest chapter found.
                    val latest = group.items.first()
                    MediaRow(
                        coverUrl = sourceCoverUrl(baseUrl, latest.providerId ?: "", latest.coverUrl),
                        apiKey = apiKey,
                        title = group.label,
                        subtitle = latest.chapterName ?: "New chapter",
                        caption = updateCaption(latest) +
                            (if (group.items.size > 1) " · +${group.items.size - 1} more" else ""),
                        // The title line is the chapter you'd read next, so it opens the reader; the
                        // series detail hangs off the cover instead.
                        onClick = { openUpdate(latest, onOpenReader, onOpenSourceReader) },
                        onCoverClick = latest.seriesId?.let { sid -> { onOpenSeries(sid) } },
                    )
                },
            ) { u ->
                ChapterSubRow(
                    title = u.chapterName ?: "New chapter",
                    caption = updateCaption(u),
                    onClick = { openUpdate(u, onOpenReader, onOpenSourceReader) },
                )
            }
        }
    }
}

/** Opens what an update points at: the downloaded book if there is one, else the live source chapter. */
private fun openUpdate(
    u: UpdateDto,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    when {
        u.bookId != null -> onOpenReader(u.bookId)
        u.providerId != null && u.chapterId != null ->
            onOpenSourceReader(u.providerId, u.chapterId, u.seriesId, u.chapterName)
        else -> Unit
    }
}

/** When a chapter turned up, plus whether it is already on the server — same wording either way. */
private fun updateCaption(u: UpdateDto): String =
    relativeTime(u.foundDate) + (if (u.bookId != null) " · downloaded" else "")

/** What was read: the chapter's own title, falling back to the series when it hasn't got one. */
private fun historyEntryTitle(h: HistoryEntryDto): String =
    h.title?.takeIf { it.isNotBlank() } ?: h.seriesName

/** When it was read and how far it got — the History counterpart of [updateCaption]. */
private fun historyCaption(h: HistoryEntryDto): String =
    "${relativeTime(h.readDate)} · ${if (h.completed) "Finished" else "Page ${h.page}"}"

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
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryEntryDto?>(null) }
    var rangePicker by remember { mutableStateOf(false) }
    val collapsed = rememberCollapsedGroups("history")

    val paged = app.kodex.client.ui.rememberPagedList(current.id, retainKey = "history") { page ->
        api.history(baseUrl, apiKey, page)
    }
    val listState = retain("history:scroll") { LazyListState() }

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
            // Every scope is confirm-gated: clearing history deletes the progress rows behind it, so
            // it also drops saved positions and read flags. Mirrors the web's confirm dialog.
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Clear today") }, onClick = {
                    menuOpen = false
                    pendingClear = PendingClear("today's history", daysAgoIsoUtc(0), nowIsoUtc(), "Today's history cleared")
                })
                DropdownMenuItem(text = { Text("Clear last 7 days") }, onClick = {
                    menuOpen = false
                    pendingClear = PendingClear("the last 7 days of history", daysAgoIsoUtc(7), nowIsoUtc(), "Last 7 days cleared")
                })
                DropdownMenuItem(text = { Text("Clear a date range…") }, onClick = {
                    menuOpen = false; rangePicker = true
                })
                DropdownMenuItem(
                    text = { Text("Clear all history", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        pendingClear = PendingClear("all history", null, null, "History cleared")
                    },
                )
            }
        }

        PagedList(paged, emptyText = "No reading history yet.", listState = listState) { items ->
            val groups = groupByDayThenSeries(
                items,
                dayKeyOf = { isoDayKey(it.readDate) },
                // Library series first, then the source series a Browse read was attributed to.
                seriesKeyOf = { it.seriesId ?: it.sourceSeriesId ?: it.seriesName },
                labelOf = { it.seriesName.ifBlank { it.title.orEmpty() } },
            )
            groupedByDayAndSeries(
                groups,
                collapsed = collapsed,
                // Folded shut like Updates: the latest chapter you read is already on the header line,
                // so a day's series fit on screen and only a backlog you want open costs a tap.
                seriesCollapsedByDefault = true,
                header = { group ->
                    // Newest-first, so the first entry is the last thing read in this series.
                    val latest = group.items.first()
                    MediaRow(
                        coverUrl = historyCover(baseUrl, latest),
                        apiKey = apiKey,
                        title = group.label,
                        subtitle = historyEntryTitle(latest),
                        caption = historyCaption(latest) +
                            (if (group.items.size > 1) " · +${group.items.size - 1} more" else ""),
                        // Same split as Updates: the title line resumes what was last read here, the
                        // cover goes to the series.
                        onClick = { openHistoryEntry(latest, onOpenReader, onOpenSourceReader, onOpenBrowseReader) },
                        onCoverClick = latest.seriesId?.let { sid -> { onOpenSeries(sid) } },
                    )
                },
            ) { h ->
                ChapterSubRow(
                    title = historyEntryTitle(h),
                    caption = historyCaption(h),
                    onClick = { openHistoryEntry(h, onOpenReader, onOpenSourceReader, onOpenBrowseReader) },
                    onDelete = { pendingDelete = h },
                )
            }
        }
    }

    pendingClear?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("Clear history?") },
            text = {
                Text(
                    "This removes ${pending.what} and the saved reading position that goes with each entry. " +
                        "Books and downloads are not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = null
                    clear(pending.from, pending.to, pending.done)
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text("Cancel") } },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove from history?") },
            text = {
                Text(
                    "“${entry.title?.takeIf { it.isNotBlank() } ?: entry.seriesName}” loses its saved position " +
                        "and read state — not obvious from a delete on a \"history\" row, hence the ask.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { api.deleteHistoryEntry(baseUrl, apiKey, entry.id) }.fold(
                            onSuccess = { paged.refresh(); snackbar?.show("Removed from history") },
                            onFailure = { snackbar?.show("Couldn't remove that entry. Please try again.") },
                        )
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (rangePicker) {
        HistoryRangeDialog(
            onDismiss = { rangePicker = false },
            onPick = { from, to, label ->
                rangePicker = false
                pendingClear = PendingClear("history from $label", from, to, "History cleared for $label")
            },
        )
    }
}

/** A clear the user has asked for but not yet confirmed. */
private class PendingClear(val what: String, val from: String?, val to: String?, val done: String)

/**
 * Date-range picker for a custom clear. Bounds are widened to whole days locally before being sent as
 * the endpoint's inclusive [from, to] window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRangeDialog(onDismiss: () -> Unit, onPick: (from: String, to: String, label: String) -> Unit) {
    val state = rememberDateRangePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear a date range") },
        text = {
            Box(Modifier.heightIn(max = 480.dp)) {
                DateRangePicker(state = state, showModeToggle = false)
            }
        },
        confirmButton = {
            val start = state.selectedStartDateMillis
            val end = state.selectedEndDateMillis
            TextButton(
                enabled = start != null && end != null,
                onClick = {
                    if (start != null && end != null) {
                        onPick(isoAtStartOfDay(start), isoAtEndOfDay(end), "${isoDayKey(isoAtStartOfDay(start))} – ${isoDayKey(isoAtEndOfDay(end))}")
                    }
                },
            ) { Text("Choose") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Cover for a history entry: a local book's own cover, else its series', else the source's. */
private fun historyCover(baseUrl: String, h: HistoryEntryDto): String = when {
    h.isBook && h.bookId != null -> bookCoverUrl(baseUrl, h.bookId)
    h.seriesId != null && h.coverUrl.isNullOrBlank() -> seriesCoverUrl(baseUrl, h.seriesId, null)
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
        h.isBook && h.bookId != null -> onOpenReader(h.bookId)
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
private class SeriesGroup<T>(val key: String, val label: String, val items: List<T>)

/** One day's activity, its series in newest-first order. [total] is what a collapsed day reports. */
private class DayGroup<T>(val day: String, val series: List<SeriesGroup<T>>) {
    val total: Int get() = series.sumOf { it.items.size }
}

/**
 * Buckets a newest-first feed by day, then by series within each day. Both levels keep
 * first-appearance order, so the feed still reads newest-first — series are only pulled together, not
 * re-sorted. Grouping runs over the whole accumulated list, so a series split across a page boundary
 * still lands in one group once the later page arrives, and appending a page only extends existing
 * groups or adds new ones at the end (nothing already on screen jumps).
 */
private fun <T> groupByDayThenSeries(
    items: List<T>,
    dayKeyOf: (T) -> String,
    seriesKeyOf: (T) -> String,
    labelOf: (T) -> String,
): List<DayGroup<T>> {
    val byDay = LinkedHashMap<String, LinkedHashMap<String, MutableList<T>>>()
    val labels = HashMap<String, String>()
    for (item in items) {
        val day = dayKeyOf(item)
        val key = seriesKeyOf(item)
        byDay.getOrPut(day) { LinkedHashMap() }.getOrPut(key) { mutableListOf() }.add(item)
        labels.getOrPut("$day/$key") { labelOf(item) }
    }
    return byDay.map { (day, series) ->
        DayGroup(day, series.map { (key, group) -> SeriesGroup(key, labels["$day/$key"].orEmpty(), group) })
    }
}

/**
 * Fold state for the day and series headings. Holds the groups whose state is *flipped from their
 * default*, so the same set works for a list that starts expanded and one that starts collapsed.
 * Session-local and keyed by group rather than by index, so it survives the regrouping that every
 * infinite-scroll page causes — and a day you folded away doesn't greet you folded on a later visit.
 *
 * Retained per list ([retainKey]), so a group you opened is still open when you come back from
 * whatever you tapped inside it.
 */
@Composable
private fun rememberCollapsedGroups(retainKey: String): MutableState<Set<String>> =
    retain("$retainKey:collapsed") { mutableStateOf(emptySet()) }

private fun dayCollapseKey(day: String) = "d:$day"
private fun seriesCollapseKey(day: String, seriesKey: String) = "s:$day:$seriesKey"

private fun MutableState<Set<String>>.toggle(key: String) {
    value = if (key in value) value - key else value + key
}

/**
 * Emits day → series sections, each heading collapsible: a sticky [DayHeader] when the day changes,
 * then a [header] row per series carrying the cover, then a compact [row] per item beneath it.
 * [seriesCollapsedByDefault] picks which way an untouched series starts.
 */
@OptIn(ExperimentalFoundationApi::class)
private inline fun <T> LazyListScope.groupedByDayAndSeries(
    groups: List<DayGroup<T>>,
    collapsed: MutableState<Set<String>>,
    seriesCollapsedByDefault: Boolean = false,
    crossinline header: @Composable (SeriesGroup<T>) -> Unit,
    crossinline row: @Composable (T) -> Unit,
) {
    groups.forEachIndexed { di, day ->
        val dayKey = dayCollapseKey(day.day)
        val dayCollapsed = dayKey in collapsed.value
        stickyHeader(key = "day-${day.day}") {
            CollapsibleDayHeader(
                label = dayLabel(day.day),
                collapsed = dayCollapsed,
                count = day.total,
                onToggle = { collapsed.toggle(dayKey) },
            )
        }
        if (dayCollapsed) return@forEachIndexed
        day.series.forEachIndexed { si, group ->
            val key = seriesCollapseKey(day.day, group.key)
            val seriesCollapsed = (key in collapsed.value) != seriesCollapsedByDefault
            item(key = "series-$di-$si") {
                CollapsibleSeriesHeader(collapsed = seriesCollapsed, onToggle = { collapsed.toggle(key) }) {
                    header(group)
                }
            }
            if (seriesCollapsed) return@forEachIndexed
            group.items.forEachIndexed { i, item -> item(key = "row-$di-$si-$i") { row(item) } }
        }
    }
}

/** Day heading that folds its whole day away; shows the entry count once collapsed. */
@Composable
private fun CollapsibleDayHeader(label: String, collapsed: Boolean, count: Int, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayHeader(label)
        if (collapsed) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (collapsed) "Expand $label" else "Collapse $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

/**
 * Wraps a series header row with its own fold control. The chevron is a separate target so tapping
 * the row itself still opens the series.
 */
@Composable
private fun CollapsibleSeriesHeader(collapsed: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { content() }
        IconButton(onClick = onToggle) {
            Icon(
                if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A chapter under a series header. Text-only and indented to line up with the header's title column,
 * so the series cover isn't repeated once per chapter.
 */
@Composable
private fun ChapterSubRow(title: String, caption: String?, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick)
            .padding(start = 72.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
    onDelete?.let {
        IconButton(onClick = it) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
  }
}

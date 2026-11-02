package dev.icedtea.kodex.ui.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.icedtea.kodex.ui.catalog.CoverImage
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.HistoryEntryDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.UpdateDto
import dev.icedtea.kodex.network.clearHistory
import dev.icedtea.kodex.network.deleteHistoryEntry
import dev.icedtea.kodex.network.history
import dev.icedtea.kodex.network.libraries
import dev.icedtea.kodex.network.updates
import dev.icedtea.kodex.ui.PagedList
import dev.icedtea.kodex.ui.catalog.bookCoverUrl
import dev.icedtea.kodex.ui.catalog.seriesCoverUrl
import dev.icedtea.kodex.ui.catalog.sourceCoverUrl
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.dayLabel
import dev.icedtea.kodex.ui.daysAgoIsoUtc
import dev.icedtea.kodex.ui.isoAtEndOfDay
import dev.icedtea.kodex.ui.isoAtStartOfDay
import dev.icedtea.kodex.ui.isoDayKey
import dev.icedtea.kodex.ui.isoEpochMillis
import dev.icedtea.kodex.ui.main.OpenBrowseReader
import dev.icedtea.kodex.ui.main.OpenSourceReader
import dev.icedtea.kodex.ui.main.SourceSeriesContext
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.nowIsoUtc
import dev.icedtea.kodex.ui.relativeTime
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

    val paged = dev.icedtea.kodex.ui.rememberPagedList(current.id, retainKey = "updates", keyOf = { it.id }) { page ->
        api.updates(baseUrl, apiKey, page)
    }
    // Retained with the rows, so opening a chapter and coming back lands where you left off.
    val listState = retain("updates:scroll") { LazyListState() }

    // Bumped alongside the feed so the "last updated" line below re-reads with it.
    var lastCheckReload by remember { mutableIntStateOf(0) }

    // Live: new chapters arrive when a WEB library finishes updating or books are imported.
    dev.icedtea.kodex.ui.OnServerEvent(
        dev.icedtea.kodex.network.ServerEvent.LIBRARY_SCAN_COMPLETED,
        dev.icedtea.kodex.network.ServerEvent.BOOK_ADDED,
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    lastRefreshed?.let { "Last updated · ${relativeTime(it)}" } ?: "Not checked for updates yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PagedList(
            paged,
            emptyText = "No updates yet.\nFollow a series in Browse to see new chapters here.",
            modifier = Modifier.weight(1f),
            listState = listState,
            emptyIcon = Icons.Outlined.AutoAwesome,
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
                header = { group, open, onToggle ->
                    // Feed order is newest-first, so the first item is the latest chapter found.
                    val latest = group.items.first()
                    SeriesHeaderRow(
                        coverUrl = sourceCoverUrl(baseUrl, latest.providerId ?: "", latest.coverUrl),
                        apiKey = apiKey,
                        title = group.label,
                        subtitle = latest.chapterName ?: "New chapter",
                        caption = relativeTime(latest.foundDate),
                        pill = updatePill(latest),
                        more = group.items.size - 1,
                        open = open,
                        // The title line is the chapter you'd read next, so it opens the reader; the
                        // series detail hangs off the cover instead.
                        onClick = { openUpdate(latest, onOpenReader, onOpenSourceReader) },
                        onCoverClick = latest.seriesId?.let { sid -> { onOpenSeries(sid) } },
                        onToggle = onToggle,
                    )
                },
            ) { u, last ->
                ChapterSubRow(
                    title = u.chapterName ?: "New chapter",
                    caption = relativeTime(u.foundDate),
                    pill = updatePill(u),
                    last = last,
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

/** An update that is already on the server is tagged as such. */
private fun updatePill(u: UpdateDto): StatusPill? = if (u.bookId != null) StatusPill.DOWNLOADED else null

/** What was read: the chapter's own title, falling back to the series when it hasn't got one. */
private fun historyEntryTitle(h: HistoryEntryDto): String =
    h.title?.takeIf { it.isNotBlank() } ?: h.seriesName

/** When it was read and, unless it was finished (that's the pill), how far it got. */
private fun historyCaption(h: HistoryEntryDto): String =
    relativeTime(h.readDate) + if (h.completed) "" else " · Page ${h.page}"

private fun historyPill(h: HistoryEntryDto): StatusPill? = if (h.completed) StatusPill.READ else null

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
    val snackbar = dev.icedtea.kodex.ui.rememberSnackbar()
    var menuOpen by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryEntryDto?>(null) }
    var rangePicker by remember { mutableStateOf(false) }
    val collapsed = rememberCollapsedGroups("history")

    val paged = dev.icedtea.kodex.ui.rememberPagedList(current.id, retainKey = "history", keyOf = { it.id }) { page ->
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Everything you've read, newest first",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
          Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }

        PagedList(
            paged,
            emptyText = "No reading history yet.\nWhat you read shows up here, grouped by day.",
            modifier = Modifier.weight(1f),
            listState = listState,
            emptyIcon = Icons.Outlined.History,
        ) { items ->
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
                header = { group, open, onToggle ->
                    // Newest-first, so the first entry is the last thing read in this series.
                    val latest = group.items.first()
                    SeriesHeaderRow(
                        coverUrl = historyCover(baseUrl, latest),
                        apiKey = apiKey,
                        title = group.label,
                        subtitle = historyEntryTitle(latest),
                        caption = historyCaption(latest),
                        pill = historyPill(latest),
                        more = group.items.size - 1,
                        open = open,
                        // Same split as Updates: the title line resumes what was last read here, the
                        // cover goes to the series.
                        onClick = { openHistoryEntry(latest, onOpenReader, onOpenSourceReader, onOpenBrowseReader) },
                        onCoverClick = latest.seriesId?.let { sid -> { onOpenSeries(sid) } },
                        onToggle = onToggle,
                    )
                },
            ) { h, last ->
                ChapterSubRow(
                    title = historyEntryTitle(h),
                    caption = historyCaption(h),
                    pill = historyPill(h),
                    last = last,
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
 * Emits day → series sections, each heading collapsible: a sticky day heading when the day changes,
 * then a [header] row per series carrying the cover, then a compact [row] per item beneath it — on a
 * thin timeline, all on one tinted surface while the series is open. [header] is told whether the
 * series is open and gets the toggle to put on its "+N" chip; [row] is told whether it's the last one,
 * which rounds the surface off. [seriesCollapsedByDefault] picks which way an untouched series starts.
 */
@OptIn(ExperimentalFoundationApi::class)
private inline fun <T> LazyListScope.groupedByDayAndSeries(
    groups: List<DayGroup<T>>,
    collapsed: MutableState<Set<String>>,
    seriesCollapsedByDefault: Boolean = false,
    crossinline header: @Composable (group: SeriesGroup<T>, open: Boolean, onToggle: () -> Unit) -> Unit,
    crossinline row: @Composable (item: T, last: Boolean) -> Unit,
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
            // A single-entry series has nothing to unfold: its one entry is the header line already.
            val open = !seriesCollapsed && group.items.size > 1
            item(key = "series-$di-$si") {
                SeriesSurface(open = open, top = true, bottom = !open) {
                    header(group, open) { collapsed.toggle(key) }
                }
            }
            if (!open) return@forEachIndexed
            group.items.forEachIndexed { i, item ->
                val last = i == group.items.lastIndex
                item(key = "row-$di-$si-$i") {
                    SeriesSurface(open = true, top = false, bottom = last) { row(item, last) }
                }
            }
        }
    }
}

/**
 * The surface a series sits on: transparent while folded, a soft tint once open so the header and its
 * entries read as one block. Split across lazy items, so each piece rounds only its own outer corners.
 */
@Composable
private fun SeriesSurface(open: Boolean, top: Boolean, bottom: Boolean, content: @Composable () -> Unit) {
    val r = 14.dp
    val shape = RoundedCornerShape(
        topStart = if (top) r else 0.dp, topEnd = if (top) r else 0.dp,
        bottomStart = if (bottom) r else 0.dp, bottomEnd = if (bottom) r else 0.dp,
    )
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .padding(bottom = if (bottom) 4.dp else 0.dp)
            .clip(shape)
            .background(if (open) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent),
    ) { content() }
}

/**
 * Day heading — the label, a hairline running to the edge, the day's count and a fold chevron; the
 * whole strip folds the day away.
 */
@Composable
private fun CollapsibleDayHeader(label: String, collapsed: Boolean, count: Int, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        Text(
            "$count",
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (collapsed) "Expand $label" else "Collapse $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One series line: cover (→ the series), title + the newest entry + caption (→ the reader), and at the
 * end a "+N" chip that unfolds the rest of the day's entries — three targets, kept apart.
 */
@Composable
private fun SeriesHeaderRow(
    coverUrl: String,
    apiKey: String,
    title: String,
    subtitle: String,
    caption: String,
    pill: StatusPill?,
    more: Int,
    open: Boolean,
    onClick: () -> Unit,
    onCoverClick: (() -> Unit)?,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 72.dp)
                .shadow(3.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onCoverClick != null) Modifier.clickable(onClick = onCoverClick) else Modifier),
        ) {
            if (coverUrl.isNotBlank()) CoverImage(coverUrl, apiKey, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1)
                pill?.let { StatusPillChip(it) }
            }
        }
        if (more > 0) {
            Row(
                Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).clickable(onClick = onToggle)
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+$more", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A small state tag on a row — "Downloaded" on an update, "Read" on a finished history entry. */
private enum class StatusPill(val label: String) { DOWNLOADED("Downloaded"), READ("Read") }

@Composable
private fun StatusPillChip(pill: StatusPill) {
    val (bg, fg) = when (pill) {
        StatusPill.DOWNLOADED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusPill.READ -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Text(
        pill.label,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
    )
}

/**
 * A chapter under an open series: text only, on a timeline whose line and dot sit under the centre of
 * the series cover, so the cover isn't repeated once per chapter.
 */
@Composable
private fun ChapterSubRow(
    title: String,
    caption: String?,
    last: Boolean,
    onClick: () -> Unit,
    pill: StatusPill? = null,
    onDelete: (() -> Unit)? = null,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                val x = 32.dp.toPx()
                // The line runs the full row, stopping at the dot on the last entry.
                drawLine(line, Offset(x, 0f), Offset(x, if (last) size.height / 2 else size.height), strokeWidth = 2.dp.toPx())
                drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, size.height / 2))
            }
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!caption.isNullOrBlank() || pill != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!caption.isNullOrBlank()) {
                        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    pill?.let { StatusPillChip(it) }
                }
            }
        }
        onDelete?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

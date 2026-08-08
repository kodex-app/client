package app.kodex.client.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.network.CategoryDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.network.SeriesGroupCount
import app.kodex.client.network.ServerEvent
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.OnServerEvent
import app.kodex.client.ui.SelectionActionBar
import app.kodex.client.ui.SelectionTopBar
import app.kodex.client.ui.TooltipIconButton
import app.kodex.client.ui.catalog.SeriesGrid
import app.kodex.client.ui.catalog.SeriesListView
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberSelection
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Mihon-style sort keys — one row each, with a direction toggled by tapping the selected key. */
private enum class SortKey(val label: String, val field: String, val defaultAsc: Boolean) {
    TITLE("Title", "title", true),
    NAME("Name", "name", true),
    DATE_ADDED("Date added", "createdDate", false),
    DATE_UPDATED("Date updated", "lastModifiedDate", false),
    TOTAL_CHAPTERS("Total chapters", "totalChapters", false),
    UNREAD("Unread count", "unreadCount", false),
    LAST_READ("Last read", "lastRead", false),
}

// Mihon/web-style tri-state filter: a facet cycles neutral → include → exclude.
private enum class Tri { INCLUDE, EXCLUDE }

private fun Tri?.next(): Tri? = when (this) {
    null -> Tri.INCLUDE
    Tri.INCLUDE -> Tri.EXCLUDE
    Tri.EXCLUDE -> null
}

// Reading-state buckets shown under READING STATE (server `readingStatus` values).
private data class ReadingOption(val label: String, val value: String)

private val READING_OPTIONS = listOf(
    ReadingOption("Unread", "NOT_STARTED"),
    ReadingOption("Started", "IN_PROGRESS"),
    ReadingOption("Read", "COMPLETED"),
)

// One tab in the group strip shown over the grid when a grouping dimension is active.
private data class GroupTab(val key: String, val label: String, val count: Int)

// Fixed order for the status dimension's tabs (mirrors the web's STATUS_ORDER).
private val STATUS_ORDER = listOf("ONGOING", "COMPLETED", "PUBLISHING_FINISHED", "LICENSED", "CANCELLED", "ON_HIATUS", "UNKNOWN")

/** A single library's series, with sort · reading-status filter · grid/list toggle · refresh. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibrarySeriesScreen(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    library: LibraryDto,
    onBack: () -> Unit,
    onOpenSeries: (SeriesDto) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    val gridView by appSettings.libraryGridView.collectAsStateSafe()
    val displayBy by appSettings.libraryDisplayBy.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var sortKey by remember { mutableStateOf(SortKey.TITLE) }
    var sortAsc by remember { mutableStateOf(SortKey.TITLE.defaultAsc) }
    var downloadedTri by remember { mutableStateOf<Tri?>(null) }
    val readingTri = remember { androidx.compose.runtime.mutableStateMapOf<String, Tri>() }
    var statusTri by remember { mutableStateOf<Tri?>(null) } // COMPLETED series status
    var reloadTick by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf(0) }
    // Seeded from the per-library store rather than defaulting to "none", so reopening a library
    // comes back grouped the way it was left (the web keeps the same two values in localStorage).
    var groupBy by remember(library.id) { mutableStateOf(appSettings.libraryGroupBy(library.id)) } // none | status | source | category
    var selectedGroup by remember(library.id) { mutableStateOf(appSettings.libraryGroupTab(library.id, appSettings.libraryGroupBy(library.id))) }
    var groups by remember { mutableStateOf<List<SeriesGroupCount>>(emptyList()) }
    var groupNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // key → friendly label (source/category)
    var allSeriesIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var categories by remember { mutableStateOf<List<app.kodex.client.network.CategoryDto>>(emptyList()) }
    var categoriesDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val selection = rememberSelection<String>()

    val activeFilters = readingTri.size +
        (if (downloadedTri != null) 1 else 0) +
        (if (statusTri != null) 1 else 0)

    val sortExpr = "${sortKey.field},${if (sortAsc) "asc" else "desc"}"

    // Derived include/exclude buckets for the tri-state reading-state filter.
    val readingInclude = READING_OPTIONS.map { it.value }.filter { readingTri[it] == Tri.INCLUDE }
    val readingExclude = READING_OPTIONS.map { it.value }.filter { readingTri[it] == Tri.EXCLUDE }

    // Server-backed sort choice: one `ui.librarySort` setting holding a { libraryId → "field,dir" } map,
    // so the choice follows the user across devices (mirrors the web's useLibrarySort).
    var sortMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(server?.id, library.id) {
        val s = server ?: return@LaunchedEffect
        val settings = runCatching { api.userSettings(s.baseUrl, s.apiKey) }.getOrNull() ?: return@LaunchedEffect
        val map = (settings["ui.librarySort"] as? kotlinx.serialization.json.JsonObject)
            ?.mapNotNull { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { k to it } }
            ?.toMap().orEmpty()
        sortMap = map
        map[library.id]?.let { expr ->
            val parts = expr.split(",")
            SortKey.entries.find { it.field == parts.getOrNull(0) }?.let { key ->
                sortKey = key
                sortAsc = parts.getOrNull(1) != "desc"
            }
        }
    }

    fun persistSort() {
        val s = server ?: return
        val newMap = sortMap + (library.id to "${sortKey.field},${if (sortAsc) "asc" else "desc"}")
        sortMap = newMap
        scope.launch {
            runCatching {
                api.saveUserSetting(
                    s.baseUrl, s.apiKey, "ui.librarySort",
                    kotlinx.serialization.json.JsonObject(newMap.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }),
                )
            }
        }
    }

    // Live per-group counts for the current grouping dimension.
    LaunchedEffect(library.id, server?.id, reloadTick, groupBy) {
        val s = server ?: return@LaunchedEffect
        groups = if (groupBy == "none") emptyList()
        else runCatching { api.seriesGroups(s.baseUrl, s.apiKey, groupBy, library.id) }.getOrDefault(emptyList()).filter { it.count > 0 }
        groupNames = when (groupBy) {
            "source" -> runCatching { api.contentSources(s.baseUrl, s.apiKey).associate { it.id to it.displayName } }.getOrDefault(emptyMap())
            "category" -> runCatching { api.categories(s.baseUrl, s.apiKey).associate { it.id to it.name } }.getOrDefault(emptyMap())
            else -> emptyMap()
        }
    }

    // Categories available for the bulk-assign action (WEB libraries).
    LaunchedEffect(library.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        categories = if (library.isWeb) runCatching { api.categories(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) else emptyList()
    }

    // System back exits selection mode first (before leaving the screen).
    app.kodex.client.platform.AppBackHandler(enabled = selection.active) { selection.clear() }

    // A finished scan of THIS library means the series list changed → reload.
    OnServerEvent(ServerEvent.LIBRARY_SCAN_COMPLETED) { e ->
        if (e.data["libraryId"]?.jsonPrimitive?.contentOrNull == library.id) reloadTick++
    }

    // Run [action] for each selected series, then clear selection + reload.
    fun bulkForEach(message: String, action: suspend (String, String, String) -> Unit) {
        val s = server ?: return
        val ids = selection.selected.toList()
        if (ids.isEmpty()) return
        scope.launch {
            runCatching { ids.forEach { action(s.baseUrl, s.apiKey, it) } }.fold(
                onSuccess = { snackbar?.show(message); selection.clear(); reloadTick++ },
                onFailure = { snackbar?.show("Action failed (check permissions).") },
            )
        }
    }

    fun bulkMark(read: Boolean) = bulkForEach(if (read) "Marked read" else "Marked unread") { b, k, id -> api.markSeriesRead(b, k, id, read) }

    fun refresh() {
        val s = server ?: return
        scope.launch {
            runCatching { api.refreshLibrary(s.baseUrl, s.apiKey, library.id) }.fold(
                onSuccess = { snackbar?.show("Refreshing ${library.name}…") },
                onFailure = { snackbar?.show("Couldn't start refresh (need manage permission).") },
            )
        }
    }

    // Group strip: when a dimension is active, series split into tabs (one per group), sorted for display.
    val tabGroups: List<GroupTab> = groups.map { g ->
        val label = when (groupBy) {
            "source", "category" -> groupNames[g.key] ?: g.key
            else -> g.key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        GroupTab(g.key, label, g.count.toInt())
    }.let { list ->
        if (groupBy == "status") list.sortedBy { STATUS_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        else list.sortedBy { it.label.lowercase() }
    }

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionTopBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(allSeriesIds) },
                    onSelectInverse = { selection.selectInverse(allSeriesIds) },
                )
            } else {
                TopAppBar(
                    title = { Text(library.name, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                        IconButton(onClick = { sheetTab = 0; sheetOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "View options") }
                    },
                )
            }
        },
        floatingActionButton = {
            // Hidden while selecting: the selection bottom bar owns the screen then, and a FAB over it
            // would sit on top of its actions.
            if (!selection.active) {
                ExtendedFloatingActionButton(
                    onClick = { sheetTab = 1; sheetOpen = true },
                    icon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                    text = { Text(if (activeFilters > 0) "Filter ($activeFilters)" else "Filter") },
                )
            }
        },
        bottomBar = {
            if (selection.active) {
                SelectionBottomBar(
                    isWeb = library.isWeb,
                    onMarkRead = { bulkMark(true) },
                    onMarkUnread = { bulkMark(false) },
                    onUpdate = { bulkForEach("Updating…") { b, k, id -> api.refreshSeriesChapters(b, k, id) } },
                    onDownload = { bulkForEach("Downloading…") { b, k, id -> api.downloadWebSeries(b, k, library.id, id, null) } },
                    onCategories = { categoriesDialog = true },
                    onRemove = { confirmRemove = true },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val filters = SeriesFilterArgs(
                libraryId = library.id, sortExpr = sortExpr,
                readingInclude = readingInclude, readingExclude = readingExclude,
                statusInclude = statusTri == Tri.INCLUDE, statusExclude = statusTri == Tri.EXCLUDE,
                downloaded = downloadedTri?.let { it == Tri.INCLUDE }, groupBy = groupBy,
            )
            if (groupBy != "none" && tabGroups.isNotEmpty()) {
                // Grouped: a tab per group, and the content is a pager so groups can be swiped between.
                val initialPage = tabGroups.indexOfFirst { it.key == selectedGroup }.coerceAtLeast(0)
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage) { tabGroups.size }
                // Keep the persisted/active group in sync with the page the user swiped or tapped to.
                LaunchedEffect(pagerState.currentPage, tabGroups) {
                    tabGroups.getOrNull(pagerState.currentPage)?.let {
                        selectedGroup = it.key
                        appSettings.setLibraryGroupTab(library.id, groupBy, it.key)
                    }
                }
                GroupTabs(tabGroups, tabGroups.getOrNull(pagerState.currentPage)?.key, onSelect = { key ->
                    val idx = tabGroups.indexOfFirst { it.key == key }
                    if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                })
                androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    LibrarySeriesResults(
                        server, api, filters, groupKey = tabGroups.getOrNull(page)?.key, reloadTick = reloadTick,
                        gridView = gridView, displayBy = displayBy, selection = selection, onOpenSeries = onOpenSeries,
                        onIdsLoaded = { if (page == pagerState.currentPage) allSeriesIds = it },
                    )
                }
            } else {
                LibrarySeriesResults(
                    server, api, filters, groupKey = null, reloadTick = reloadTick,
                    gridView = gridView, displayBy = displayBy, selection = selection, onOpenSeries = onOpenSeries,
                    onIdsLoaded = { allSeriesIds = it },
                )
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            val tabs = listOf("Sort", "Filter", "Group", "Display")
            val sheetPager = androidx.compose.foundation.pager.rememberPagerState(sheetTab) { tabs.size }
            // Transparent container so the tab strip blends with the sheet (no mismatched white band/seam).
            SecondaryTabRow(
                selectedTabIndex = sheetPager.currentPage,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = sheetPager.currentPage == i,
                        onClick = { scope.launch { sheetPager.animateScrollToPage(i) } },
                        text = { Text(t, style = MaterialTheme.typography.titleSmall) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            // Each page scrolls independently. The height band is on the pager rather than the content
            // so the sheet keeps a steady height while swiping instead of resizing under the gesture.
            androidx.compose.foundation.pager.HorizontalPager(
                state = sheetPager,
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 440.dp),
            ) { sheetTab ->
              Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
              ) {
                when (sheetTab) {
                    0 -> SortKey.entries.forEach { key ->
                        val selected = key == sortKey
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = selected, onClick = {
                                    if (selected) sortAsc = !sortAsc else { sortKey = key; sortAsc = key.defaultAsc }
                                    persistSort()
                                })
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.layout.Box(Modifier.size(24.dp), Alignment.Center) {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                key.label, Modifier.padding(start = 12.dp).weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (selected) Icon(
                                if (sortAsc) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (sortAsc) "Ascending" else "Descending",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    1 -> {
                        TriRow("Downloaded", downloadedTri) { downloadedTri = downloadedTri.next() }
                        SheetLabel("Reading state")
                        READING_OPTIONS.forEach { opt ->
                            TriRow(opt.label, readingTri[opt.value]) {
                                val n = readingTri[opt.value].next()
                                if (n == null) readingTri.remove(opt.value) else readingTri[opt.value] = n
                            }
                        }
                        SheetLabel("Series status")
                        TriRow("Completed", statusTri) { statusTri = statusTri.next() }
                        FilterLegend()
                    }

                    2 -> {
                        SheetLabel("Group by")
                        val opts = buildList {
                            add("none" to "None"); add("status" to "Status"); add("source" to "Source")
                            if (library.isWeb) add("category" to "Category")
                        }
                        opts.forEach { (value, label) ->
                            CheckRow(label, groupBy == value) {
                                groupBy = value
                                appSettings.setLibraryGroupBy(library.id, value)
                                // A new dimension has different tabs, so fall back to whichever one
                                // was last open under it (null → the first).
                                selectedGroup = appSettings.libraryGroupTab(library.id, value)
                            }
                        }
                        if (groupBy != "none") {
                            Text(
                                "Series split into a tab per group — swipe to switch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    else -> {
                        SheetLabel("View as")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            SegmentedButton(selected = gridView, onClick = { appSettings.setLibraryGridView(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Grid") }
                            SegmentedButton(selected = !gridView, onClick = { appSettings.setLibraryGridView(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("List") }
                        }
                        SheetLabel("Display by")
                        CheckRow("Series title", displayBy == "title") { appSettings.setLibraryDisplayBy("title") }
                        CheckRow("Folder name", displayBy == "name") { appSettings.setLibraryDisplayBy("name") }
                    }
                }
              }
            }
        }
    }

    if (categoriesDialog && server != null) {
        CategoriesDialog(
            categories = categories,
            onDismiss = { categoriesDialog = false },
            onApply = { add ->
                categoriesDialog = false
                val s = server!!
                val ids = selection.selected.toList()
                scope.launch {
                    runCatching { api.assignCategories(s.baseUrl, s.apiKey, ids, add, emptyList()) }.fold(
                        onSuccess = { snackbar?.show("Categories updated"); selection.clear(); reloadTick++ },
                        onFailure = { snackbar?.show("Couldn't update categories.") },
                    )
                }
            },
        )
    }
    if (confirmRemove && server != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${selection.count} series?") },
            text = { Text("They'll be removed from this library. Downloaded files are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    bulkForEach("Removed") { b, k, id -> api.unfollowWebSeries(b, k, library.id, id, false) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}


/** Contextual bottom action bar for the multi-select mode — the bulk functions as icon buttons. */
@Composable
private fun SelectionBottomBar(
    isWeb: Boolean,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onUpdate: () -> Unit,
    onDownload: () -> Unit,
    onCategories: () -> Unit,
    onRemove: () -> Unit,
) {
    SelectionActionBar {
        TooltipIconButton("Mark as read", onMarkRead) { Icon(Icons.Filled.Check, contentDescription = "Mark as read") }
        TooltipIconButton("Mark as unread", onMarkUnread) { Icon(Icons.Filled.MarkAsUnread, contentDescription = "Mark as unread") }
        if (isWeb) {
            TooltipIconButton("Update", onUpdate) { Icon(Icons.Filled.Refresh, contentDescription = "Update") }
            TooltipIconButton("Download", onDownload) { Icon(Icons.Filled.Download, contentDescription = "Download") }
            TooltipIconButton("Add to categories", onCategories) { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Add to categories") }
            TooltipIconButton("Remove", onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        }
    }
}


/** The shared query facets (everything except the group key), passed to each pager page / the flat list. */
private data class SeriesFilterArgs(
    val libraryId: String,
    val sortExpr: String,
    val readingInclude: List<String>,
    val readingExclude: List<String>,
    val statusInclude: Boolean,
    val statusExclude: Boolean,
    val downloaded: Boolean?,
    val groupBy: String,
)

/**
 * One group's series (or the whole library when [groupKey] is null): loads with the active filters and
 * renders the grid/list. Extracted so it can back both the flat list and each swipeable pager page.
 */
@Composable
private fun LibrarySeriesResults(
    server: app.kodex.client.data.model.ServerConnection?,
    api: KodexApi,
    filters: SeriesFilterArgs,
    groupKey: String?,
    reloadTick: Int,
    gridView: Boolean,
    displayBy: String,
    selection: app.kodex.client.ui.SelectionState<String>,
    onOpenSeries: (SeriesDto) -> Unit,
    onIdsLoaded: (List<String>) -> Unit,
) {
    LoadedContent(
        key = listOf(filters.libraryId, server?.id, filters.sortExpr, filters.readingInclude, filters.readingExclude, filters.downloaded, filters.statusInclude, filters.statusExclude, filters.groupBy, groupKey, reloadTick),
        load = {
            val s = server!!
            val statuses = buildList {
                if (filters.groupBy == "status") groupKey?.let { add(it) }
                if (filters.statusInclude) add("COMPLETED")
            }
            api.querySeries(
                s.baseUrl, s.apiKey,
                libraryId = filters.libraryId,
                sort = filters.sortExpr,
                readingStatuses = filters.readingInclude,
                readingStatusExcludes = filters.readingExclude,
                statuses = statuses,
                statusExcludes = if (filters.statusExclude) listOf("COMPLETED") else emptyList(),
                downloaded = filters.downloaded,
                sources = if (filters.groupBy == "source") groupKey?.let { listOf(it) } ?: emptyList() else emptyList(),
                categoryIds = if (filters.groupBy == "category") groupKey?.let { listOf(it) } ?: emptyList() else emptyList(),
            )
        },
    ) { series ->
        onIdsLoaded(series.map { it.id })
        val titleOf: (SeriesDto) -> String = { if (displayBy == "name") it.name.ifBlank { it.title } else it.title.ifBlank { it.name } }
        when {
            series.isEmpty() -> EmptyMessage("No series match this filter.")
            server != null && gridView -> SeriesGrid(server.baseUrl, server.apiKey, series, onOpenSeries, selection = selection, titleOf = titleOf)
            server != null -> SeriesListView(server.baseUrl, server.apiKey, series, onOpenSeries, selection = selection, titleOf = titleOf)
        }
    }
}

/** Horizontal tab strip over the grid: one tab per group (with count) when a dimension is active. */
@Composable
private fun GroupTabs(tabs: List<GroupTab>, activeKey: String?, onSelect: (String) -> Unit) {
    val index = tabs.indexOfFirst { it.key == activeKey }.coerceAtLeast(0)
    androidx.compose.material3.SecondaryScrollableTabRow(selectedTabIndex = index, edgePadding = 12.dp) {
        tabs.forEachIndexed { i, tab ->
            Tab(
                selected = i == index,
                onClick = { onSelect(tab.key) },
                text = { Text("${tab.label} (${tab.count})") },
            )
        }
    }
}

/** A tri-state filter row: tap cycles the leading glyph neutral → include (check) → exclude (cross). */
@Composable
private fun TriRow(label: String, state: Tri?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriBox(state)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The tri-state glyph: outlined box (neutral), filled check (include), filled cross (exclude). */
@Composable
private fun TriBox(state: Tri?) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val bg = when (state) {
        Tri.INCLUDE -> MaterialTheme.colorScheme.primary
        Tri.EXCLUDE -> MaterialTheme.colorScheme.error
        null -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderColor = when (state) {
        Tri.INCLUDE -> MaterialTheme.colorScheme.primary
        Tri.EXCLUDE -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(20.dp).background(bg, shape).border(1.5.dp, borderColor, shape),
        Alignment.Center,
    ) {
        when (state) {
            Tri.INCLUDE -> Icon(Icons.Filled.Check, contentDescription = "Include", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            Tri.EXCLUDE -> Icon(Icons.Filled.Close, contentDescription = "Exclude", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
            null -> {}
        }
    }
}

/** Legend explaining the tri-state glyphs shown at the bottom of the Filter tab. */
@Composable
private fun FilterLegend() {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            TriBox(Tri.INCLUDE)
            Text("Include", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            TriBox(Tri.EXCLUDE)
            Text("Exclude", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A list row with a leading checkmark when selected (Mihon-style option list). */
@Composable
private fun CheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(24.dp), Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Bulk category-assign dialog: pick categories to add to the selected series. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesDialog(categories: List<app.kodex.client.network.CategoryDto>, onDismiss: () -> Unit, onApply: (List<String>) -> Unit) {
    val checked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to categories") },
        text = {
            if (categories.isEmpty()) {
                Text("No categories yet. Create some on the server first.")
            } else Column {
                categories.forEach { c ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = c.id in checked, onClick = { if (c.id in checked) checked.remove(c.id) else checked.add(c.id) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.id in checked, onCheckedChange = { if (it) checked.add(c.id) else checked.remove(c.id) })
                        Text(c.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(checked.toList()) }, enabled = checked.isNotEmpty()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

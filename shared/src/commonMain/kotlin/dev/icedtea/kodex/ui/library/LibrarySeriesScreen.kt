package dev.icedtea.kodex.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.network.CategoryDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.SeriesDto
import dev.icedtea.kodex.network.SeriesGroupCount
import dev.icedtea.kodex.network.ServerEvent
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.KodexBottomSheet
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.OnServerEvent
import dev.icedtea.kodex.ui.SelectionActionBar
import dev.icedtea.kodex.ui.catalog.rememberSourceNames
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.SelectionTopBar
import dev.icedtea.kodex.ui.TooltipIconButton
import dev.icedtea.kodex.ui.catalog.SeriesGrid
import dev.icedtea.kodex.ui.catalog.SeriesListView
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.rememberSelection
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Mihon-style sort keys — one row each, with a direction toggled by tapping the selected key. */
/**
 * The sortable columns, in the web's order and wording — the choice is stored server-side under
 * `ui.librarySort` and shared with it, so a library must not come up ordered differently here.
 */
private enum class SortKey(val label: String, val field: String, val defaultAsc: Boolean) {
    TITLE("Title", "title", true),
    NAME("Name", "name", true),
    DATE_ADDED("Recently added", "createdDate", false),
    DATE_UPDATED("Recently updated", "lastModifiedDate", false),
    TOTAL_CHAPTERS("Total chapters", "totalChapters", false),
    UNREAD("Unread count", "unreadCount", false),
    LAST_READ("Last read", "lastRead", false),
}

/** What a library sorts by until the user picks something — the web's `DEFAULT_SERIES_SORT`. */
private val DEFAULT_SORT = SortKey.NAME

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

// The per-user server settings that carry how a library is being viewed. Each holds one flat
// { scope -> value } map, written by the web UI under the same keys (its useLibrarySort /
// useLibraryGroup) — so a library grouped by source in the browser opens grouped by source here.
private const val LIBRARY_SORT_KEY = "ui.librarySort"
private const val LIBRARY_GROUP_KEY = "ui.libraryGroup"
private const val LIBRARY_GROUP_TAB_KEY = "ui.libraryGroupTab"

/** Reads one of those maps, skipping entries whose value isn't a plain string. */
private fun JsonObject.stringMap(key: String): Map<String, String> =
    (this[key] as? JsonObject)
        ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
        ?.toMap()
        .orEmpty()

private fun Map<String, String>.asSetting() = JsonObject(mapValues { JsonPrimitive(it.value) })

/** Group tabs are stored per (library, dimension), since switching dimension swaps the whole strip. */
private fun groupTabScope(libraryId: String, groupBy: String) = "$libraryId.$groupBy"

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

    // The dimensions series can be split into, matching the web: status always, source only where
    // series have one. Category is deliberately absent — it is the chip filter below, not a grouping.
    val groupOptions = buildList {
        add("none" to "None")
        add("status" to "Status")
        if (library.isWeb) add("source" to "Source")
    }
    // Retained: opening a series unmounts this screen, so without this the sort, filters, grouping and
    // the loaded group counts would all be rebuilt on the way back. The counts especially: they arrive
    // asynchronously, and a screen that comes back with no groups yet cannot restore its group tab.
    val allowedGroups = groupOptions.map { it.first }.toSet()
    val st = retain("library:${library.id}") {
        LibraryScreenState(library.id, appSettings, allowedGroups)
    }
    var sortKey by st.sortKey
    var sortAsc by st.sortAsc
    var downloadedTri by st.downloadedTri
    val readingTri = st.readingTri
    var statusTri by st.statusTri // COMPLETED series status
    var reloadTick by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf(0) }
    // Seeded from the per-library store rather than defaulting to "none", so reopening a library comes
    // back grouped the way it was left. Coerced against the options first, so a value stored before
    // this list changed (or by a library of another type) falls back to "none" instead of grouping by
    // a dimension that is no longer offered.
    var groupBy by st.groupBy
    var selectedGroup by st.selectedGroup
    // The category chip filter (WEB libraries): narrows the whole view, and combines with grouping.
    var categoryId by st.categoryId
    // Source display names for the cover labels; cached per server, so this is a map lookup after
    // the first grid on the first screen that asks for it.
    val sourceNames = rememberSourceNames(session, api)
    var searchOpen by st.searchOpen
    var searchQuery by st.searchQuery
    // Debounced: the field drives the query, but a request per keystroke would be a request per
    // keystroke. Blank searches skip the wait so clearing the box restores the list at once.
    var searchTerm by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) searchTerm = "" else {
            kotlinx.coroutines.delay(300)
            searchTerm = searchQuery
        }
    }
    // Group counts can't be scoped to a search (the server's /series/groups takes no query), so tabs
    // would disagree with the results. Searching therefore shows one flat list, which is what you
    // want from a search anyway.
    val searching = searchTerm.isNotBlank()
    var groups by st.groups
    var groupNames by st.groupNames // key → friendly label (source)
    var allSeriesIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var categories by remember { mutableStateOf<List<dev.icedtea.kodex.network.CategoryDto>>(emptyList()) }
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

    // Server-backed view choices: the sort, the grouping dimension and the open group tab all live in
    // per-user settings shared with the web UI, so a library is presented the same way wherever it is
    // opened. The local store still holds them too — it is what the screen shows before the fetch lands
    // (and all it has while offline), so every save writes both.
    var sortMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var groupMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var groupTabMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(server?.id, library.id) {
        val s = server ?: return@LaunchedEffect
        val settings = runCatching { api.userSettings(s.baseUrl, s.apiKey) }.getOrNull() ?: return@LaunchedEffect
        sortMap = settings.stringMap(LIBRARY_SORT_KEY)
        groupMap = settings.stringMap(LIBRARY_GROUP_KEY)
        groupTabMap = settings.stringMap(LIBRARY_GROUP_TAB_KEY)
        sortMap[library.id]?.let { expr ->
            val parts = expr.split(",")
            SortKey.entries.find { it.field == parts.getOrNull(0) }?.let { key ->
                sortKey = key
                sortAsc = parts.getOrNull(1) != "desc"
            }
        }
        // A dimension this library doesn't offer (stored by a library of another type, or by a newer
        // web build) is ignored rather than grouping by something that has no tabs here.
        groupMap[library.id]?.takeIf { it in allowedGroups }?.let { g ->
            groupBy = g
            appSettings.setLibraryGroupBy(library.id, g)
            groupTabMap[groupTabScope(library.id, g)]?.let { tab ->
                selectedGroup = tab
                appSettings.setLibraryGroupTab(library.id, g, tab)
            }
        }
    }

    /** Writes one entry of a shared settings map back, leaving every other library's entry untouched. */
    fun persistSetting(key: String, map: Map<String, String>) {
        val s = server ?: return
        scope.launch { runCatching { api.saveUserSetting(s.baseUrl, s.apiKey, key, map.asSetting()) } }
    }

    fun persistSort() {
        sortMap = sortMap + (library.id to "${sortKey.field},${if (sortAsc) "asc" else "desc"}")
        persistSetting(LIBRARY_SORT_KEY, sortMap)
    }

    fun persistGroupBy(value: String) {
        appSettings.setLibraryGroupBy(library.id, value)
        groupMap = groupMap + (library.id to value)
        persistSetting(LIBRARY_GROUP_KEY, groupMap)
    }

    fun persistGroupTab(dimension: String, key: String) {
        appSettings.setLibraryGroupTab(library.id, dimension, key)
        groupTabMap = groupTabMap + (groupTabScope(library.id, dimension) to key)
        persistSetting(LIBRARY_GROUP_TAB_KEY, groupTabMap)
    }

    // Live per-group counts for the current grouping dimension.
    LaunchedEffect(library.id, server?.id, reloadTick, groupBy, categoryId) {
        val s = server ?: return@LaunchedEffect
        val counts = if (groupBy == "none") emptyList()
        else runCatching { api.seriesGroups(s.baseUrl, s.apiKey, groupBy, library.id, categoryId) }
            .getOrDefault(emptyList()).filter { it.count > 0 }
        val names = if (groupBy == "source") {
            runCatching { api.contentSources(s.baseUrl, s.apiKey).associate { it.id to it.displayName } }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        // Published together, names first. Tabs are sorted by their label, so a list built while the
        // names were still in flight would be ordered by raw source id — the pager would keep its index
        // through the reorder and silently land on a different group, then persist that as your choice.
        groupNames = names
        groups = counts
    }

    // Categories available for the bulk-assign action (WEB libraries).
    LaunchedEffect(library.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        categories = if (library.isWeb) runCatching { api.categories(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()) else emptyList()
    }

    // System back peels off one layer at a time: selection, then the search, then the screen.
    dev.icedtea.kodex.platform.AppBackHandler(enabled = selection.active || searchOpen) {
        if (selection.active) selection.clear() else { searchOpen = false; searchQuery = "" }
    }

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
        val label = if (groupBy == "source") groupNames[g.key] ?: g.key
        else g.key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
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
                    title = {
                        if (searchOpen) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                placeholder = { Text("Search ${library.name}") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        } else {
                            Text(library.name, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        // While searching, back closes the search rather than leaving the library —
                        // the same step the system back button takes.
                        IconButton(onClick = { if (searchOpen) { searchOpen = false; searchQuery = "" } else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (searchOpen) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                            IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                            IconButton(onClick = { sheetTab = 0; sheetOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "View options") }
                        }
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
            // Category chips (WEB): pick *which* category, while grouping subdivides within it.
            if (library.isWeb && categories.isNotEmpty()) {
                CategoryChips(
                    categories = categories,
                    selected = categoryId,
                    onSelect = { categoryId = it },
                )
            }
            val filters = SeriesFilterArgs(
                libraryId = library.id, sortExpr = sortExpr,
                readingInclude = readingInclude, readingExclude = readingExclude,
                statusInclude = statusTri == Tri.INCLUDE, statusExclude = statusTri == Tri.EXCLUDE,
                downloaded = downloadedTri?.let { it == Tri.INCLUDE },
                groupBy = if (searching) "none" else groupBy,
                categoryId = categoryId,
                search = searchTerm.takeIf { searching },
            )
            if (!searching && groupBy != "none" && tabGroups.isNotEmpty()) {
                // Grouped: a tab per group, and the content is a pager so groups can be swiped between.
                val initialPage = tabGroups.indexOfFirst { it.key == selectedGroup }.coerceAtLeast(0)
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage) { tabGroups.size }
                // Should the tabs change under the pager (a group emptying out, a refresh), follow the
                // chosen group by key rather than letting the index decide which group that now is.
                val activeIndex = tabGroups.indexOfFirst { it.key == selectedGroup }.coerceAtLeast(0)
                LaunchedEffect(activeIndex) {
                    if (activeIndex != pagerState.currentPage) pagerState.scrollToPage(activeIndex)
                }
                // Keep the persisted/active group in sync with the page the user swiped or tapped to.
                LaunchedEffect(pagerState.settledPage, tabGroups) {
                    val key = tabGroups.getOrNull(pagerState.settledPage)?.key ?: return@LaunchedEffect
                    if (key == selectedGroup) return@LaunchedEffect
                    selectedGroup = key
                    persistGroupTab(groupBy, key)
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
                        sourceNames = sourceNames,
                    )
                }
            } else {
                LibrarySeriesResults(
                    server, api, filters, groupKey = null, reloadTick = reloadTick,
                    gridView = gridView, displayBy = displayBy, selection = selection, onOpenSeries = onOpenSeries,
                    onIdsLoaded = { allSeriesIds = it },
                    sourceNames = sourceNames,
                )
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        KodexBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
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
                    0 -> {
                        SheetLabel("Sort by")
                        SortKey.entries.forEach { key ->
                            val selected = key == sortKey
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth()
                                    // Tapping the active column flips the direction; tapping another
                                    // switches to it in whichever direction reads naturally for it.
                                    .selectable(selected = selected, onClick = {
                                        if (selected) sortAsc = !sortAsc else { sortKey = key; sortAsc = key.defaultAsc }
                                        persistSort()
                                    })
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Only the active column carries an icon, and it is the direction —
                                // there is no separate tick, matching the web's menu.
                                androidx.compose.foundation.layout.Box(Modifier.size(24.dp), Alignment.Center) {
                                    if (selected) {
                                        Icon(
                                            if (sortAsc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                            contentDescription = if (sortAsc) "Ascending" else "Descending",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    key.label, Modifier.padding(start = 12.dp).weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
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
                        groupOptions.forEach { (value, label) ->
                            CheckRow(label, groupBy == value) {
                                groupBy = value
                                persistGroupBy(value)
                                // A new dimension has different tabs, so fall back to whichever one
                                // was last open under it (null → the first).
                                selectedGroup = groupTabMap[groupTabScope(library.id, value)]
                                    ?: appSettings.libraryGroupTab(library.id, value)
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


/** The category filter as a scrolling chip row: "All" plus one chip per category. */
@Composable
private fun CategoryChips(
    categories: List<CategoryDto>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        categories.forEach { c ->
            FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(if (selected == c.id) null else c.id) },
                label = { Text(c.name) },
            )
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
    val categoryId: String?,
    val search: String?,
)

/**
 * One group's series (or the whole library when [groupKey] is null): loads with the active filters and
 * renders the grid/list. Extracted so it can back both the flat list and each swipeable pager page.
 */
@Composable
private fun LibrarySeriesResults(
    server: dev.icedtea.kodex.data.model.ServerConnection?,
    api: KodexApi,
    filters: SeriesFilterArgs,
    groupKey: String?,
    reloadTick: Int,
    gridView: Boolean,
    displayBy: String,
    selection: dev.icedtea.kodex.ui.SelectionState<String>,
    onOpenSeries: (SeriesDto) -> Unit,
    onIdsLoaded: (List<String>) -> Unit,
    sourceNames: Map<String, String>,
) {
    LoadedContent(
        // One holder per group tab, so swiping between them keeps each tab's loaded page.
        retainKey = "series:${groupKey.orEmpty()}",
        key = listOf(filters.libraryId, server?.id, filters.sortExpr, filters.readingInclude, filters.readingExclude, filters.downloaded, filters.statusInclude, filters.statusExclude, filters.groupBy, filters.categoryId, filters.search, groupKey, reloadTick),
        load = {
            val s = server!!
            val statuses = buildList {
                if (filters.groupBy == "status") groupKey?.let { add(it) }
                if (filters.statusInclude) add("COMPLETED")
            }
            api.querySeries(
                s.baseUrl, s.apiKey,
                libraryId = filters.libraryId,
                search = filters.search,
                sort = filters.sortExpr,
                readingStatuses = filters.readingInclude,
                readingStatusExcludes = filters.readingExclude,
                statuses = statuses,
                statusExcludes = if (filters.statusExclude) listOf("COMPLETED") else emptyList(),
                downloaded = filters.downloaded,
                sources = if (filters.groupBy == "source") groupKey?.let { listOf(it) } ?: emptyList() else emptyList(),
                categoryIds = listOfNotNull(filters.categoryId),
            )
        },
    ) { series ->
        onIdsLoaded(series.map { it.id })
        val titleOf: (SeriesDto) -> String = { if (displayBy == "name") it.name.ifBlank { it.title } else it.title.ifBlank { it.name } }
        // Retained alongside the loaded page, so opening a series and coming back lands where you were.
        val scroll = retain("scroll:${groupKey.orEmpty()}") { GridScrollState() }
        when {
            series.isEmpty() -> EmptyMessage(
                if (filters.search != null) "No series match \u201c${filters.search}\u201d." else "No series match this filter.",
            )
            server != null && gridView ->
                SeriesGrid(
                    server.baseUrl, server.apiKey, series, onOpenSeries,
                    selection = selection, titleOf = titleOf, state = scroll.grid, sourceNames = sourceNames,
                )
            server != null ->
                SeriesListView(server.baseUrl, server.apiKey, series, onOpenSeries, selection = selection, titleOf = titleOf, state = scroll.list)
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
private fun CategoriesDialog(categories: List<dev.icedtea.kodex.network.CategoryDto>, onDismiss: () -> Unit, onApply: (List<String>) -> Unit) {
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

/** Scroll offsets for one group tab; grid and list keep their own, since the toggle swaps between them. */
private class GridScrollState {
    val grid = androidx.compose.foundation.lazy.grid.LazyGridState()
    val list = androidx.compose.foundation.lazy.LazyListState()
}

/**
 * Everything about how this library is being viewed that must outlive opening a series on top of it:
 * sort, filters, the grouping dimension and which group tab, the category chip, and the loaded group
 * counts. [GridScrollState] holds where each tab was scrolled to.
 */
private class LibraryScreenState(
    libraryId: String,
    appSettings: AppSettings,
    /** The grouping dimensions this library actually offers; anything else stored reads as "none". */
    allowedGroups: Set<String>,
) {
    val sortKey = mutableStateOf(DEFAULT_SORT)
    val sortAsc = mutableStateOf(DEFAULT_SORT.defaultAsc)
    val downloadedTri = mutableStateOf<Tri?>(null)
    val readingTri = mutableStateMapOf<String, Tri>()
    val statusTri = mutableStateOf<Tri?>(null)
    val groupBy = mutableStateOf(
        appSettings.libraryGroupBy(libraryId).takeIf { it in allowedGroups } ?: "none",
    )
    val selectedGroup = mutableStateOf(appSettings.libraryGroupTab(libraryId, groupBy.value))
    val categoryId = mutableStateOf<String?>(null)
    val searchOpen = mutableStateOf(false)
    val searchQuery = mutableStateOf("")
    val groups = mutableStateOf<List<SeriesGroupCount>>(emptyList())
    val groupNames = mutableStateOf<Map<String, String>>(emptyMap())
}

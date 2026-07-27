package app.kodex.client.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
    var groupBy by remember { mutableStateOf("none") } // none | status | source | category
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<SeriesGroupCount>>(emptyList()) }
    var groupNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // key → friendly label (source/category)
    var allSeriesIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var categories by remember { mutableStateOf<List<app.kodex.client.network.CategoryDto>>(emptyList()) }
    var categoriesDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val selection = rememberSelection<String>()

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

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionTopBar(
                    count = selection.count,
                    isWeb = library.isWeb,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(allSeriesIds) },
                    onMarkRead = { bulkMark(true) },
                    onMarkUnread = { bulkMark(false) },
                    onUpdate = { bulkForEach("Updating…") { b, k, id -> api.refreshSeriesChapters(b, k, id) } },
                    onDownload = { bulkForEach("Downloading…") { b, k, id -> api.downloadWebSeries(b, k, library.id, id, null) } },
                    onCategories = { categoriesDialog = true },
                    onRemove = { confirmRemove = true },
                )
            } else {
                TopAppBar(
                    title = { Text(library.name, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                        IconButton(onClick = { sheetOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "View options") }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = listOf(library.id, server?.id, sortExpr, readingInclude, readingExclude, downloadedTri, statusTri, groupBy, selectedGroup, reloadTick),
                load = {
                    val s = server!!
                    val statuses = buildList {
                        if (groupBy == "status") selectedGroup?.let { add(it) }
                        if (statusTri == Tri.INCLUDE) add("COMPLETED")
                    }
                    api.querySeries(
                        s.baseUrl, s.apiKey,
                        libraryId = library.id,
                        sort = sortExpr,
                        readingStatuses = readingInclude,
                        readingStatusExcludes = readingExclude,
                        statuses = statuses,
                        statusExcludes = if (statusTri == Tri.EXCLUDE) listOf("COMPLETED") else emptyList(),
                        downloaded = downloadedTri?.let { it == Tri.INCLUDE },
                        sources = if (groupBy == "source") selectedGroup?.let { listOf(it) } ?: emptyList() else emptyList(),
                        categoryIds = if (groupBy == "category") selectedGroup?.let { listOf(it) } ?: emptyList() else emptyList(),
                    )
                },
            ) { series ->
                val s = server
                allSeriesIds = series.map { it.id }
                val titleOf: (SeriesDto) -> String = { if (displayBy == "name") it.name.ifBlank { it.title } else it.title.ifBlank { it.name } }
                when {
                    series.isEmpty() -> EmptyMessage("No series match this filter.")
                    s != null && gridView -> SeriesGrid(s.baseUrl, s.apiKey, series, onOpenSeries, selection = selection, titleOf = titleOf)
                    s != null -> SeriesListView(s.baseUrl, s.apiKey, series, onOpenSeries, selection = selection, titleOf = titleOf)
                }
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        var sheetTab by remember { mutableStateOf(0) }
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            val tabs = listOf("Sort", "Filter", "Group", "Display")
            TabRow(selectedTabIndex = sheetTab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = sheetTab == i, onClick = { sheetTab = i }, text = { Text(t) })
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 440.dp)
                    .verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
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
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            val opts = buildList {
                                add("none" to "None"); add("status" to "Status"); add("source" to "Source")
                                if (library.isWeb) add("category" to "Category")
                            }
                            opts.forEachIndexed { i, (value, label) ->
                                SegmentedButton(
                                    selected = groupBy == value,
                                    onClick = { groupBy = value; selectedGroup = null },
                                    shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                                ) { Text(label) }
                            }
                        }
                        if (groups.isNotEmpty()) {
                            SheetLabel("Show group")
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null }, label = { Text("All") })
                                groups.forEach { g ->
                                    val lbl = when (groupBy) {
                                        "source", "category" -> groupNames[g.key] ?: g.key
                                        else -> g.key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                                    }
                                    FilterChip(
                                        selected = selectedGroup == g.key,
                                        onClick = { selectedGroup = if (selectedGroup == g.key) null else g.key },
                                        label = { Text("$lbl (${g.count})") },
                                    )
                                }
                            }
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

/** Contextual top bar shown while series are multi-selected. Matches the Mihon action set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    isWeb: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onUpdate: () -> Unit,
    onDownload: () -> Unit,
    onCategories: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = { Text("$count selected", fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") } },
        actions = {
            IconButton(onClick = onMarkRead) { Icon(Icons.Filled.Check, contentDescription = "Mark read") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; onSelectAll() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Mark as unread") }, onClick = { menu = false; onMarkUnread() })
                if (isWeb) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Update") }, onClick = { menu = false; onUpdate() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Add to categories") }, onClick = { menu = false; onCategories() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() })
                }
            }
        },
    )
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

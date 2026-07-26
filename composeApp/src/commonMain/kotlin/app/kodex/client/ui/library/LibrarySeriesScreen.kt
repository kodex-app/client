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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
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

private enum class SeriesSort(val label: String, val expr: String) {
    TITLE_ASC("Title (A–Z)", "title,asc"),
    TITLE_DESC("Title (Z–A)", "title,desc"),
    NAME_ASC("Name (A–Z)", "name,asc"),
    NAME_DESC("Name (Z–A)", "name,desc"),
    RECENTLY_ADDED("Recently added", "createdDate,desc"),
    RECENTLY_UPDATED("Recently updated", "lastModifiedDate,desc"),
    MOST_CHAPTERS("Total chapters", "totalChapters,desc"),
    MOST_UNREAD("Unread count", "unreadCount,desc"),
    LAST_READ("Last read", "lastRead,desc"),
}

// Reading-status filter (server `readingStatus`); null = all.
private data class ReadFilter(val label: String, val value: String?)

private val READ_FILTERS = listOf(
    ReadFilter("All", null),
    ReadFilter("Unread", "NOT_STARTED"),
    ReadFilter("In progress", "IN_PROGRESS"),
    ReadFilter("Read", "COMPLETED"),
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
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var sort by remember { mutableStateOf(SeriesSort.TITLE_ASC) }
    var readFilter by remember { mutableStateOf(READ_FILTERS.first()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var groupBy by remember { mutableStateOf("status") } // none | status | source | category
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<SeriesGroupCount>>(emptyList()) }
    var groupNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // key → friendly label (source/category)
    val selection = rememberSelection<String>()

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

    // System back exits selection mode first (before leaving the screen).
    app.kodex.client.platform.AppBackHandler(enabled = selection.active) { selection.clear() }

    // A finished scan of THIS library means the series list changed → reload.
    OnServerEvent(ServerEvent.LIBRARY_SCAN_COMPLETED) { e ->
        if (e.data["libraryId"]?.jsonPrimitive?.contentOrNull == library.id) reloadTick++
    }

    fun bulkMark(read: Boolean) {
        val s = server ?: return
        val ids = selection.selected.toList()
        if (ids.isEmpty()) return
        scope.launch {
            runCatching { ids.forEach { api.markSeriesRead(s.baseUrl, s.apiKey, it, read) } }.fold(
                onSuccess = { snackbar?.show(if (read) "Marked read" else "Marked unread"); selection.clear(); reloadTick++ },
                onFailure = { snackbar?.show("Action failed.") },
            )
        }
    }

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
                    onClose = { selection.clear() },
                    onMarkRead = { bulkMark(true) },
                    onMarkUnread = { bulkMark(false) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (groups.isNotEmpty()) {
                GroupChips(
                    groupBy = groupBy,
                    groups = groups,
                    groupNames = groupNames,
                    selectedGroup = selectedGroup,
                    onGroup = { selectedGroup = it },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LoadedContent(
                    key = listOf(library.id, server?.id, sort, readFilter.value, groupBy, selectedGroup, reloadTick),
                    load = {
                        val s = server!!
                        api.querySeries(
                            s.baseUrl, s.apiKey,
                            libraryId = library.id,
                            sort = sort.expr,
                            readingStatuses = readFilter.value?.let { listOf(it) } ?: emptyList(),
                            statuses = if (groupBy == "status") selectedGroup?.let { listOf(it) } ?: emptyList() else emptyList(),
                            sources = if (groupBy == "source") selectedGroup?.let { listOf(it) } ?: emptyList() else emptyList(),
                            categoryIds = if (groupBy == "category") selectedGroup?.let { listOf(it) } ?: emptyList() else emptyList(),
                        )
                    },
                ) { series ->
                    val s = server
                    when {
                        series.isEmpty() -> EmptyMessage("No series match this filter.")
                        s != null && gridView -> SeriesGrid(s.baseUrl, s.apiKey, series, onOpenSeries, selection = selection)
                        s != null -> SeriesListView(s.baseUrl, s.apiKey, series, onOpenSeries, selection = selection)
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SheetLabel("View")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = gridView,
                        onClick = { appSettings.setLibraryGridView(true) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Grid") }
                    SegmentedButton(
                        selected = !gridView,
                        onClick = { appSettings.setLibraryGridView(false) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("List") }
                }

                SheetLabel("Sort by")
                SeriesSort.entries.forEach { option ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = sort == option, onClick = { sort = option })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sort == option, onClick = { sort = option })
                        Text(option.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                SheetLabel("Show")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    READ_FILTERS.forEach { f ->
                        FilterChip(
                            selected = readFilter == f,
                            onClick = { readFilter = f },
                            label = { Text(f.label) },
                        )
                    }
                }

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
            }
        }
    }
}

/** Contextual top bar shown while series are multi-selected in the library grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onMarkRead: () -> Unit, onMarkUnread: () -> Unit) {
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
                androidx.compose.material3.DropdownMenuItem(text = { Text("Mark unread") }, onClick = { menu = false; onMarkUnread() })
            }
        },
    )
}

/** Horizontally-scrolling group chips for the selected dimension (status / source / category). */
@Composable
private fun GroupChips(
    groupBy: String,
    groups: List<SeriesGroupCount>,
    groupNames: Map<String, String>,
    selectedGroup: String?,
    onGroup: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selectedGroup == null, onClick = { onGroup(null) }, label = { Text("All") })
        }
        items(groups, key = { it.key }) { g ->
            val label = when (groupBy) {
                "source", "category" -> groupNames[g.key] ?: g.key
                else -> g.key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            FilterChip(
                selected = selectedGroup == g.key,
                onClick = { onGroup(if (selectedGroup == g.key) null else g.key) },
                label = { Text("$label (${g.count})") },
            )
        }
    }
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

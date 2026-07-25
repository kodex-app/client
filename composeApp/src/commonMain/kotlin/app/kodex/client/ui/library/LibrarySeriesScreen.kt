package app.kodex.client.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.network.ServerEvent
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.OnServerEvent
import app.kodex.client.ui.catalog.SeriesGrid
import app.kodex.client.ui.catalog.SeriesListView
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private enum class SeriesSort(val label: String, val expr: String) {
    TITLE_ASC("Title (A–Z)", "title,asc"),
    TITLE_DESC("Title (Z–A)", "title,desc"),
    RECENTLY_ADDED("Recently added", "createdDate,desc"),
    RECENTLY_UPDATED("Recently updated", "lastModifiedDate,desc"),
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

    // A finished scan of THIS library means the series list changed → reload.
    OnServerEvent(ServerEvent.LIBRARY_SCAN_COMPLETED) { e ->
        if (e.data["libraryId"]?.jsonPrimitive?.contentOrNull == library.id) reloadTick++
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
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = listOf(library.id, server?.id, sort, readFilter.value, reloadTick),
                load = {
                    val s = server!!
                    api.seriesInLibrary(s.baseUrl, s.apiKey, library.id, sort.expr, readFilter.value)
                },
            ) { series ->
                val s = server
                when {
                    series.isEmpty() -> EmptyMessage(
                        if (readFilter.value != null) "No ${readFilter.label.lowercase()} series." else "No series in this library yet.",
                    )
                    s != null && gridView -> SeriesGrid(s.baseUrl, s.apiKey, series, onOpenSeries)
                    s != null -> SeriesListView(s.baseUrl, s.apiKey, series, onOpenSeries)
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
            }
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

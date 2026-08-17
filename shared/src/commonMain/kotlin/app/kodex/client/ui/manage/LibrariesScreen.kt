package app.kodex.client.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.LibraryNavPrefs
import app.kodex.client.data.loadLibraryNavPrefs
import app.kodex.client.data.orderedBy
import app.kodex.client.data.saveLibraryNavPrefs
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.ui.catalog.ColorBadge
import app.kodex.client.ui.ErrorState
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Manage libraries, mirroring the web UI's Libraries page: reorder, hide from the Libraries tab, hide
 * from Home, refresh / deep-scan / analyze, edit, delete, and create.
 *
 * Order and the two visibility flags are per-user *view* preferences stored under `nav.libraries`
 * (see [LibraryNavPrefs]) — the same setting the web writes, so the two clients agree and neither
 * mutates the shared Library records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<LibraryDto>?>(null) }
    var prefs by remember { mutableStateOf(LibraryNavPrefs()) }
    var counts by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var reload by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<LibraryDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LibraryDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.libraries(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { libraries = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
        prefs = loadLibraryNavPrefs(api, s.baseUrl, s.apiKey)
    }

    // Series counts fill in per library once the list is known; a failure just leaves that row's
    // subtitle without a count rather than blocking the screen.
    LaunchedEffect(libraries, server?.id) {
        val s = server ?: return@LaunchedEffect
        val list = libraries ?: return@LaunchedEffect
        counts = list.associate { lib ->
            lib.id to (runCatching { api.seriesCountInLibrary(s.baseUrl, s.apiKey, lib.id) }.getOrDefault(-1L))
        }
    }

    val ordered = remember(libraries, prefs) { (libraries ?: emptyList()).orderedBy(prefs) }

    /** Persist a preference edit optimistically — the row reflects it immediately. */
    fun updatePrefs(next: LibraryNavPrefs) {
        prefs = next
        val s = server ?: return
        scope.launch {
            runCatching { saveLibraryNavPrefs(api, s.baseUrl, s.apiKey, next) }
                .onFailure { snackbar?.show("Couldn't save library preferences.") }
        }
    }

    fun move(lib: LibraryDto, delta: Int) {
        val ids = ordered.map { it.id }.toMutableList()
        val i = ids.indexOf(lib.id)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids[i] = ids[j].also { ids[j] = ids[i] }
        updatePrefs(prefs.withOrder(ids))
    }

    fun act(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show("Action failed (need manage permission).") },
            )
        }
    }

    if (creating || editTarget != null) {
        LibraryFormScreen(
            session = session,
            api = api,
            existing = editTarget,
            onBack = { creating = false; editTarget = null },
            onSaved = { creating = false; editTarget = null; reload++; snackbar?.show("Library saved") },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Libraries", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New library") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (libraries) {
                null -> if (loadError != null) ErrorState(loadError!!) { reload++ }
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> if (ordered.isEmpty()) {
                    Text("No libraries. Tap + to add one.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(ordered, key = { it.id }) { lib ->
                        val index = ordered.indexOfFirst { it.id == lib.id }
                        LibraryRow(
                            lib = lib,
                            seriesCount = counts[lib.id],
                            hidden = prefs.isHidden(lib.id),
                            hiddenFromHome = prefs.isHiddenFromHome(lib.id),
                            canMoveUp = index > 0,
                            canMoveDown = index < ordered.lastIndex,
                            onMoveUp = { move(lib, -1) },
                            onMoveDown = { move(lib, 1) },
                            onToggleHidden = { updatePrefs(prefs.withHidden(lib.id, !prefs.isHidden(lib.id))) },
                            onToggleHiddenFromHome = { updatePrefs(prefs.withHiddenFromHome(lib.id, !prefs.isHiddenFromHome(lib.id))) },
                            onRefresh = { deep -> act(if (deep) "Deep scan queued" else "Refresh queued") { val s = server!!; api.refreshLibrary(s.baseUrl, s.apiKey, lib.id, deep) } },
                            onAnalyze = { act("Analyze queued") { val s = server!!; api.analyzeLibrary(s.baseUrl, s.apiKey, lib.id) } },
                            onEdit = { editTarget = lib },
                            onDelete = { confirmDelete = lib },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    confirmDelete?.let { target -> DeleteLibraryDialog(target, onDismiss = { confirmDelete = null }) { deleteFiles ->
        confirmDelete = null
        act("Library deleted") { val s = server!!; api.deleteLibrary(s.baseUrl, s.apiKey, target.id, deleteFiles) }
    } }
}

/** Confirm + the optional "also delete files on disk", matching the web's delete dialog. */
@Composable
private fun DeleteLibraryDialog(target: LibraryDto, onDismiss: () -> Unit, onConfirm: (deleteFiles: Boolean) -> Unit) {
    var deleteFiles by remember(target.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete library?") },
        text = {
            Column {
                Text("“${target.name}” will be removed from Kodex.")
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (target.isWeb) "Also delete downloaded files" else "Also delete files on disk",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LibraryRow(
    lib: LibraryDto,
    seriesCount: Long?,
    hidden: Boolean,
    hiddenFromHome: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleHiddenFromHome: () -> Unit,
    onRefresh: (Boolean) -> Unit,
    onAnalyze: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // A hidden library still lists here (this is where you un-hide it) but reads back visually.
    val nameColor = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(lib.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = nameColor)
            Spacer(Modifier.size(2.dp))
            Text(
                librarySubtitle(lib, seriesCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorBadge(lib.type)
                lib.mediaKind?.takeIf { it.isNotBlank() }?.let { ColorBadge(it) }
                if (hidden) ColorBadge("Hidden")
                if (hiddenFromHome) ColorBadge("Not on Home")
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Move up") },
                    enabled = canMoveUp,
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                    onClick = { menu = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    enabled = canMoveDown,
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                    onClick = { menu = false; onMoveDown() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (hidden) "Show in Libraries" else "Hide from Libraries") },
                    onClick = { menu = false; onToggleHidden() },
                )
                DropdownMenuItem(
                    text = { Text(if (hiddenFromHome) "Show on Home" else "Hide from Home") },
                    onClick = { menu = false; onToggleHiddenFromHome() },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; onRefresh(false) })
                DropdownMenuItem(text = { Text("Deep scan") }, onClick = { menu = false; onRefresh(true) })
                DropdownMenuItem(text = { Text("Analyze") }, onClick = { menu = false; onAnalyze() })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

/**
 * Row subtitle: how much the library holds, then where it lives. The count is null while it's still
 * loading and -1 when the request failed, in which case it's simply left out.
 */
private fun librarySubtitle(lib: LibraryDto, seriesCount: Long?): String {
    val count = when {
        seriesCount == null -> null
        seriesCount < 0 -> null
        seriesCount == 1L -> "1 series"
        else -> "$seriesCount series"
    }
    val where = lib.root?.takeIf { it.isNotBlank() } ?: if (lib.isWeb) "Web library" else null
    return listOfNotNull(count, where).joinToString(" · ").ifBlank { "—" }
}

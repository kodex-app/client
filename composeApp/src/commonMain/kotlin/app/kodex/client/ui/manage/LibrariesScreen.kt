package app.kodex.client.ui.manage

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Manage libraries: list, refresh / deep-scan / analyze, edit, delete, and create new ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<LibraryDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editTarget by remember { mutableStateOf<LibraryDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LibraryDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        libraries = runCatching { api.libraries(s.baseUrl, s.apiKey) }.getOrNull()
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
            when (val list = libraries) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> if (list.isEmpty()) {
                    Text("No libraries. Tap + to add one.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { lib ->
                        LibraryRow(
                            lib = lib,
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

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete library?") },
            text = { Text("“${target.name}” will be removed from Kodex. Files on disk are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act("Library deleted") { val s = server!!; api.deleteLibrary(s.baseUrl, s.apiKey, target.id, deleteFiles = false) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LibraryRow(
    lib: LibraryDto,
    onRefresh: (Boolean) -> Unit,
    onAnalyze: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(lib.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.kodex.client.ui.catalog.ColorBadge(lib.type)
                lib.mediaKind?.takeIf { it.isNotBlank() }?.let { app.kodex.client.ui.catalog.ColorBadge(it) }
                lib.root?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

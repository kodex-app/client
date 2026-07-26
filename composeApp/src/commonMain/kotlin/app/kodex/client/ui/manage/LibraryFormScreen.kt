package app.kodex.client.ui.manage

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.CreateLibraryRequest
import app.kodex.client.network.DirectoryListing
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.UpdateLibraryRequest
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Create a new library (LOCAL folder scan or WEB content source) or edit an existing one's name/root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFormScreen(
    session: SessionManager,
    api: KodexApi,
    existing: LibraryDto?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "LOCAL") }
    var mediaKind by remember { mutableStateOf(existing?.mediaKind ?: "COMIC") }
    var root by remember { mutableStateOf(existing?.root ?: "") }
    var sourceId by remember { mutableStateOf(existing?.contentSourceId ?: "") }
    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        if (type == "WEB" || !isEdit) sources = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
    }

    if (picking) {
        FolderPicker(session, api, initial = root.ifBlank { null }, onCancel = { picking = false }, onPick = { root = it; picking = false })
        return
    }

    val canSave = name.isNotBlank() && (if (type == "LOCAL") root.isNotBlank() else sourceId.isNotBlank())

    fun save() {
        val s = server ?: return
        saving = true
        scope.launch {
            val result = runCatching {
                if (isEdit) api.updateLibrary(s.baseUrl, s.apiKey, existing!!.id, UpdateLibraryRequest(name = name, root = root.ifBlank { null }, contentSourceId = sourceId.ifBlank { null }))
                else api.createLibrary(s.baseUrl, s.apiKey, CreateLibraryRequest(name = name, type = type, mediaKind = mediaKind, root = root.ifBlank { null }, contentSourceId = sourceId.ifBlank { null }))
            }
            saving = false
            result.fold(onSuccess = { onSaved() }, onFailure = { snackbar?.show("Couldn't save library.") })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit library" else "New library", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.size(12.dp))
            Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = type == "LOCAL", onClick = { if (!isEdit) type = "LOCAL" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Local folder") }
                SegmentedButton(selected = type == "WEB", onClick = { if (!isEdit) type = "WEB" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Web source") }
            }

            Spacer(Modifier.size(12.dp))
            Text("Content", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mediaKind == "COMIC", onClick = { mediaKind = "COMIC" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Comics") }
                SegmentedButton(selected = mediaKind == "BOOK", onClick = { mediaKind = "BOOK" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Books") }
            }

            Spacer(Modifier.size(12.dp))
            if (type == "LOCAL") {
                OutlinedTextField(
                    root, { root = it }, label = { Text("Folder path") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { picking = true }) { Text("Browse") } },
                )
            } else {
                Text("Source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(sources.firstOrNull { it.id == sourceId }?.displayName ?: "Choose a source")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        if (sources.isEmpty()) DropdownMenuItem(text = { Text("No sources installed") }, onClick = { open = false }, enabled = false)
                        sources.forEach { src -> DropdownMenuItem(text = { Text(src.displayName) }, onClick = { open = false; sourceId = src.id }) }
                    }
                }
            }

            Spacer(Modifier.size(20.dp))
            Button(onClick = { save() }, enabled = canSave && !saving, modifier = Modifier.fillMaxWidth()) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (isEdit) "Save" else "Create library")
            }
        }
    }
}

/** Server-side directory browser (`GET /filesystem`) for choosing a LOCAL library root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPicker(session: SessionManager, api: KodexApi, initial: String?, onCancel: () -> Unit, onPick: (String) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var current by remember { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(current, server?.id) {
        val s = server ?: return@LaunchedEffect
        loading = true
        listing = runCatching { api.listDirectory(s.baseUrl, s.apiKey, current) }.getOrNull()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.path?.ifBlank { "Choose folder" } ?: "Choose folder", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { listing?.path?.let { onPick(it) } }, enabled = listing?.path?.isNotBlank() == true, modifier = Modifier.weight(1f)) { Text("Use this folder") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading && listing == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                val l = listing
                LazyColumn(Modifier.fillMaxSize()) {
                    if (l?.parent != null) {
                        item {
                            Text("⬆  Up", Modifier.fillMaxWidth().clickable { current = l.parent }.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    items(l?.directories ?: emptyList(), key = { it.path }) { dir ->
                        Text("📁  ${dir.name}", Modifier.fillMaxWidth().clickable { current = dir.path }.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

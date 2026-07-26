package app.kodex.client.ui.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.kodex.client.network.LabelDto
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Manage metadata labels: list, create, rename, delete (admin-only server-side). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var labels by remember { mutableStateOf<List<LabelDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<LabelDto?>(null) } // rename target
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LabelDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        labels = runCatching { api.labels(s.baseUrl, s.apiKey) }.getOrNull()
    }

    fun act(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show("Action failed (admins only).") },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Labels", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New label") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val list = labels) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> if (list.isEmpty()) {
                    Text("No labels yet. Tap + to create one.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { label ->
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { editing = label }) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                                IconButton(onClick = { confirmDelete = label }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog("New label", "", onDismiss = { creating = false }) { name ->
            creating = false
            val s = server ?: return@NameDialog
            act("Label created") { api.createLabel(s.baseUrl, s.apiKey, name) }
        }
    }
    editing?.let { target ->
        NameDialog("Rename label", target.name, onDismiss = { editing = null }) { name ->
            editing = null
            val s = server ?: return@NameDialog
            act("Label renamed") { api.renameLabel(s.baseUrl, s.apiKey, target.id, name) }
        }
    }
    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete label?") },
            text = { Text("“${target.name}” will be removed from all series.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    val s = server ?: return@TextButton
                    act("Label deleted") { api.deleteLabel(s.baseUrl, s.apiKey, target.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

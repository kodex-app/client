package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.CreateRepositoryRequest
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.PluginRepositoryDto
import dev.icedtea.kodex.network.UpdateRepositoryRequest
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Where installable plugins come from: add, rename, enable/disable and remove repositories.
 *
 * A repository's token can be replaced or cleared but never read — the server only reports whether one
 * is stored, so the field is always blank on open and blank means "leave it alone".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRepositoriesScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var repos by remember { mutableStateOf<List<PluginRepositoryDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PluginRepositoryDto?>(null) }
    var confirmDelete by remember { mutableStateOf<PluginRepositoryDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.pluginRepositories(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { repos = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
    }

    fun act(message: String, block: suspend (String, String) -> Unit) {
        val s = server ?: return
        scope.launch {
            runCatching { block(s.baseUrl, s.apiKey) }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin repositories", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add repository")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val list = repos) {
                null -> if (loadError != null) ErrorState(loadError!!) { reload++ }
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> if (list.isEmpty()) {
                    EmptyMessage("No repositories. Add one to install plugins from it.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { repo ->
                            RepositoryRow(
                                repo = repo,
                                onToggleEnabled = {
                                    act(if (repo.enabled) "Repository disabled" else "Repository enabled") { b, k ->
                                        api.updatePluginRepository(b, k, repo.id, UpdateRepositoryRequest(enabled = !repo.enabled))
                                    }
                                },
                                onEdit = { editing = repo },
                                onDelete = { confirmDelete = repo },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        RepositoryDialog(title = "Add repository", initial = null, onDismiss = { creating = false }) { name, url, token, _ ->
            creating = false
            act("Repository added") { b, k -> api.addPluginRepository(b, k, CreateRepositoryRequest(name, url, token)) }
        }
    }

    editing?.let { repo ->
        RepositoryDialog(title = "Edit repository", initial = repo, onDismiss = { editing = null }) { name, url, token, clearToken ->
            editing = null
            act("Repository updated") { b, k ->
                api.updatePluginRepository(
                    b, k, repo.id,
                    UpdateRepositoryRequest(name = name, url = url, token = token, clearToken = clearToken.takeIf { it }),
                )
            }
        }
    }

    confirmDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove repository?") },
            text = { Text("${repo.name} is removed as a source of plugins. Plugins already installed from it stay installed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act("Repository removed") { b, k -> api.deletePluginRepository(b, k, repo.id) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RepositoryRow(
    repo: PluginRepositoryDto,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(repo.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                repo.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (repo.hasToken) {
                Text("Token stored", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Switch(checked = repo.enabled, onCheckedChange = { onToggleEnabled() })
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Repository actions")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** [onConfirm] receives name, url, a new token (null = unchanged) and whether to clear the stored one. */
@Composable
private fun RepositoryDialog(
    title: String,
    initial: PluginRepositoryDto?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var clearToken by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && url.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("URL") },
                    supportingText = { Text("The repository's plugins.json") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; if (it.isNotBlank()) clearToken = false },
                    singleLine = true,
                    enabled = !clearToken,
                    label = { Text(if (initial?.hasToken == true) "Replace token" else "Access token (optional)") },
                    supportingText = {
                        Text(if (initial?.hasToken == true) "Blank keeps the stored token" else "For private repositories")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial?.hasToken == true) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Remove the stored token", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = clearToken, onCheckedChange = { clearToken = it; if (it) token = "" })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), url.trim(), token.ifBlank { null }, clearToken) },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

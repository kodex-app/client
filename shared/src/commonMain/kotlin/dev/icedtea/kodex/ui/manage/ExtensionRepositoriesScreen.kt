package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LnReaderRepositoryDto
import dev.icedtea.kodex.network.MihonRepositoryDto
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import kotlinx.coroutines.launch

/**
 * Where the Mihon and LNReader screens get their lists from — the extension repositories, one
 * section per runtime. Nothing is bundled: an admin adds Keiyoushi's index and the LNReader manifest
 * here first. Each section lists, adds (fetched first), edits, reorders, switches on/off and removes;
 * installed extensions and plugins are never affected by changes to these lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionRepositoriesScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension repositories", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val s = server ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Where the Mihon and LNReader lists come from. Nothing is bundled — add Keiyoushi's index or the LNReader manifest first. Installed extensions and plugins are never affected by changes here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SettingsSectionHeader("Mihon")
            MihonRepositoriesSection(api, s.baseUrl, s.apiKey)
            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("LNReader")
            LnReaderRepositoriesSection(api, s.baseUrl, s.apiKey)
        }
    }
}

@Composable
fun MihonRepositoriesSection(api: KodexApi, baseUrl: String, apiKey: String) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<MihonRepositoryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newUrl by remember { mutableStateOf("") }
    // The URL of the row being edited; null while the field adds a new one ("Edit" loads a row into it).
    var editing by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        runCatching { api.mihonRepositories(baseUrl, apiKey) }.fold(onSuccess = { repos = it; error = null }, onFailure = { error = it.friendlyMessage() })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (val r = repos) {
                null -> if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator(Modifier.size(24.dp))
                else -> if (r.isEmpty()) {
                    Text("No repositories yet — add one below (Keiyoushi's index is the usual first).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else r.forEach { repo ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(repo.name ?: repo.url, repo.badgeLabel?.let { "[$it]" }).joinToString(" "),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(repo.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val status = when {
                                !repo.enabled -> "off — kept in the list, not fetched"
                                repo.error != null -> "unreachable: ${repo.error}"
                                repo.jarCount > 0 -> "${repo.jarCount} of ${repo.extensionCount} run here (JAR)"
                                else -> "APK only — nothing from it can run on the server"
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (repo.error != null || repo.jarCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            repo.signingKey?.takeIf { it.isNotBlank() }?.let { key ->
                                Text("key ${key.take(8)}…${key.takeLast(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val index = r.indexOf(repo)
                        fun moveBy(delta: Int) {
                            val urls = r.map { it.url }.toMutableList()
                            val j = index + delta
                            if (j !in urls.indices) return
                            urls[index] = urls[j].also { urls[j] = urls[index] }
                            scope.launch {
                                pending = true
                                runCatching { api.mihonReorderRepositories(baseUrl, apiKey, urls) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        }
                        Column {
                            IconButton(enabled = !pending && index > 0, onClick = { moveBy(-1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(enabled = !pending && index < r.lastIndex, onClick = { moveBy(1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                        }
                        Switch(checked = repo.enabled, enabled = !pending, onCheckedChange = { on ->
                            scope.launch {
                                pending = true
                                runCatching { api.mihonSetRepositoryEnabled(baseUrl, apiKey, repo.url, on) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        })
                        TextButton(enabled = !pending, onClick = { editing = repo.url; newUrl = repo.url }) { Text("Edit") }
                        TextButton(enabled = !pending, onClick = {
                            scope.launch {
                                pending = true
                                runCatching { api.mihonRemoveRepository(baseUrl, apiKey, repo.url) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        }) { Text("Remove") }
                    }
                }
            }
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                placeholder = { Text("https://…/index.json") },
                singleLine = true,
                enabled = !pending,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "Any URL the Mihon app accepts (index.json, index.pb, repo.json, index.min.json). Only JAR builds run on the server; Keiyoushi publishes one for every extension, other repositories usually ship APKs only.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (editing != null) TextButton(onClick = { editing = null; newUrl = "" }) { Text("Cancel") }
        TextButton(enabled = newUrl.isNotBlank() && !pending, onClick = {
            val target = editing
            scope.launch {
                pending = true
                runCatching {
                    if (target != null) api.mihonUpdateRepository(baseUrl, apiKey, target, newUrl.trim())
                    else api.mihonAddRepository(baseUrl, apiKey, newUrl.trim())
                }.fold(
                    onSuccess = { newUrl = ""; editing = null; error = null },
                    onFailure = { error = it.friendlyMessage() },
                )
                pending = false
                reload++
            }
        }) { Text(if (editing != null) "Save" else "Add") }
            }
        }
    }
}

@Composable
fun LnReaderRepositoriesSection(api: KodexApi, baseUrl: String, apiKey: String) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<LnReaderRepositoryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newUrl by remember { mutableStateOf("") }
    // The URL of the row being edited; null while the field adds a new one.
    var editing by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        runCatching { api.lnreaderRepositories(baseUrl, apiKey) }.fold(onSuccess = { repos = it; error = null }, onFailure = { error = it.friendlyMessage() })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (val r = repos) {
                null -> if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator(Modifier.size(24.dp))
                else -> if (r.isEmpty()) {
                    Text("No repositories yet — add one below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else r.forEach { repo ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repo.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    if (!repo.enabled) "off" else null,
                                    if (repo.builtIn) "from configuration" else null,
                                    repo.error?.let { "unreachable: $it" } ?: "${repo.pluginCount} plugins",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (repo.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val index = r.indexOf(repo)
                        fun moveBy(delta: Int) {
                            val urls = r.map { it.url }.toMutableList()
                            val j = index + delta
                            if (j !in urls.indices) return
                            urls[index] = urls[j].also { urls[j] = urls[index] }
                            scope.launch {
                                pending = true
                                runCatching { api.lnreaderReorderRepositories(baseUrl, apiKey, urls) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        }
                        Column {
                            IconButton(enabled = !pending && index > 0, onClick = { moveBy(-1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(enabled = !pending && index < r.lastIndex, onClick = { moveBy(1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                        }
                        Switch(checked = repo.enabled, enabled = !pending, onCheckedChange = { on ->
                            scope.launch {
                                pending = true
                                runCatching { api.lnreaderSetRepositoryEnabled(baseUrl, apiKey, repo.url, on) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        })
                        TextButton(enabled = !pending, onClick = { editing = repo.url; newUrl = repo.url }) { Text("Edit") }
                        TextButton(enabled = !pending, onClick = {
                            scope.launch {
                                pending = true
                                runCatching { api.lnreaderRemoveRepository(baseUrl, apiKey, repo.url) }.onFailure { error = it.friendlyMessage() }
                                pending = false
                                reload++
                            }
                        }) { Text("Remove") }
                    }
                }
            }
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                placeholder = { Text("https://…/plugins.min.json") },
                singleLine = true,
                enabled = !pending,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "Any plugins.min.json the LNReader app accepts. Repositories merge in order; a later one's entry replaces an earlier one with the same plugin id.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (editing != null) TextButton(onClick = { editing = null; newUrl = "" }) { Text("Cancel") }
        TextButton(enabled = newUrl.isNotBlank() && !pending, onClick = {
            val target = editing
            scope.launch {
                pending = true
                runCatching {
                    if (target != null) api.lnreaderUpdateRepository(baseUrl, apiKey, target, newUrl.trim())
                    else api.lnreaderAddRepository(baseUrl, apiKey, newUrl.trim())
                }.fold(
                    onSuccess = { newUrl = ""; editing = null; error = null },
                    onFailure = { error = it.friendlyMessage() },
                )
                pending = false
                reload++
            }
        }) { Text(if (editing != null) "Save" else "Add") }
            }
        }
    }
}

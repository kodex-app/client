package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LnReaderAvailableDto
import dev.icedtea.kodex.network.LnReaderInstalledDto
import dev.icedtea.kodex.network.LnReaderRepositoryDto
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * LNReader plugins — the ~280 compiled novel sources of the LNReader plugin repository, run by the
 * server. One list (installed first) with search, language chips and an installed-only toggle;
 * install / update / uninstall per row, per-source settings through [SourceConfigSheet] (the plugin's
 * own `pluginSettings`, a User-Agent override, pasted site storage), and the repository list in a
 * dialog. Admin-only. Mirrors the web UI's Extensions › LNReader tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LnReaderPluginsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<List<LnReaderAvailableDto>?>(null) }
    var installed by remember { mutableStateOf<List<LnReaderInstalledDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshNext by remember { mutableStateOf(false) }
    var updateCount by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var installedOnly by remember { mutableStateOf(false) }
    var hiddenLangs by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var reposOpen by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        val refresh = refreshNext
        refreshNext = false
        runCatching { api.lnreaderAvailable(s.baseUrl, s.apiKey, refresh) }.fold(
            onSuccess = { available = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
        runCatching { api.lnreaderInstalled(s.baseUrl, s.apiKey) }.onSuccess { installed = it }
        runCatching { api.lnreaderUpdateStatus(s.baseUrl, s.apiKey) }.onSuccess { updateCount = it.updates.size }
    }

    fun act(id: String?, message: String, block: suspend () -> Unit) {
        scope.launch {
            busy = id
            runCatching { block() }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
            busy = null
        }
    }

    // Sideloaded plugins the repository doesn't list, shaped like repository rows so they share the list.
    val rows = remember(available, installed) {
        val repo = available ?: emptyList()
        val known = repo.map { it.id }.toSet()
        val extra = (installed ?: emptyList()).filter { it.id !in known }.map {
            LnReaderAvailableDto(it.id, it.name, it.site, it.lang, it.lang, it.version, it.iconUrl, true, it.version, false, it.sourceId, it.webStorageUtilized)
        }
        (repo + extra).sortedWith(compareByDescending<LnReaderAvailableDto> { it.installed }.thenBy { it.name.lowercase() })
    }
    val languages = remember(rows) {
        rows.groupingBy { it.lang }.eachCount().entries.sortedWith(compareBy<Map.Entry<String, Int>> { it.key == "all" }.thenBy { it.key })
    }
    val list = remember(rows, query, installedOnly, hiddenLangs) {
        val q = query.trim().lowercase()
        rows.filter { r ->
            (!installedOnly || r.installed) && r.lang !in hiddenLangs &&
                (q.isEmpty() || r.name.lowercase().contains(q) || r.id.lowercase().contains(q) || r.site.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LNReader plugins", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Check for updates") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                val n = runCatching { val s = server!!; api.lnreaderCheckUpdates(s.baseUrl, s.apiKey).updates.size }.getOrNull()
                                snackbar?.show(if (n == null) "Check failed." else if (n == 0) "All plugins up to date." else "$n update(s) available.")
                                reload++
                            }
                        })
                        if (updateCount > 0) DropdownMenuItem(text = { Text("Update all ($updateCount)") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                val done = runCatching { val s = server!!; api.lnreaderUpdateAll(s.baseUrl, s.apiKey) }.getOrNull()
                                snackbar?.show(if (done == null) "Update failed." else "Updated ${done.count { it.updated }} of ${done.size}.")
                                reload++
                            }
                        })
                        DropdownMenuItem(text = { Text("Refresh repositories") }, onClick = { menuOpen = false; refreshNext = true; reload++ })
                        DropdownMenuItem(text = { Text("Repositories") }, onClick = { menuOpen = false; reposOpen = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search LNReader plugins…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                item {
                    FilterChip(selected = installedOnly, onClick = { installedOnly = !installedOnly }, label = { Text("Installed only") }, modifier = Modifier.padding(horizontal = 4.dp))
                }
                items(languages, key = { it.key }) { (lang, count) ->
                    FilterChip(
                        selected = lang !in hiddenLangs,
                        onClick = { hiddenLangs = if (lang in hiddenLangs) hiddenLangs - lang else hiddenLangs + lang },
                        label = { Text("${if (lang == "all") "multi" else lang} · $count") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            when {
                available == null && loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                available == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("No plugins match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("${list.size} of ${rows.size} plugins", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.id }) { p ->
                        PluginRow(
                            p, busy == p.id,
                            onInstall = { act(p.id, if (p.installed) "Updating ${p.name}…" else "Installing ${p.name}…") { val s = server!!; api.lnreaderInstall(s.baseUrl, s.apiKey, p.id) } },
                            onUninstall = { act(p.id, "Uninstalled ${p.name}") { val s = server!!; api.lnreaderUninstall(s.baseUrl, s.apiKey, p.id) } },
                            onConfigure = { p.sourceId?.let { configuring = it to p.name } },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    val s = server
    val cfg = configuring
    if (cfg != null && s != null) {
        SourceConfigSheet(
            api = api,
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            providerId = cfg.first,
            onDismiss = { configuring = null },
            onSaved = { message -> configuring = null; snackbar?.show(message) },
        )
    }
    if (reposOpen && s != null) {
        LnReaderRepositoriesDialog(api, s.baseUrl, s.apiKey, onDismiss = { reposOpen = false; reload++ })
    }
}

@Composable
private fun PluginRow(p: LnReaderAvailableDto, busy: Boolean, onInstall: () -> Unit, onUninstall: () -> Unit, onConfigure: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (p.iconUrl != null) AsyncImage(model = p.iconUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
            else Text(p.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.installed && p.webStorageUtilized) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(onClick = onConfigure, label = { Text("site storage", style = MaterialTheme.typography.labelSmall) })
                }
            }
            Text(
                listOfNotNull(p.lang, "v${p.installedVersion ?: p.version}", p.site.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            busy -> CircularProgressIndicator(Modifier.padding(horizontal = 20.dp).size(20.dp), strokeWidth = 2.dp)
            !p.installed -> TextButton(onClick = onInstall) { Text("Install") }
            else -> Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.updateAvailable) DropdownMenuItem(text = { Text("Update to v${p.version}") }, onClick = { menu = false; onInstall() })
                    if (p.sourceId != null) DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onConfigure() })
                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
                }
            }
        }
    }
}

/** The plugin repositories (manifest URLs) the server browses — list, add (fetched first), remove. */
@Composable
private fun LnReaderRepositoriesDialog(api: KodexApi, baseUrl: String, apiKey: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<LnReaderRepositoryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newUrl by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        runCatching { api.lnreaderRepositories(baseUrl, apiKey) }.fold(onSuccess = { repos = it; error = null }, onFailure = { error = it.friendlyMessage() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repositories") },
        text = {
            Column {
                when (val r = repos) {
                    null -> if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator(Modifier.size(24.dp))
                    else -> r.forEach { repo ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(repo.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(if (repo.builtIn) "default" else null, repo.error?.let { "unreachable: $it" } ?: "${repo.pluginCount} plugins").joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (repo.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
            }
        },
        confirmButton = {
            TextButton(enabled = newUrl.isNotBlank() && !pending, onClick = {
                scope.launch {
                    pending = true
                    runCatching { api.lnreaderAddRepository(baseUrl, apiKey, newUrl.trim()) }.fold(
                        onSuccess = { newUrl = ""; error = null },
                        onFailure = { error = it.friendlyMessage() },
                    )
                    pending = false
                    reload++
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

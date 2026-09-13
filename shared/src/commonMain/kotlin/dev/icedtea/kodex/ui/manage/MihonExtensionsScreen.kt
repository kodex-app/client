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
import dev.icedtea.kodex.network.MihonAvailableDto
import dev.icedtea.kodex.network.MihonInstalledDto
import dev.icedtea.kodex.network.MihonInstalledSourceDto
import dev.icedtea.kodex.network.MihonRepositoryDto
import dev.icedtea.kodex.network.MihonSourceDto
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Mihon extensions — the Keiyoushi repository's 1300+ Tachiyomi/Mihon extensions (their JVM JAR
 * builds), run by the server. One list (installed first) with search, an installed-only toggle, an
 * 18+ toggle and language chips; install / update / uninstall per row, per-source settings through
 * [SourceConfigSheet] (the extension's own preferences), "open in browser" to log in to a source's site
 * on the server, and the repository list in a dialog. Admin-only. Mirrors the web UI's Extensions ›
 * Mihon tab; sideloading a JAR stays on the web UI (no file picker here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihonExtensionsScreen(
    session: SessionManager,
    api: KodexApi,
    onBack: () -> Unit,
    /** Opens the interactive browser at a source's site (sourceId, title). */
    onOpenBrowser: (sourceId: String, title: String) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<List<MihonAvailableDto>?>(null) }
    var installed by remember { mutableStateOf<List<MihonInstalledDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshNext by remember { mutableStateOf(false) }
    var updateCount by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var installedOnly by remember { mutableStateOf(false) }
    var showNsfw by remember { mutableStateOf(false) }
    var hiddenLangs by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var reposOpen by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<Pair<String, String>?>(null) }
    // An installed extension with several sources: which one to configure / open is asked in a dialog.
    var pickSource by remember { mutableStateOf<Pair<MihonInstalledDto, (MihonInstalledSourceDto) -> Unit>?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        val refresh = refreshNext
        refreshNext = false
        runCatching { api.mihonAvailable(s.baseUrl, s.apiKey, refresh) }.fold(
            onSuccess = { available = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
        runCatching { api.mihonInstalled(s.baseUrl, s.apiKey) }.onSuccess { installed = it }
        runCatching { api.mihonUpdateStatus(s.baseUrl, s.apiKey) }.onSuccess { updateCount = it.updates.size }
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

    val installedByPkg = remember(installed) { (installed ?: emptyList()).associateBy { it.packageName } }
    // Sideloaded JARs the repository doesn't list, shaped like repository rows so they share the list.
    val rows = remember(available, installed) {
        val repo = available ?: emptyList()
        val known = repo.map { it.packageName }.toSet()
        val extra = (installed ?: emptyList()).filter { it.packageName !in known }.map { i ->
            val langs = i.sources.map { it.lang ?: "all" }.toSet()
            MihonAvailableDto(
                i.packageName, i.name, if (langs.size == 1) langs.first() else "all", i.versionName, i.versionCode.toLong(), i.libVersion,
                i.iconUrl, i.nsfw, if (i.nsfw) "NSFW" else "SAFE",
                i.sources.map { MihonSourceDto(it.id, it.name, it.lang ?: "all", it.website ?: "") },
                installed = true, installedVersion = i.versionName, updateAvailable = false, repo = "sideloaded",
            )
        }
        (repo + extra).sortedWith(compareByDescending<MihonAvailableDto> { it.installed }.thenBy { it.name.lowercase() })
    }
    val multiRepo = remember(available) { (available ?: emptyList()).map { it.repoUrl }.toSet().size > 1 }
    val languages = remember(rows) {
        rows.groupingBy { it.lang }.eachCount().entries.sortedWith(compareBy<Map.Entry<String, Int>> { it.key == "all" }.thenBy { it.key })
    }
    val list = remember(rows, query, installedOnly, showNsfw, hiddenLangs) {
        val q = query.trim().lowercase()
        rows.filter { r ->
            (!installedOnly || r.installed) && (showNsfw || !r.nsfw || r.installed) && r.lang !in hiddenLangs &&
                (q.isEmpty() || r.name.lowercase().contains(q) || r.packageName.lowercase().contains(q) ||
                    r.sources.any { it.name.lowercase().contains(q) || it.homeUrl.lowercase().contains(q) })
        }
    }

    /** Runs [block] for the extension's single source, or asks which one when it bundles several. */
    fun withSource(e: MihonAvailableDto, block: (MihonInstalledSourceDto) -> Unit) {
        val inst = installedByPkg[e.packageName] ?: return
        when (inst.sources.size) {
            0 -> snackbar?.show("This extension has no sources.")
            1 -> block(inst.sources.first())
            else -> pickSource = inst to block
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mihon extensions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Check for updates") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                val n = runCatching { val s = server!!; api.mihonCheckUpdates(s.baseUrl, s.apiKey).updates.size }.getOrNull()
                                snackbar?.show(if (n == null) "Check failed." else if (n == 0) "All extensions up to date." else "$n update(s) available.")
                                reload++
                            }
                        })
                        if (updateCount > 0) DropdownMenuItem(text = { Text("Update all ($updateCount)") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                val done = runCatching { val s = server!!; api.mihonUpdateAll(s.baseUrl, s.apiKey) }.getOrNull()
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
                placeholder = { Text("Search extensions…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                item {
                    FilterChip(selected = installedOnly, onClick = { installedOnly = !installedOnly }, label = { Text("Installed only") }, modifier = Modifier.padding(horizontal = 4.dp))
                }
                item {
                    FilterChip(selected = showNsfw, onClick = { showNsfw = !showNsfw }, label = { Text("18+") }, modifier = Modifier.padding(horizontal = 4.dp))
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
                    Text("No extensions match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("${list.size} of ${rows.size} extensions", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.packageName }) { e ->
                        ExtensionRow(
                            e, busy == e.packageName, multiRepo,
                            onInstall = { act(e.packageName, if (e.installed) "Updating ${e.name}…" else "Installing ${e.name}…") { val s = server!!; api.mihonInstall(s.baseUrl, s.apiKey, e.packageName) } },
                            onUninstall = { act(e.packageName, "Uninstalled ${e.name}") { val s = server!!; api.mihonUninstall(s.baseUrl, s.apiKey, e.packageName) } },
                            onConfigure = { withSource(e) { src -> configuring = src.id to (if (src.lang != null) "${src.name} (${src.lang})" else src.name) } },
                            onOpenBrowser = { withSource(e) { src -> onOpenBrowser(src.id, src.name) } },
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
    pickSource?.let { (ext, block) ->
        AlertDialog(
            onDismissRequest = { pickSource = null },
            title = { Text(ext.name) },
            text = {
                Column {
                    ext.sources.forEach { src ->
                        TextButton(onClick = { pickSource = null; block(src) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (src.lang != null) "${src.name} (${src.lang})" else src.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickSource = null }) { Text("Cancel") } },
        )
    }
    if (reposOpen && s != null) {
        MihonRepositoriesDialog(api, s.baseUrl, s.apiKey, onDismiss = { reposOpen = false; reload++ })
    }
}

@Composable
private fun ExtensionRow(
    e: MihonAvailableDto,
    busy: Boolean,
    multiRepo: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onConfigure: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!e.iconUrl.isNullOrBlank()) AsyncImage(model = e.iconUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
            else Text(e.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.nsfw) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(onClick = {}, label = { Text("18+", style = MaterialTheme.typography.labelSmall) })
                }
                if (e.repo == "sideloaded" || (multiRepo && !e.repo.isNullOrBlank())) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(onClick = {}, label = { Text(e.repo!!, style = MaterialTheme.typography.labelSmall) })
                }
            }
            val langs = e.sources.map { it.lang }.distinct()
            val langLabel = if (langs.size > 3) langs.take(3).joinToString(", ") + " +${langs.size - 3}" else langs.joinToString(", ").ifBlank { e.lang }
            Text(
                listOfNotNull(langLabel, "v${e.installedVersion ?: e.versionName}", if (e.sources.size == 1) e.sources[0].homeUrl.ifBlank { null } else "${e.sources.size} sources").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            busy -> CircularProgressIndicator(Modifier.padding(horizontal = 20.dp).size(20.dp), strokeWidth = 2.dp)
            !e.installed -> TextButton(onClick = onInstall) { Text("Install") }
            else -> Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (e.updateAvailable) DropdownMenuItem(text = { Text("Update to v${e.versionName}") }, onClick = { menu = false; onInstall() })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onConfigure() })
                    DropdownMenuItem(text = { Text("Open in browser") }, onClick = { menu = false; onOpenBrowser() })
                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
                }
            }
        }
    }
}

/**
 * The extension repositories the server browses — Keiyoushi by default, any index URL the Mihon app
 * accepts. Only JAR builds run on the server, so each repository shows how many it has; an APK-only one
 * is flagged rather than silently contributing nothing.
 */
@Composable
private fun MihonRepositoriesDialog(api: KodexApi, baseUrl: String, apiKey: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<MihonRepositoryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newUrl by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        runCatching { api.mihonRepositories(baseUrl, apiKey) }.fold(onSuccess = { repos = it; error = null }, onFailure = { error = it.friendlyMessage() })
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
                                Text(
                                    listOfNotNull(repo.name ?: repo.url, repo.badgeLabel?.let { "[$it]" }).joinToString(" "),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(repo.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val status = when {
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
            }
        },
        confirmButton = {
            TextButton(enabled = newUrl.isNotBlank() && !pending, onClick = {
                scope.launch {
                    pending = true
                    runCatching { api.mihonAddRepository(baseUrl, apiKey, newUrl.trim()) }.fold(
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

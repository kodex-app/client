@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
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
import dev.icedtea.kodex.network.MihonSourceDto
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Mihon extensions — the Tachiyomi/Mihon extensions of the configured repositories (their JVM JAR
 * builds, e.g. Keiyoushi's 1300+), run by the server. Sections per language, collapsed except
 * "Installed", with search, an installed-only toggle, an 18+ toggle and language chips; install /
 * update / uninstall per row, per-source settings through [SourceConfigSheet] (the extension's own
 * preferences), "Open site" in the device browser, and "Log in via server browser" for a source's
 * site (cookies go to the extension). The repository list lives in [ExtensionRepositoriesScreen].
 * Admin-only. Mirrors the web UI's Extensions › Mihon tab; sideloading a JAR stays on the web UI.
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

    // Sectioned like the Mihon app: what is installed first, then one section per language (multi-language
    // last), each a sticky band — a long repository reads as a table of contents, not one alphabetical wall.
    // Expanded section keys: only what is installed starts open, languages stay folded until tapped.
    var expanded by remember { mutableStateOf(setOf("installed")) }
    val sections = remember(list) {
        val byInstalled = list.partition { it.installed }
        val langs = byInstalled.second.groupBy { it.lang }.entries
            .sortedWith(compareBy<Map.Entry<String, List<MihonAvailableDto>>> { it.key == "all" }.thenBy { languageLabel(it.key).lowercase() })
        buildList {
            if (byInstalled.first.isNotEmpty()) add("installed" to ("Installed" to byInstalled.first))
            langs.forEach { add(it.key to (languageLabel(it.key) to it.value)) }
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
                        label = { Text("${languageLabel(lang)} · $count") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            when {
                available == null && loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                available == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        if (rows.isEmpty()) "No repositories yet — add one under More › Extension repositories." else "No extensions match.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("${list.size} of ${rows.size} extensions", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sections.forEach { (key, section) ->
                        val (label, extensions) = section
                        stickyHeader(key = "h:$key") {
                            LanguageSectionHeader(label, extensions.size, expanded = key in expanded) {
                                expanded = if (key in expanded) expanded - key else expanded + key
                            }
                        }
                        if (key in expanded) items(extensions, key = { it.packageName }) { e ->
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
    // The source's website, opened in the device's own browser (the server-side browser is only for logging in).
    val uriHandler = LocalUriHandler.current
    val site = e.sources.firstOrNull { it.homeUrl.isNotBlank() }?.homeUrl
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
            !e.installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                site?.let { url -> IconButton(onClick = { uriHandler.openUri(url) }) { Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open site") } }
                TextButton(onClick = onInstall) { Text("Install") }
            }
            else -> Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (e.updateAvailable) DropdownMenuItem(text = { Text("Update to v${e.versionName}") }, onClick = { menu = false; onInstall() })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onConfigure() })
                    site?.let { url -> DropdownMenuItem(text = { Text("Open site") }, onClick = { menu = false; uriHandler.openUri(url) }) }
                    DropdownMenuItem(text = { Text("Log in via server browser") }, onClick = { menu = false; onOpenBrowser() })
                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
                }
            }
        }
    }
}


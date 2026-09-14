@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.SourcePrefsStore
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LnReaderAvailableDto
import dev.icedtea.kodex.network.LnReaderInstalledDto
import dev.icedtea.kodex.network.lnreaderAvailable
import dev.icedtea.kodex.network.lnreaderCheckUpdates
import dev.icedtea.kodex.network.lnreaderInstall
import dev.icedtea.kodex.network.lnreaderInstalled
import dev.icedtea.kodex.network.lnreaderUninstall
import dev.icedtea.kodex.network.lnreaderUpdateAll
import dev.icedtea.kodex.network.lnreaderUpdateStatus
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.LanguageFilterButton
import dev.icedtea.kodex.ui.SourceAvatar
import dev.icedtea.kodex.ui.SourceCard
import dev.icedtea.kodex.ui.SourceFilterRow
import dev.icedtea.kodex.ui.SourceListContentPadding
import dev.icedtea.kodex.ui.SourceSearchField
import dev.icedtea.kodex.ui.SourceSectionHeader
import dev.icedtea.kodex.ui.catalog.ColorBadge
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.languageLabel
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * LNReader plugins — the ~280 compiled novel sources of the LNReader plugin repository, run by the
 * server. One list (installed first) with search, language chips and an installed-only toggle;
 * install / update / uninstall per row, per-source settings through [SourceConfigSheet] (the plugin's
 * own `pluginSettings`, a User-Agent override, pasted site storage). Sections per language, collapsed
 * except "Installed". The repository list lives in [ExtensionRepositoriesScreen]. Admin-only. Mirrors
 * the web UI's Extensions › LNReader tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LnReaderPluginsScreen(session: SessionManager, api: KodexApi, sourcePrefs: SourcePrefsStore, onBack: () -> Unit) {
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
    // Server-side and shared with the other extension list and the web UI (see SourcePrefsStore).
    val hiddenLangs by sourcePrefs.extensionHiddenLanguages.collectAsStateSafe()
    var busy by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
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
        rows.groupingBy { it.lang }.eachCount().entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == "all" }.thenBy { languageLabel(it.key).lowercase() })
            .map { it.key to it.value }
    }
    val list = remember(rows, query, installedOnly, hiddenLangs) {
        val q = query.trim().lowercase()
        rows.filter { r ->
            (!installedOnly || r.installed) && r.lang !in hiddenLangs &&
                (q.isEmpty() || r.name.lowercase().contains(q) || r.id.lowercase().contains(q) || r.site.lowercase().contains(q))
        }
    }

    // Sectioned like the Mihon app: what is installed first, then one section per language (multi-language
    // last), each a sticky band — a long repository reads as a table of contents, not one alphabetical wall.
    // Expanded section keys: only what is installed starts open, languages stay folded until tapped.
    var expanded by remember { mutableStateOf(setOf("installed")) }
    val sections = remember(list) {
        val byInstalled = list.partition { it.installed }
        val langs = byInstalled.second.groupBy { it.lang }.entries
            .sortedWith(compareBy<Map.Entry<String, List<LnReaderAvailableDto>>> { it.key == "all" }.thenBy { languageLabel(it.key).lowercase() })
        buildList {
            if (byInstalled.first.isNotEmpty()) add("installed" to ("Installed" to byInstalled.first))
            langs.forEach { add(it.key to (languageLabel(it.key) to it.value)) }
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
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SourceSearchField(query, onValueChange = { query = it }, placeholder = "Search plugins")
            SourceFilterRow {
                item {
                    LanguageFilterButton(
                        languages = languages,
                        hidden = hiddenLangs,
                        onToggle = sourcePrefs::toggleExtensionHiddenLanguage,
                        onSetHidden = sourcePrefs::setExtensionHiddenLanguages,
                    )
                }
                item { FilterChip(selected = installedOnly, onClick = { installedOnly = !installedOnly }, label = { Text("Installed only") }) }
            }
            when {
                available == null && loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                available == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        if (rows.isEmpty()) "No repositories yet — add one under More › Extension repositories." else "No plugins match.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = SourceListContentPadding) {
                    sections.forEach { (key, section) ->
                        val (label, plugins) = section
                        stickyHeader(key = "h:$key") {
                            SourceSectionHeader(label, plugins.size, expanded = key in expanded) {
                                expanded = if (key in expanded) expanded - key else expanded + key
                            }
                        }
                        if (key in expanded) items(plugins, key = { it.id }) { p ->
                            PluginRow(
                                p, busy == p.id,
                                onInstall = { act(p.id, if (p.installed) "Updating ${p.name}…" else "Installing ${p.name}…") { val s = server!!; api.lnreaderInstall(s.baseUrl, s.apiKey, p.id) } },
                                onUninstall = { act(p.id, "Uninstalled ${p.name}") { val s = server!!; api.lnreaderUninstall(s.baseUrl, s.apiKey, p.id) } },
                                onConfigure = { p.sourceId?.let { configuring = it to p.name } },
                            )
                            Spacer(Modifier.height(10.dp))
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
}

@Composable
private fun PluginRow(p: LnReaderAvailableDto, busy: Boolean, onInstall: () -> Unit, onUninstall: () -> Unit, onConfigure: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // The plugin's site, opened in the device's own browser.
    val uriHandler = LocalUriHandler.current
    SourceCard(
        title = p.name,
        avatar = { SourceAvatar(p.iconUrl?.takeIf { it.isNotBlank() }, p.name) },
        subtitle = listOfNotNull("v${p.installedVersion ?: p.version}", p.site.ifBlank { null }).joinToString(" · "),
        badges = {
            ColorBadge(if (p.lang == "all") "Multi" else p.lang.uppercase())
            if (p.installed && p.updateAvailable) ColorBadge("Update", container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
            // Pasted site storage in use — tapping the card's Settings is where it lives, hence the badge here.
            if (p.installed && p.webStorageUtilized) ColorBadge("Site storage")
        },
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.padding(horizontal = 20.dp).size(20.dp), strokeWidth = 2.dp)
            !p.installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.site.isNotBlank()) IconButton(onClick = { uriHandler.openUri(p.site) }) { Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open site") }
                TextButton(onClick = onInstall) { Text("Install") }
            }
            else -> Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.updateAvailable) DropdownMenuItem(text = { Text("Update to v${p.version}") }, onClick = { menu = false; onInstall() })
                    if (p.sourceId != null) DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onConfigure() })
                    if (p.site.isNotBlank()) DropdownMenuItem(text = { Text("Open site") }, onClick = { menu = false; uriHandler.openUri(p.site) })
                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
                }
            }
        }
    }
}


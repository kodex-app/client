package dev.icedtea.kodex.ui.manage

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
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
import dev.icedtea.kodex.network.AvailablePluginDto
import dev.icedtea.kodex.network.InstalledPluginDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Manage plugins: Installed (enable/disable/uninstall) and Browse (install from repository). Admin-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit, onOpenRepositories: () -> Unit = {}) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    // Tab selection lives in the pager so the strip and the content can't disagree — swiping
    // and tapping drive the same state.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(0) { 2 }
    var installed by remember { mutableStateOf<List<InstalledPluginDto>?>(null) }
    var available by remember { mutableStateOf<List<AvailablePluginDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // One slot per list: the two tabs fetch independently, so a shared slot let whichever call
    // finished second clear the other's failure and leave that tab spinning forever.
    var installedError by remember { mutableStateOf<String?>(null) }
    var availableError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // The provider whose settings sheet is open; its schema is fetched by the sheet itself.
    var configuring by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.installedPlugins(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { installed = it; installedError = null },
            onFailure = { installedError = it.friendlyMessage() },
        )
        runCatching { api.availablePlugins(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { available = it; availableError = null },
            onFailure = { availableError = it.friendlyMessage() },
        )
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
                title = { Text("Plugins", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Check for updates") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                val n = runCatching { val s = server!!; api.checkPluginUpdates(s.baseUrl, s.apiKey).updates.size }.getOrNull()
                                snackbar?.show(if (n == null) "Check failed." else if (n == 0) "All plugins up to date." else "$n update(s) available.")
                                reload++
                            }
                        })
                        DropdownMenuItem(text = { Text("Refresh catalogue") }, onClick = {
                            menuOpen = false; act("Catalogue refreshed") { val s = server!!; api.refreshAvailablePlugins(s.baseUrl, s.apiKey) }
                        })
                        DropdownMenuItem(text = { Text("Repositories") }, onClick = { menuOpen = false; onOpenRepositories() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                listOf("Installed", "Browse").forEachIndexed { i, label ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = { Text(label) },
                    )
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) InstalledList(installed, installedError, { reload++ }, onEnable = { id -> act("Enabled") { val s = server!!; api.pluginAction(s.baseUrl, s.apiKey, id, "enable") } },
                    onDisable = { id -> act("Disabled") { val s = server!!; api.pluginAction(s.baseUrl, s.apiKey, id, "disable") } },
                    onUpdate = { id -> act("Updating…") { val s = server!!; api.pluginAction(s.baseUrl, s.apiKey, id, "update") } },
                    onUninstall = { id -> act("Uninstalled") { val s = server!!; api.uninstallPlugin(s.baseUrl, s.apiKey, id) } },
                    onConfigure = { id -> configuring = id })
                else BrowseList(available, installed, availableError, { reload++ }, onInstall = { p -> act("Installing ${p.name}…") { val s = server!!; api.installPlugin(s.baseUrl, s.apiKey, p.id, p.latestVersion) } })
            }
        }
    }

    val s = server
    if (configuring != null && s != null) {
        SourceConfigSheet(
            api = api,
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            providerId = configuring!!,
            onDismiss = { configuring = null },
            onSaved = { message -> configuring = null; snackbar?.show(message) },
        )
    }
}

@Composable
private fun InstalledList(
    installed: List<InstalledPluginDto>?,
    loadError: String?,
    onRetry: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onConfigure: (String) -> Unit,
) {
    when (installed) {
        null -> if (loadError != null) ErrorState(loadError, onRetry = onRetry)
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else -> if (installed.isEmpty()) Empty("No plugins installed.")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(installed, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull("v${p.version}", p.kind, p.state).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val disabled = p.state.equals("DISABLED", ignoreCase = true) || p.state.equals("STOPPED", ignoreCase = true)
                            if (disabled) DropdownMenuItem(text = { Text("Enable") }, onClick = { menu = false; onEnable(p.id) })
                            else DropdownMenuItem(text = { Text("Disable") }, onClick = { menu = false; onDisable(p.id) })
                            DropdownMenuItem(text = { Text("Update") }, onClick = { menu = false; onUpdate(p.id) })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onConfigure(p.id) })
                            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall(p.id) })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun BrowseList(
    available: List<AvailablePluginDto>?,
    installed: List<InstalledPluginDto>?,
    loadError: String?,
    onRetry: () -> Unit,
    onInstall: (AvailablePluginDto) -> Unit,
) {
    when (available) {
        null -> if (loadError != null) ErrorState(loadError, onRetry = onRetry)
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else -> if (available.isEmpty()) Empty("No plugins in the repository.\nAdd a plugin repository on the server.")
        else {
            val installedIds = installed?.map { it.id }?.toSet() ?: emptySet()
            LazyColumn(Modifier.fillMaxSize()) {
                items(available, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (!p.description.isNullOrBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull("v${p.latestVersion}", p.kind, p.provider).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.id in installedIds) Text("Installed", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        else TextButton(onClick = { onInstall(p) }) { Text("Install") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

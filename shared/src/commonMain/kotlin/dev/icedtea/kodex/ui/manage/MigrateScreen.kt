package dev.icedtea.kodex.ui.manage

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.MigrateRequest
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.SourceSearchResult
import dev.icedtea.kodex.network.contentSources
import dev.icedtea.kodex.network.followedSeriesRef
import dev.icedtea.kodex.network.migrateSeries
import dev.icedtea.kodex.network.migrationCandidates
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.languageLabel
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Mihon-style source migration: re-link a followed WEB series to another source. Pick a target source,
 * find the matching series there, choose what to carry over, and migrate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    session: SessionManager,
    api: KodexApi,
    seriesId: String,
    currentProviderId: String,
    sourceSeriesId: String,
    seriesTitle: String,
    onBack: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var libraryId by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<SourceDescriptor?>(null) }
    var query by remember { mutableStateOf(seriesTitle) }
    var candidates by remember { mutableStateOf<List<SourceSearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var migrating by remember { mutableStateOf(false) }
    var migrateRead by remember { mutableStateOf(true) }
    var migrateMeta by remember { mutableStateOf(true) }
    var deleteDownloads by remember { mutableStateOf(false) }

    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        // Targets: same media kind as the origin (a comic can't move to a novel plugin) and the same
        // language — plus multi-language sources, which carry every language — never itself.
        // Same-language ones first, then multi-language.
        val all = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        val from = all.firstOrNull { it.id == currentProviderId }
        sources = all
            .filter { it.id != currentProviderId && (from == null || (it.mediaKind == from.mediaKind && (it.language == from.language || it.language == null))) }
            .sortedWith(compareBy<SourceDescriptor> { it.language == null }.thenBy { it.displayName.lowercase() })
        libraryId = runCatching { api.followedSeriesRef(s.baseUrl, s.apiKey, currentProviderId, sourceSeriesId)?.libraryId }.getOrNull()
    }

    fun search() {
        val s = server ?: return; val t = target ?: return; val lib = libraryId ?: return
        searching = true
        scope.launch {
            candidates = runCatching { api.migrationCandidates(s.baseUrl, s.apiKey, lib, seriesId, t.id, query.ifBlank { null }) }.getOrNull() ?: emptyList()
            searching = false
        }
    }

    fun migrate(candidate: SourceSearchResult) {
        val s = server ?: return; val t = target ?: return; val lib = libraryId ?: return
        migrating = true
        scope.launch {
            runCatching {
                api.migrateSeries(s.baseUrl, s.apiKey, lib, seriesId, MigrateRequest(
                    targetProviderId = candidate.providerId ?: t.id,
                    targetExternalId = candidate.externalId,
                    migrateRead = migrateRead, deleteDownloads = deleteDownloads, migrateMetadata = migrateMeta,
                ))
            }.fold(
                onSuccess = { snackbar?.show("Migrated to ${t.displayName}"); onBack() },
                onFailure = { snackbar?.show("Migration failed."); migrating = false },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Migrate series", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (libraryId == null) {
                Text("Only followed WEB series can be migrated.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("Target source", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            var open by remember { mutableStateOf(false) }
            Box {
                val chosen = target?.let { t -> if (sources.count { it.displayName == t.displayName } > 1) "${t.displayName} (${languageLabel(t.language ?: "all")})" else t.displayName }
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(chosen ?: "Choose a source") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    if (sources.isEmpty()) DropdownMenuItem(text = { Text("No other sources installed") }, onClick = { open = false }, enabled = false)
                    // One extension can bundle a source per language under one name (MangaDex × 60): a
                    // duplicated name gets its language appended so the entries can be told apart.
                    val dup = sources.groupingBy { it.displayName }.eachCount()
                    sources.forEach { src ->
                        val label = if ((dup[src.displayName] ?: 0) > 1) "${src.displayName} (${languageLabel(src.language ?: "all")})" else src.displayName
                        DropdownMenuItem(text = { Text(label) }, onClick = { open = false; target = src; candidates = null })
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            OutlinedTextField(query, { query = it }, label = { Text("Title to match") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OptionCheck("Carry over read progress", migrateRead) { migrateRead = it }
            OptionCheck("Carry over metadata", migrateMeta) { migrateMeta = it }
            OptionCheck("Delete downloaded chapters", deleteDownloads) { deleteDownloads = it }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { search() }, enabled = target != null && !searching, modifier = Modifier.fillMaxWidth()) { Text("Find matches") }

            Spacer(Modifier.size(12.dp))
            Box(Modifier.fillMaxSize()) {
                when {
                    searching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    candidates == null -> {}
                    candidates!!.isEmpty() -> Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(candidates!!, key = { it.externalId }) { c ->
                            Column(Modifier.fillMaxWidth().clickable(enabled = !migrating) { migrate(c) }.padding(vertical = 12.dp)) {
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (!c.author.isNullOrBlank()) Text(c.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

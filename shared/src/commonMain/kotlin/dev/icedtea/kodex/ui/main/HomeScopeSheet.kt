package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.LibraryNavPrefs
import dev.icedtea.kodex.data.SourcePrefsStore
import dev.icedtea.kodex.data.loadLibraryNavPrefs
import dev.icedtea.kodex.data.orderedBy
import dev.icedtea.kodex.data.saveLibraryNavPrefs
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.contentSources
import dev.icedtea.kodex.network.libraries
import dev.icedtea.kodex.ui.KodexBottomSheet
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.languageLabel
import kotlinx.coroutines.launch

/**
 * "Customize Home": which libraries and content sources (plugins) feed the Home rails. Writes the same
 * server-side preferences the web UI does (`nav.libraries.hiddenFromHome`, `browse.hiddenFromHome`), and
 * the server applies them when it builds `GET /api/v1/home`. Hiding only affects Home: the library tab,
 * Browse and search still show everything.
 *
 * [onDismiss] gets whether anything changed, so Home reloads only when it needs to.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScopeSheet(
    session: SessionManager,
    api: KodexApi,
    sourcePrefs: SourcePrefsStore,
    onDismiss: (changed: Boolean) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val hiddenSources by sourcePrefs.hiddenFromHome.collectAsStateSafe()
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<LibraryDto>?>(null) }
    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var navPrefs by remember { mutableStateOf(LibraryNavPrefs()) }
    var changed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sourceFilter by remember { mutableStateOf("") }

    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        navPrefs = loadLibraryNavPrefs(api, s.baseUrl, s.apiKey)
        libraries = runCatching { api.libraries(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()).orderedBy(navPrefs)
        sources = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.displayName.lowercase() }, { it.language.orEmpty() }))
    }

    fun save(block: suspend () -> Unit) {
        changed = true
        scope.launch { runCatching { block() }.onFailure { error = it.friendlyMessage() } }
    }

    KodexBottomSheet(onDismissRequest = { onDismiss(changed) }, fraction = 0.85f) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Customize Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Choose what feeds the Home rails. Hidden items stay reachable from Libraries, Browse and Search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            SectionHeader("Libraries")
            val libs = libraries
            if (libs == null) {
                CircularProgressIndicator(Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
            } else if (libs.isEmpty()) {
                EmptyLine("No libraries yet.")
            } else {
                libs.forEach { lib ->
                    val everywhere = lib.id in navPrefs.hiddenEverywhere
                    ToggleRow(
                        title = lib.name,
                        note = if (everywhere) "Hidden everywhere (set on the web)" else null,
                        checked = !everywhere && !navPrefs.isHiddenFromHome(lib.id),
                        enabled = !everywhere,
                    ) { show ->
                        val next = navPrefs.withHiddenFromHome(lib.id, !show)
                        navPrefs = next
                        val s = server ?: return@ToggleRow
                        save { saveLibraryNavPrefs(api, s.baseUrl, s.apiKey, next) }
                    }
                }
            }

            SectionHeader("Sources")
            if (sources.isEmpty()) {
                EmptyLine("No content sources installed.")
            } else {
                // One multi-language plugin alone can add dozens of rows, so a filter once the list is long.
                if (sources.size > 8) {
                    OutlinedTextField(
                        value = sourceFilter,
                        onValueChange = { sourceFilter = it },
                        placeholder = { Text("Filter sources") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                val q = sourceFilter.trim().lowercase()
                sources.filter { q.isEmpty() || "${it.displayName} ${it.language?.let(::languageLabel).orEmpty()}".lowercase().contains(q) }
                    .forEach { src ->
                    ToggleRow(
                        title = src.displayName.ifBlank { src.id },
                        // One plugin can ship many same-named sources; the language tells them apart.
                        note = src.language?.takeIf { it.isNotBlank() }?.let(::languageLabel),
                        checked = src.id !in hiddenSources,
                    ) { show -> save { sourcePrefs.setHiddenFromHome(src.id, !show) } }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ToggleRow(title: String, note: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

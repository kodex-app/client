package dev.icedtea.kodex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.persistSetting
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.collectAsStateSafe
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-user settings backed by the server (`/users/me/settings`). Currently the two series-behaviour
 * prefs the mobile app uses — auto-update-on-open and the default chapter sort. Reader defaults and
 * device sync will join here as those screens land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = server?.id,
                load = { val s = server!!; SettingsData(api.userSettings(s.baseUrl, s.apiKey), api.libraries(s.baseUrl, s.apiKey)) },
            ) { data ->
                val s = server ?: return@LoadedContent
                SettingsForm(session, s.baseUrl, s.apiKey, api, data.settings, data.libraries)
            }
        }
    }
}

private data class SettingsData(val settings: JsonObject, val libraries: List<dev.icedtea.kodex.network.LibraryDto>)

private const val CHAPTER_SORT_DEFAULT = "number,desc"

@Composable
private fun SettingsForm(
    session: SessionManager,
    baseUrl: String,
    apiKey: String,
    api: KodexApi,
    initial: JsonObject,
    libraries: List<dev.icedtea.kodex.network.LibraryDto>,
) {
    val snackbar = dev.icedtea.kodex.ui.rememberSnackbar()

    var autoUpdate by remember {
        mutableStateOf(initial["series.autoUpdateOnOpen"]?.jsonPrimitive?.booleanOrNull ?: true)
    }
    var chapterSort by remember {
        mutableStateOf(initial["series.chapterSort"]?.jsonPrimitive?.contentOrNull ?: CHAPTER_SORT_DEFAULT)
    }
    // sync.libraries: empty selection ⇒ all libraries sync (server default).
    var syncLibs by remember {
        mutableStateOf(
            (initial["sync.libraries"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
        )
    }
    var comicPrefs by remember { mutableStateOf(dev.icedtea.kodex.ui.reader.parseReaderDefault(initial, "comic")) }
    var pdfPrefs by remember { mutableStateOf(dev.icedtea.kodex.ui.reader.parseReaderDefault(initial, "pdf")) }

    // On the session's scope, not this screen's: toggling a setting and going straight back is the
    // ordinary way to use this screen, and a write started here would be cancelled by that very
    // navigation. Failures now say so instead of leaving the switch showing a value nobody stored.
    fun save(key: String, value: kotlinx.serialization.json.JsonElement) {
        session.persistSetting(snackbar) { api.saveUserSetting(baseUrl, apiKey, key, value) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SectionHeader("Series")
        SwitchRow(
            title = "Auto-update on open",
            subtitle = "Check for new chapters when opening a series",
            checked = autoUpdate,
            onCheckedChange = { autoUpdate = it; save("series.autoUpdateOnOpen", JsonPrimitive(it)) },
        )
        ChapterSortRow(current = chapterSort, onSelect = { chapterSort = it; save("series.chapterSort", JsonPrimitive(it)) })

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Reader defaults")
        ReaderDefaultsRows("Comics", comicPrefs) { p ->
            comicPrefs = p
            session.persistSetting(snackbar) { dev.icedtea.kodex.ui.reader.saveReaderDefault(api, baseUrl, apiKey, "comic", p) }
        }
        ReaderDefaultsRows("PDF", pdfPrefs) { p ->
            pdfPrefs = p
            session.persistSetting(snackbar) { dev.icedtea.kodex.ui.reader.saveReaderDefault(api, baseUrl, apiKey, "pdf", p) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Sync to reading devices")
        Text(
            if (syncLibs.isEmpty()) "All libraries sync to KOReader / Kobo." else "${syncLibs.size} librar${if (syncLibs.size == 1) "y" else "ies"} selected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        libraries.forEach { lib ->
            CheckRow(lib.name, lib.id in syncLibs) {
                syncLibs = if (lib.id in syncLibs) syncLibs - lib.id else syncLibs + lib.id
                save("sync.libraries", kotlinx.serialization.json.JsonArray(syncLibs.map { JsonPrimitive(it) }))
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
    }
}

private val READER_MODES = listOf("auto" to "Auto", "paged" to "Paged", "continuous" to "Continuous")
private val READER_DIRS = listOf("ltr" to "Left→Right", "rtl" to "Right→Left")
private val READER_ZOOMS = listOf("height" to "Fit height", "width" to "Fit width", "original" to "Original")

@Composable
private fun ReaderDefaultsRows(label: String, prefs: dev.icedtea.kodex.ui.reader.ReaderPrefs, onChange: (dev.icedtea.kodex.ui.reader.ReaderPrefs) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
    PickerRow("Mode", READER_MODES, prefs.mode) { onChange(prefs.copy(mode = it)) }
    PickerRow("Direction", READER_DIRS, prefs.direction) { onChange(prefs.copy(direction = it)) }
    PickerRow("Zoom", READER_ZOOMS, prefs.zoom) { onChange(prefs.copy(zoom = it)) }
}

@Composable
private fun PickerRow(label: String, options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(options.firstOrNull { it.first == current }?.second ?: current) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val SORT_OPTIONS = listOf(
    "number,desc" to "Chapter number (newest first)",
    "number,asc" to "Chapter number (oldest first)",
    "releaseDate,desc" to "Release date (newest first)",
    "releaseDate,asc" to "Release date (oldest first)",
)

@Composable
private fun ChapterSortRow(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = SORT_OPTIONS.firstOrNull { it.first == current }?.second ?: SORT_OPTIONS.first().second
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Default chapter sort", style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { open = true }) { Text("Change") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SORT_OPTIONS.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

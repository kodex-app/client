package app.kodex.client.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe
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
                load = { val s = server!!; api.userSettings(s.baseUrl, s.apiKey) },
            ) { settings ->
                val s = server ?: return@LoadedContent
                SettingsForm(s.baseUrl, s.apiKey, api, settings)
            }
        }
    }
}

private const val CHAPTER_SORT_DEFAULT = "number,desc"

@Composable
private fun SettingsForm(baseUrl: String, apiKey: String, api: KodexApi, initial: JsonObject) {
    val scope = rememberCoroutineScope()

    var autoUpdate by remember {
        mutableStateOf(initial["series.autoUpdateOnOpen"]?.jsonPrimitive?.booleanOrNull ?: true)
    }
    var chapterSort by remember {
        mutableStateOf(initial["series.chapterSort"]?.jsonPrimitive?.contentOrNull ?: CHAPTER_SORT_DEFAULT)
    }

    fun save(key: String, value: kotlinx.serialization.json.JsonElement) {
        scope.launch { runCatching { api.saveUserSetting(baseUrl, apiKey, key, value) } }
    }

    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        SectionHeader("Series")

        SwitchRow(
            title = "Auto-update on open",
            subtitle = "Check for new chapters when opening a series",
            checked = autoUpdate,
            onCheckedChange = {
                autoUpdate = it
                save("series.autoUpdateOnOpen", JsonPrimitive(it))
            },
        )

        ChapterSortRow(
            current = chapterSort,
            onSelect = {
                chapterSort = it
                save("series.chapterSort", JsonPrimitive(it))
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "Reader defaults and device sync will appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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

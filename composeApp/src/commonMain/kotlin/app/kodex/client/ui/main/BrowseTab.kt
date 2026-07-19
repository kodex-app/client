package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe

/** Browse installed content sources, grouped by language, with a name filter. Tap one to open its feed. */
@Composable
fun BrowseTab(session: SessionManager, api: KodexApi, onOpenSource: (SourceDescriptor) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

    LoadedContent(
        key = server?.id,
        load = { val s = server!!; api.contentSources(s.baseUrl, s.apiKey) },
    ) { sources ->
        if (sources.isEmpty()) {
            EmptyMessage("No content sources installed.\nInstall a plugin on your server to browse.")
        } else {
            SourceList(sources, onOpenSource)
        }
    }
}

@Composable
private fun SourceList(sources: List<SourceDescriptor>, onOpen: (SourceDescriptor) -> Unit) {
    var filter by remember { mutableStateOf("") }

    val groups = remember(sources, filter) {
        val f = filter.trim().lowercase()
        val visible = if (f.isEmpty()) sources else sources.filter { it.displayName.lowercase().contains(f) }
        visible
            .groupBy { it.language }
            .toList()
            .sortedWith(compareBy({ it.first == null }, { it.first ?: "" }))
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Filter sources") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            groups.forEach { (language, list) ->
                item(key = "hdr-${language ?: "multi"}") {
                    Text(
                        languageLabel(language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                }
                items(list, key = { it.id }) { source ->
                    SourceRow(source, onClick = { onOpen(source) })
                    Spacer(Modifier.size(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: SourceDescriptor, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        source.displayName.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                source.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (source.adultContent) {
                Chip("18+")
                Spacer(Modifier.size(6.dp))
            }
            Chip(source.kind)
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Friendly name for a BCP-47 language tag; falls back to the uppercased code, or "Multi-language". */
private fun languageLabel(code: String?): String {
    if (code.isNullOrBlank()) return "Multi-language"
    return LANGUAGE_NAMES[code.lowercase()] ?: code.uppercase()
}

private val LANGUAGE_NAMES = mapOf(
    "en" to "English", "ja" to "Japanese", "zh" to "Chinese", "ko" to "Korean",
    "es" to "Spanish", "fr" to "French", "de" to "German", "ru" to "Russian",
    "pt" to "Portuguese", "it" to "Italian", "id" to "Indonesian", "vi" to "Vietnamese",
    "th" to "Thai", "ar" to "Arabic", "tr" to "Turkish", "pl" to "Polish",
)

package app.kodex.client.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.ktor.http.Url
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
fun BrowseTab(session: SessionManager, api: KodexApi, onOpenSource: (SourceDescriptor, String) -> Unit) {
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
private fun SourceList(sources: List<SourceDescriptor>, onOpen: (SourceDescriptor, String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf<String?>(null) }

    val kinds = remember(sources) { sources.map { it.kind }.distinct().sorted() }
    val langs = remember(sources) {
        sources.mapNotNull { it.language }.distinct().sortedBy { languageLabel(it) }
    }
    val groups = remember(sources, filter, kind, lang) {
        val f = filter.trim().lowercase()
        val visible = sources
            .filter { kind == null || it.kind == kind }
            .filter { lang == null || it.language == lang }
            .filter { f.isEmpty() || it.displayName.lowercase().contains(f) }
        visible
            .groupBy { it.language }
            .toList()
            .sortedWith(compareBy({ it.first == null }, { it.first ?: "" }))
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            placeholder = { Text("Filter sources") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (kinds.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All types") }) }
                items(kinds, key = { it }) { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = if (kind == k) null else k },
                        label = { Text(k.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        if (langs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = lang == null, onClick = { lang = null }, label = { Text("All languages") }) }
                items(langs, key = { it }) { l ->
                    FilterChip(
                        selected = lang == l,
                        onClick = { lang = if (lang == l) null else l },
                        label = { Text(languageLabel(l)) },
                    )
                }
            }
        }
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
                    SourceRow(source, onOpen = { feed -> onOpen(source, feed) })
                    Spacer(Modifier.size(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: SourceDescriptor, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen("popular") }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceAvatar(source)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (source.adultContent) { Chip("18+"); Spacer(Modifier.size(6.dp)) }
                    Chip(source.kind)
                }
            }
            // Direct entry into the source's Latest feed (when supported).
            if (source.supportsLatest) {
                TextButton(onClick = { onOpen("latest") }) { Text("Latest") }
            }
        }
    }
}

/**
 * The source's "logo": its website favicon (via Google's favicon service, matching the web UI), with
 * a coloured initial as the fallback when the source has no website / the favicon fails to load.
 */
@Composable
private fun SourceAvatar(source: SourceDescriptor) {
    val fav = remember(source.website) { faviconUrl(source.website) }
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (fav != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (fav != null) {
            AsyncImage(
                model = fav,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Text(
                source.displayName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A source's favicon URL via Google's favicon service, or null when it has no website. */
private fun faviconUrl(website: String?): String? {
    if (website.isNullOrBlank()) return null
    val host = runCatching { Url(website).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
    return "https://www.google.com/s2/favicons?sz=64&domain=$host"
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

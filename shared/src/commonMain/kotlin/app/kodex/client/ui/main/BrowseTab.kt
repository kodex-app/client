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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe

/** Browse installed content sources — favourites + recents on top, grouped by language, with filters. */
@Composable
fun BrowseTab(
    session: SessionManager,
    api: KodexApi,
    sourcePrefs: app.kodex.client.data.SourcePrefsStore,
    onOpenSource: (SourceDescriptor, String) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()

    LoadedContent(
        retainKey = "sources",
        key = server?.id,
        load = { val s = server!!; api.contentSources(s.baseUrl, s.apiKey) },
    ) { sources ->
        if (sources.isEmpty()) {
            EmptyMessage("No content sources installed.\nInstall a plugin on your server to browse.")
        } else {
            SourceList(
                sources = sources,
                sourcePrefs = sourcePrefs,
                onOpen = { src, feed -> sourcePrefs.recordRecent(src.id); onOpenSource(src, feed) },
            )
        }
    }
}

@Composable
private fun SourceList(
    sources: List<SourceDescriptor>,
    sourcePrefs: app.kodex.client.data.SourcePrefsStore,
    onOpen: (SourceDescriptor, String) -> Unit,
) {
    val favorites by sourcePrefs.favorites.collectAsStateSafe()
    val recents by sourcePrefs.recents.collectAsStateSafe()
    var filter by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<String?>(null) }
    var selectedLangs by remember { mutableStateOf<Set<String>>(emptySet()) } // empty = all languages
    var langMenu by remember { mutableStateOf(false) }

    val byId = remember(sources) { sources.associateBy { it.id } }
    val favoriteSources = remember(sources, favorites) { sources.filter { it.id in favorites } }
    val recentSources = remember(sources, recents) { recents.mapNotNull { byId[it] } }

    val kinds = remember(sources) { sources.map { it.kind }.distinct().sorted() }
    val langs = remember(sources) {
        sources.mapNotNull { it.language }.distinct().sortedBy { languageLabel(it) }
    }
    val groups = remember(sources, filter, kind, selectedLangs) {
        val f = filter.trim().lowercase()
        val visible = sources
            .filter { kind == null || it.kind == kind }
            .filter { selectedLangs.isEmpty() || it.language in selectedLangs }
            .filter { f.isEmpty() || it.displayName.lowercase().contains(f) }
        visible
            .groupBy { it.language }
            .toList()
            .sortedWith(compareBy({ it.first == null }, { it.first ?: "" }))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Filter sources") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
                // Pill + tonal fill: reads as a search affordance rather than a form input, and the
                // hairline box no longer competes with the cards below it.
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            if (langs.size > 1) {
                Spacer(Modifier.size(8.dp))
                Box {
                    BadgedBox(badge = { if (selectedLangs.isNotEmpty()) Badge { Text("${selectedLangs.size}") } }) {
                        IconButton(onClick = { langMenu = true }) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = "Filter by language",
                                tint = if (selectedLangs.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All languages") },
                            onClick = { selectedLangs = emptySet() },
                            leadingIcon = { if (selectedLangs.isEmpty()) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                        langs.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(languageLabel(l)) },
                                onClick = { selectedLangs = if (l in selectedLangs) selectedLangs - l else selectedLangs + l },
                                leadingIcon = { if (l in selectedLangs) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            )
                        }
                    }
                }
            }
        }
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
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            fun sourceItems(list: List<SourceDescriptor>, prefix: String, showLanguage: Boolean = false) {
                items(list, key = { "$prefix-${it.id}" }) { source ->
                    SourceRow(
                        source = source,
                        isFavorite = source.id in favorites,
                        showLanguage = showLanguage,
                        onToggleFavorite = { sourcePrefs.toggleFavorite(source.id) },
                        onOpen = { feed -> onOpen(source, feed) },
                    )
                    Spacer(Modifier.size(10.dp))
                }
            }
            // Favourites and recents are pulled out of the language grouping below, so they're the
            // only rows where the language isn't already stated by the section header — hence the
            // badge here and not there, where it would just repeat the heading on every row.
            if (favoriteSources.isNotEmpty()) {
                item(key = "hdr-fav") { SourceSectionHeader("Favorites", favoriteSources.size) }
                sourceItems(favoriteSources, "fav", showLanguage = true)
            }
            if (recentSources.isNotEmpty()) {
                item(key = "hdr-recent") { SourceSectionHeader("Recently used", recentSources.size) }
                sourceItems(recentSources, "recent", showLanguage = true)
            }
            groups.forEach { (language, list) ->
                item(key = "hdr-${language ?: "multi"}") { SourceSectionHeader(languageLabel(language), list.size) }
                sourceItems(list, "grp")
            }
        }
    }
}

@Composable
private fun SourceSectionHeader(text: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceRow(
    source: SourceDescriptor,
    isFavorite: Boolean,
    /** Set outside the language-grouped sections, where the header doesn't already say it. */
    showLanguage: Boolean = false,
    onToggleFavorite: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(
        onClick = { onOpen("popular") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceAvatar(source)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (source.adultContent) app.kodex.client.ui.catalog.ColorBadge("18+")
                    app.kodex.client.ui.catalog.ColorBadge(source.kind)
                    if (showLanguage) app.kodex.client.ui.catalog.ColorBadge(languageBadge(source.language))
                }
            }
            // Outlined when off, so a glance distinguishes favourites instead of every row showing
            // the same amber star.
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                    tint = if (isFavorite) FavoriteAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
            // Secondary to the row's own tap (which opens Popular), so it stays a quiet text action.
            if (source.supportsLatest) {
                TextButton(onClick = { onOpen("latest") }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Latest", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val FavoriteAmber = Color(0xFFF59E0B)

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

/**
 * Badge-sized language label: the bare tag, uppercased. The full name is right for a section header
 * but too wide for a pill sitting beside the kind badge — "PT" reads fine where "Portuguese" would
 * push the row's title into an ellipsis on a narrow phone.
 */
private fun languageBadge(code: String?): String =
    if (code.isNullOrBlank()) "Multi" else code.uppercase()

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

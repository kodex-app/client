package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.ktor.http.Url
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.contentSources
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.LanguageFilterButton
import dev.icedtea.kodex.ui.SourceAvatar
import dev.icedtea.kodex.ui.SourceCard
import dev.icedtea.kodex.ui.SourceFilterRow
import dev.icedtea.kodex.ui.SourceListContentPadding
import dev.icedtea.kodex.ui.SourceSearchField
import dev.icedtea.kodex.ui.SourceSectionHeader
import dev.icedtea.kodex.ui.catalog.ColorBadge
import dev.icedtea.kodex.ui.languageLabel
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.nav.retain

/** Browse installed content sources — favourites + recents on top, grouped by language, with filters. */
@Composable
fun BrowseTab(
    session: SessionManager,
    api: KodexApi,
    sourcePrefs: dev.icedtea.kodex.data.SourcePrefsStore,
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
    sourcePrefs: dev.icedtea.kodex.data.SourcePrefsStore,
    onOpen: (SourceDescriptor, String) -> Unit,
) {
    val favorites by sourcePrefs.favorites.collectAsStateSafe()
    val recents by sourcePrefs.recents.collectAsStateSafe()
    // Retained for the same reason as the scroll position: opening a source and coming back to an
    // unfiltered list loses the search you narrowed it down with. The language menu below stays a
    // plain `remember` — a popup that was open should not reappear on return.
    var filter by retain("browse:filter") { mutableStateOf("") }
    var kind by retain("browse:kind") { mutableStateOf<String?>(null) }
    // Which language groups are hidden — server-persisted with the other Browse prefs, so the web UI
    // and this screen always agree on what's filtered out. Empty = every language shown.
    val hiddenLangs by sourcePrefs.hiddenLanguages.collectAsStateSafe()

    val byId = remember(sources) { sources.associateBy { it.id } }
    val favoriteSources = remember(sources, favorites) { sources.filter { it.id in favorites } }
    val recentSources = remember(sources, recents) { recents.mapNotNull { byId[it] } }

    val kinds = remember(sources) { sources.map { it.kind }.distinct().sorted() }
    // One entry per language present, the multi-language bucket included and pinned last, so every
    // group the list can render is toggleable (web parity).
    val langs = remember(sources) {
        sources.groupingBy { langKey(it.language) }.eachCount().entries
            .sortedWith(compareBy({ it.key.isEmpty() }, { languageLabel(it.key).lowercase() }))
            .map { it.key to it.value }
    }
    val groups = remember(sources, filter, kind, hiddenLangs) {
        val f = filter.trim().lowercase()
        val visible = sources
            .filter { kind == null || it.kind == kind }
            .filter { langKey(it.language) !in hiddenLangs }
            .filter { f.isEmpty() || it.displayName.lowercase().contains(f) }
        visible
            .groupBy { langKey(it.language) }
            .toList()
            .sortedWith(compareBy({ it.first.isEmpty() }, { languageLabel(it.first) }))
    }

    Column(Modifier.fillMaxSize()) {
        SourceSearchField(filter, onValueChange = { filter = it }, placeholder = "Filter sources")
        SourceFilterRow {
            item {
                LanguageFilterButton(
                    languages = langs,
                    hidden = hiddenLangs,
                    onToggle = sourcePrefs::toggleHiddenLanguage,
                    onSetHidden = sourcePrefs::setHiddenLanguages,
                )
            }
            if (kinds.size > 1) {
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
        // Retained: opening a source unmounts this list, and without it the long grouped list of
        // sources came back scrolled to the top. See nav/RetainedState.kt.
        val listState = retain("browse:scroll") { LazyListState() }
        LazyColumn(
            state = listState,
            contentPadding = SourceListContentPadding,
        ) {
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
                stickyHeader(key = "hdr-fav") { SourceSectionHeader("Favorites", favoriteSources.size) }
                sourceItems(favoriteSources, "fav", showLanguage = true)
            }
            if (recentSources.isNotEmpty()) {
                stickyHeader(key = "hdr-recent") { SourceSectionHeader("Recently used", recentSources.size) }
                sourceItems(recentSources, "recent", showLanguage = true)
            }
            groups.forEach { (language, list) ->
                stickyHeader(key = "hdr-${language.ifEmpty { "multi" }}") { SourceSectionHeader(languageLabel(language), list.size) }
                sourceItems(list, "grp")
            }
        }
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
    val fav = remember(source.website) { faviconUrl(source.website) }
    SourceCard(
        title = source.displayName,
        avatar = { SourceAvatar(fav, source.displayName) },
        onClick = { onOpen("popular") },
        badges = {
            if (source.adultContent) ColorBadge("18+")
            ColorBadge(source.kind)
            if (showLanguage) ColorBadge(languageBadge(source.language))
        },
    ) {
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

private val FavoriteAmber = Color(0xFFF59E0B)

/** A source's favicon URL via Google's favicon service, or null when it has no website. */
private fun faviconUrl(website: String?): String? {
    if (website.isNullOrBlank()) return null
    val host = runCatching { Url(website).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
    return "https://www.google.com/s2/favicons?sz=64&domain=$host"
}

/**
 * Badge-sized language label: the bare tag, uppercased. The full name is right for a section header
 * but too wide for a pill sitting beside the kind badge — "PT" reads fine where "Portuguese" would
 * push the row's title into an ellipsis on a narrow phone.
 */
private fun languageBadge(code: String?): String =
    if (code.isNullOrBlank()) "Multi" else code.uppercase()

/** Grouping/filter key for a source's language tag — null or blank collapses to "" (web parity). */
private fun langKey(code: String?): String = if (code.isNullOrBlank()) "" else code

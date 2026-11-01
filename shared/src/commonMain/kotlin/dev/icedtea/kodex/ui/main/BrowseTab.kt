package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import dev.icedtea.kodex.ui.SourceListInset
import dev.icedtea.kodex.ui.SourceFilterRow
import dev.icedtea.kodex.ui.SourceListContentPadding
import dev.icedtea.kodex.ui.SourceSearchField
import dev.icedtea.kodex.ui.SourceSectionHeader
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
    var otherOpen by retain("browse:otherOpen") { mutableStateOf(false) }
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
                items(kinds, key = { it }) { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = if (kind == k) null else k },
                        label = { Text(prettyKind(k)) },
                        leadingIcon = {
                            Icon(kindIcon(k), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        },
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
            // Favourites and recents as one scrolling strip of logo tiles: they're for jumping straight
            // back in, and as full cards they pushed the actual catalogue a screen down. Favourites lead
            // (marked with a star); recents follow without repeating one already shown. Hidden while
            // filtering by name, where the grouped list below is the answer.
            val quick = (favoriteSources + recentSources).distinctBy { it.id }
            if (quick.isNotEmpty() && filter.isBlank()) {
                item(key = "quick") {
                    Column {
                        SourceSectionHeader(if (favoriteSources.isEmpty()) "Recently used" else "Quick access", quick.size)
                        LazyRow(
                            // Bleeds to the screen edges so tiles scroll out from under the inset.
                            modifier = Modifier.fillMaxWidth().layout { m, c ->
                                val extra = SourceListInset.roundToPx() * 2
                                val p = m.measure(c.copy(maxWidth = c.maxWidth + extra))
                                layout(c.maxWidth, p.height) { p.place(0, 0) }
                            },
                            contentPadding = PaddingValues(horizontal = SourceListInset),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(quick, key = { "quick-${it.id}" }) { source ->
                                QuickSourceTile(
                                    source = source,
                                    isFavorite = source.id in favorites,
                                    onOpen = { onOpen(source, "popular") },
                                )
                            }
                        }
                    }
                }
            }
            // Each language is one grouped card with hairline-separated rows. A card per source gave a
            // language with a single source (most of them) a band plus a whole card — five languages
            // filled the screen.
            // A language with a single source doesn't earn a band of its own: multi-language sources
            // such as MangaDex register once per language, and forty one-row sections buried the rest.
            // Those fold into one trailing group whose rows name their language instead.
            val (multi, singles) = groups.partition { it.second.size > 1 }
            val sections = multi.map { (lang, list) -> Triple("hdr-${lang.ifEmpty { "multi" }}", languageLabel(lang), list) } +
                listOfNotNull(
                    singles.takeIf { it.isNotEmpty() }?.let { s ->
                        Triple("hdr-other", if (multi.isEmpty()) "Sources" else "Other languages", s.flatMap { it.second })
                    },
                )
            sections.forEach { (key, label, list) ->
                val folded = key == "hdr-other" && multi.isNotEmpty()
                val showLanguage = key == "hdr-other"
                stickyHeader(key = key) {
                    // The folded tail is mostly one multi-language source repeated, so it starts closed;
                    // its header opens it. Every other section stays open, as before.
                    if (folded) {
                        SourceSectionHeader(label, list.size, expanded = otherOpen, onToggle = { otherOpen = !otherOpen })
                    } else {
                        SourceSectionHeader(label, list.size)
                    }
                }
                if (folded && !otherOpen) return@forEach
                itemsIndexed(list, key = { _, it -> "grp-${it.id}" }) { index, source ->
                    SourceRow(
                        source = source,
                        isFavorite = source.id in favorites,
                        showLanguage = showLanguage,
                        first = index == 0,
                        last = index == list.lastIndex,
                        onToggleFavorite = { sourcePrefs.toggleFavorite(source.id) },
                        onOpen = { feed -> onOpen(source, feed) },
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No sources match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

/**
 * One source inside its language's grouped card: logo, name, a quiet "kind · 18+" caption, then the
 * favourite star and the Latest shortcut. [first]/[last] round the group's outer corners only.
 */
@Composable
private fun SourceRow(
    source: SourceDescriptor,
    isFavorite: Boolean,
    /** In the folded "Other languages" group, where no header says which language a row is. */
    showLanguage: Boolean,
    first: Boolean,
    last: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val fav = remember(source.website) { faviconUrl(source.website) }
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = if (last) 4.dp else 0.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Tapping the row opens Popular, the feed every source has.
            .clickable { onOpen("popular") },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceAvatar(fav, source.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        kindIcon(source.kind), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOfNotNull(
                            prettyKind(source.kind),
                            languageLabel(langKey(source.language)).takeIf { showLanguage },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Still flagged — it matters — but as a small mark, not the loudest thing on the row.
                    if (source.adultContent) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "18+",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
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
        if (!last) {
            HorizontalDivider(
                Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** A quick-access tile: the source's logo on a card, its name under it, language tag as a caption. */
@Composable
private fun QuickSourceTile(source: SourceDescriptor, isFavorite: Boolean, onOpen: () -> Unit) {
    val fav = remember(source.website) { faviconUrl(source.website) }
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            SourceAvatar(fav, source.displayName, size = 60.dp)
            if (isFavorite) {
                Icon(
                    Icons.Filled.Star, contentDescription = "Favorite", tint = FavoriteAmber,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            source.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            languageBadge(source.language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun prettyKind(kind: String): String =
    kind.ifBlank { "Comic" }.lowercase().replaceFirstChar { it.uppercase() }

private fun kindIcon(kind: String?): ImageVector = when (kind?.uppercase()) {
    "BOOK", "NOVEL" -> Icons.Outlined.AutoStories
    else -> Icons.AutoMirrored.Outlined.MenuBook
}

private val FavoriteAmber = Color(0xFFF59E0B)

/** A source's favicon URL via Google's favicon service, or null when it has no website. */
internal fun faviconUrl(website: String?): String? {
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

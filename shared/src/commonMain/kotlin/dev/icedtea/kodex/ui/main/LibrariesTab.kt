package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.data.loadLibraryNavPrefs
import dev.icedtea.kodex.data.orderedBy
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.libraries
import dev.icedtea.kodex.network.libraryPreview
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.isoEpochMillis
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.catalog.CoverThumb
import dev.icedtea.kodex.ui.catalog.seriesCoverUrl
import kotlinx.coroutines.launch

/** Gap between mosaic cells — a seam of card colour, enough to read as separate covers. */
private val MosaicGap = 2.dp

/**
 * Lists the server's libraries as cover tiles; tapping one drills into its series grid. [onArrange]
 * opens the manage screen, where the order and the hidden flags this list obeys are edited.
 */
@Composable
fun LibrariesTab(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    onOpenLibrary: (LibraryDto) -> Unit,
    onArrange: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    // Retained: drilling into a library unmounts the tab, so a grid state remembered here would put
    // you back at the top of the tiles on return. See nav/RetainedState.kt.
    val gridState = retain("libraries:grid") { LazyGridState() }
    val storedSort by appSettings.librariesSort.collectAsStateSafe()
    val sort = remember(storedSort) { LibrariesSort.parse(storedSort) }

    // Loaded together so the list is ordered and filtered from its first frame — fetching the prefs
    // separately would show the server order for a beat and then reshuffle.
    LoadedContent(
        retainKey = "libraries",
        key = server?.id,
        load = {
            val s = server!!
            val prefs = loadLibraryNavPrefs(api, s.baseUrl, s.apiKey)
            api.libraries(s.baseUrl, s.apiKey).orderedBy(prefs).filterNot { prefs.isHidden(it.id) }
        },
    ) { libraries ->
        val current = server
        // Previews fill in after the list so the tiles appear immediately, and each library lands on
        // its own — one slow or failing library leaves its tile on the initial plate instead of holding
        // the whole screen at a spinner.
        var previews by remember(libraries) { mutableStateOf<Map<String, LibraryPreview>>(emptyMap()) }
        LaunchedEffect(libraries, current?.id) {
            val s = current ?: return@LaunchedEffect
            libraries.forEach { lib ->
                launch {
                    val page = runCatching { api.libraryPreview(s.baseUrl, s.apiKey, lib.id) }.getOrNull()
                        ?: return@launch
                    previews += lib.id to LibraryPreview(
                        total = page.totalElements,
                        covers = page.content.map { seriesCoverUrl(s.baseUrl, it) },
                    )
                }
            }
        }

        if (libraries.isEmpty()) {
            EmptyMessage("No libraries yet.\nCreate one on your server to see it here.")
        } else {
            // Retained alongside the list itself: coming back from a library to a cleared search box
            // showed a different set of tiles than the one you left, which reads as the filter having
            // been thrown away rather than the screen having been restored.
            var query by retain("libraries:query") { mutableStateOf("") }
            var filter by retain("libraries:filter") { mutableStateOf(LibrariesFilter()) }
            val kinds = remember(libraries) {
                libraries.mapNotNull { it.mediaKind?.takeIf(String::isNotBlank) }.distinct().sorted()
            }
            val types = remember(libraries) { libraries.map(::libraryType).distinct().sorted() }
            val shown = remember(libraries, query, filter, sort, previews) {
                libraries
                    .filter { it.name.contains(query.trim(), ignoreCase = true) && filter.accepts(it, previews[it.id]) }
                    .sortedFor(sort, previews)
            }

            Column(Modifier.fillMaxSize()) {
                LibrariesToolbar(
                    query = query,
                    onQuery = { query = it },
                    filter = filter,
                    onFilter = { filter = it },
                    kinds = kinds,
                    types = types,
                    sort = sort,
                    onSort = { appSettings.setLibrariesSort(it.store()) },
                    onArrange = onArrange,
                )
                if (shown.isEmpty()) {
                    // The toolbar stays put above this: the way out of an over-narrow filter is the
                    // control that caused it, so hiding it with the grid would strand the screen.
                    Box(Modifier.weight(1f)) {
                        EmptyMessage("No libraries match.\nTry a different name or clear the filters.")
                    }
                } else {
                    // Adaptive rather than two fixed columns: a phone lands on two, a tablet widens into
                    // more instead of stretching two tiles across the screen.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(168.dp),
                        state = gridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown, key = { it.id }) { library ->
                            LibraryTile(
                                library = library,
                                preview = previews[library.id],
                                apiKey = current?.apiKey.orEmpty(),
                                onClick = { onOpenLibrary(library) },
                                // Sorting by size reorders as the counts land, and a tile teleporting
                                // mid-scan reads as a glitch; animating the move shows it for what it is.
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a tile draws once its one request lands: the mosaic's covers and the library's total. */
private data class LibraryPreview(val total: Long, val covers: List<String>)

/**
 * What the tiles can be ordered by. [CUSTOM] is the order the server's own nav preferences give — the
 * one this screen has always opened in, and the one the sidebar on the web uses — so it stays the
 * default and the others are opt-in.
 */
private enum class LibrariesSortKey(val label: String, val defaultAsc: Boolean) {
    CUSTOM("Custom order", true),
    NAME("Name", true),
    SIZE("Series count", false),
    REFRESHED("Last refreshed", false),
}

/** A sort key and its direction, as persisted in [AppSettings.librariesSort]. */
private data class LibrariesSort(val key: LibrariesSortKey, val asc: Boolean) {
    fun store(): String = "${key.name},${if (asc) "asc" else "desc"}"

    companion object {
        val Default = LibrariesSort(LibrariesSortKey.CUSTOM, LibrariesSortKey.CUSTOM.defaultAsc)

        /** Anything unparseable — an empty setting, or one written by a build with other keys — is the default. */
        fun parse(stored: String): LibrariesSort {
            val parts = stored.split(",")
            val key = LibrariesSortKey.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return Default
            return LibrariesSort(key, parts.getOrNull(1) != "desc")
        }
    }
}

/**
 * Orders the tiles. Kotlin's sorts are stable, so libraries that tie — same name, same count, or a
 * value that isn't known yet — keep the server's custom order between them rather than shuffling.
 *
 * A missing value (a preview still in flight, a library never refreshed) sorts last in *both*
 * directions rather than being treated as the smallest: flipping to "most series" should not fill the
 * top of the screen with the libraries that simply haven't answered yet.
 */
private fun List<LibraryDto>.sortedFor(
    sort: LibrariesSort,
    previews: Map<String, LibraryPreview>,
): List<LibraryDto> {
    fun orderBy(value: (LibraryDto) -> Comparable<*>?): List<LibraryDto> {
        val known = compareBy<LibraryDto> { value(it) == null }
        val direction = if (sort.asc) compareBy(value) else compareByDescending(value)
        return sortedWith(known.then(direction))
    }
    return when (sort.key) {
        LibrariesSortKey.CUSTOM -> if (sort.asc) this else reversed()
        LibrariesSortKey.NAME -> orderBy { it.name.lowercase() }
        LibrariesSortKey.SIZE -> orderBy { previews[it.id]?.total }
        LibrariesSortKey.REFRESHED -> orderBy { isoEpochMillis(it.lastRefreshedDate) }
    }
}

/**
 * Which libraries are shown. Empty [types] and a null [kind] mean "all" rather than "none", so the
 * unset state needs no special case at the call site.
 *
 * Session-local by design, unlike the sort: a filter that survived a restart would open the tab with
 * libraries silently missing, and the one thing worse than not finding a library is not being told it
 * was hidden.
 */
private data class LibrariesFilter(
    val types: Set<String> = emptySet(),
    val kind: String? = null,
    val hideEmpty: Boolean = false,
) {
    fun accepts(library: LibraryDto, preview: LibraryPreview?): Boolean {
        if (types.isNotEmpty() && libraryType(library) !in types) return false
        if (kind != null && library.mediaKind != kind) return false
        // Only hide what is *known* to be empty: a preview still in flight isn't evidence of anything,
        // and a library blinking out of the grid a second after you opened the tab looks like a bug.
        if (hideEmpty && preview != null && preview.total == 0L) return false
        return true
    }
}

private fun libraryType(library: LibraryDto): String = if (library.isWeb) "WEB" else "LOCAL"

/**
 * Search, sort and filter over the tiles: a pill-shaped name filter with the way into Arrange beside
 * it, then a single chip row. Sort leads the row (it opens a menu of keys; the chip names the active
 * one and its direction), followed by every filter as a one-tap toggle — type, media kind, hide
 * empty. These used to sit behind a filter icon and a bare arrow icon, which said neither what was
 * active nor what was on offer.
 */
@Composable
private fun LibrariesToolbar(
    query: String,
    onQuery: (String) -> Unit,
    filter: LibrariesFilter,
    onFilter: (LibrariesFilter) -> Unit,
    kinds: List<String>,
    types: List<String>,
    sort: LibrariesSort,
    onSort: (LibrariesSort) -> Unit,
    onArrange: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Filter libraries") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                    }
                }
            },
            singleLine = true,
            // Pill + tonal fill, matching Browse's source filter.
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        // Reordering and hiding are lasting decisions about the list, not a view you flip in and out of,
        // so they keep their own screen — but the way in belongs here, next to the list they rearrange,
        // rather than only under More.
        IconButton(onClick = onArrange) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = "Arrange libraries",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "sort") {
            Box {
                FilterChip(
                    // Highlighted only once moved off the default, so a glance says the order is custom.
                    selected = sort != LibrariesSort.Default,
                    onClick = { sortMenu = true },
                    label = { Text(sort.key.label) },
                    leadingIcon = {
                        Icon(
                            if (sort.asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (sort.asc) "Ascending" else "Descending",
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    // The active key carries the direction arrow and tapping it flips; tapping another
                    // switches to it in whichever direction suits it.
                    LibrariesSortKey.entries.forEach { key ->
                        val selected = key == sort.key
                        DropdownMenuItem(
                            text = {
                                Text(
                                    key.label,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    if (selected) {
                                        Icon(
                                            if (sort.asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                            contentDescription = if (sort.asc) "Ascending" else "Descending",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                sortMenu = false
                                onSort(if (selected) sort.copy(asc = !sort.asc) else LibrariesSort(key, key.defaultAsc))
                            },
                        )
                    }
                }
            }
        }
        item(key = "divider") {
            Box(Modifier.size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        // Types and media kinds come from the libraries themselves rather than a fixed list — a server
        // that only holds comics shouldn't offer a "Book" filter — and a single value needs no chip.
        if (types.size > 1) {
            items(types, key = { "type:$it" }) { t ->
                val on = t in filter.types
                FilterChip(
                    selected = on,
                    onClick = { onFilter(filter.copy(types = if (on) filter.types - t else filter.types + t)) },
                    label = { Text(if (t == "WEB") "Web" else "Local") },
                    leadingIcon = {
                        Icon(typeIcon(t == "WEB"), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
            }
        }
        if (kinds.size > 1) {
            items(kinds, key = { "kind:$it" }) { k ->
                FilterChip(
                    selected = filter.kind == k,
                    onClick = { onFilter(filter.copy(kind = if (filter.kind == k) null else k)) },
                    label = { Text(prettyKind(k)) },
                    leadingIcon = {
                        Icon(kindIcon(k), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
            }
        }
        item(key = "hideEmpty") {
            FilterChip(
                selected = filter.hideEmpty,
                onClick = { onFilter(filter.copy(hideEmpty = !filter.hideEmpty)) },
                label = { Text("Hide empty") },
                leadingIcon = if (filter.hideEmpty) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

private fun prettyKind(kind: String): String = kind.lowercase().replaceFirstChar { it.uppercase() }

private fun typeIcon(web: Boolean): ImageVector = if (web) Icons.Outlined.Public else Icons.Outlined.Folder

private fun kindIcon(kind: String?): ImageVector = when (kind?.uppercase()) {
    "COMIC", "MANGA" -> Icons.AutoMirrored.Outlined.MenuBook
    "BOOK", "NOVEL" -> Icons.Outlined.AutoStories
    else -> Icons.Outlined.CollectionsBookmark
}

/**
 * One library: a mosaic of its most recently touched covers filling the card, with the name and a
 * one-line caption (type icon · kind · series count) on a scrim along the bottom.
 *
 * The caption used to be a separate block under the art with two saturated badges, which made each
 * tile as tall again as its artwork and gave the loudest colour on screen to the least useful fact.
 */
@Composable
private fun LibraryTile(
    library: LibraryDto,
    preview: LibraryPreview?,
    apiKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
            CoverMosaic(preview?.covers, apiKey, library.mediaKind)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f).background(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f)),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        typeIcon(library.isWeb),
                        contentDescription = if (library.isWeb) "Web library" else "Local library",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    // The count joins once its request lands; the kind alone holds the line until then.
                    val caption = listOfNotNull(
                        library.mediaKind?.takeIf { it.isNotBlank() }?.let(::prettyKind),
                        seriesCountLabel(preview?.total),
                    ).joinToString(" · ")
                    Text(
                        caption.ifBlank { if (library.isWeb) "Web" else "Local" },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Up to four covers arranged to fill a square: one fills it, two split it, three give the newest the
 * left half, four make the 2×2. Fewer covers than cells would leave holes, so each count gets a layout
 * that has none.
 *
 * [covers] is `null` until the library answers, which draws a blank block rather than the initial —
 * otherwise every tile flashes its letter for as long as the request takes and then swaps.
 */
@Composable
private fun CoverMosaic(covers: List<String>?, apiKey: String, kind: String?) {
    if (covers == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (covers.size) {
            0 -> EmptyPlate(kind)
            1 -> MosaicCell(covers[0], apiKey, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                MosaicCell(covers[0], apiKey, Modifier.weight(1f).fillMaxHeight())
                MosaicCell(covers[1], apiKey, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                MosaicCell(covers[0], apiKey, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
                    MosaicCell(covers[1], apiKey, Modifier.weight(1f).fillMaxWidth())
                    MosaicCell(covers[2], apiKey, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                        MosaicCell(covers[row * 2], apiKey, Modifier.weight(1f).fillMaxHeight())
                        MosaicCell(covers[row * 2 + 1], apiKey, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** One cover of the mosaic, on a tonal block that stands in while it loads or if it never arrives. */
@Composable
private fun MosaicCell(url: String, apiKey: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        CoverThumb(url, apiKey, Modifier.fillMaxSize())
    }
}

/**
 * For a library with no covers to show: a tonal gradient with its media kind's icon. The name is on
 * the scrim already, so repeating its initial here said nothing.
 */
@Composable
private fun EmptyPlate(kind: String?) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            kindIcon(kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(56.dp).padding(bottom = 8.dp),
        )
    }
}

/** `null` while the count is still loading or if it failed — the tile then shows no pill. */
private fun seriesCountLabel(count: Long?): String? = when {
    count == null || count < 0 -> null
    count == 1L -> "1 series"
    else -> "$count series"
}

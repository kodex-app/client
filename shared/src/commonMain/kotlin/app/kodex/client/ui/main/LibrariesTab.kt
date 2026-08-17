package app.kodex.client.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.data.loadLibraryNavPrefs
import app.kodex.client.data.orderedBy
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.isoEpochMillis
import app.kodex.client.ui.catalog.ColorBadge
import app.kodex.client.ui.catalog.CoverThumb
import app.kodex.client.ui.catalog.seriesCoverUrl
import kotlinx.coroutines.launch

/** Gap between mosaic cells — a seam of card colour, enough to read as separate covers. */
private val MosaicGap = 2.dp

/** Lists the server's libraries as cover tiles; tapping one drills into its series grid. */
@Composable
fun LibrariesTab(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    onOpenLibrary: (LibraryDto) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
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
            var query by remember(libraries) { mutableStateOf("") }
            var filter by remember(libraries) { mutableStateOf(LibrariesFilter()) }
            val kinds = remember(libraries) {
                libraries.mapNotNull { it.mediaKind?.takeIf(String::isNotBlank) }.distinct().sorted()
            }
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
                    sort = sort,
                    onSort = { appSettings.setLibrariesSort(it.store()) },
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
    /** What the badge on the filter button counts — [kind] is left out, it has its own visible chip. */
    val activeCount: Int get() = types.size + if (hideEmpty) 1 else 0

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
 * Search, filter and sort over the tiles, following the source list in Browse: a pill-shaped name
 * filter, a dropdown for the dimension with few values worth multi-selecting, and a chip row for the
 * one that reads better spelled out.
 *
 * The sort button carries only its direction arrow — the key it applies to is named in its menu. At
 * this width, a button wide enough to spell out "Series count" would squeeze the search field.
 */
@Composable
private fun LibrariesToolbar(
    query: String,
    onQuery: (String) -> Unit,
    filter: LibrariesFilter,
    onFilter: (LibrariesFilter) -> Unit,
    kinds: List<String>,
    sort: LibrariesSort,
    onSort: (LibrariesSort) -> Unit,
) {
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
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
        Box {
            BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                IconButton(onClick = { filterMenu = true }) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter libraries",
                        tint = if (filter.activeCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                DropdownMenuItem(
                    text = { Text("All libraries") },
                    onClick = { onFilter(filter.copy(types = emptySet())) },
                    leadingIcon = { if (filter.types.isEmpty()) CheckMark() },
                )
                listOf("LOCAL" to "Local", "WEB" to "Web").forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            val next = if (value in filter.types) filter.types - value else filter.types + value
                            onFilter(filter.copy(types = next))
                        },
                        leadingIcon = { if (value in filter.types) CheckMark() },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Hide empty") },
                    onClick = { onFilter(filter.copy(hideEmpty = !filter.hideEmpty)) },
                    leadingIcon = { if (filter.hideEmpty) CheckMark() },
                )
            }
        }
        Box {
            IconButton(onClick = { sortMenu = true }) {
                Icon(
                    if (sort.asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = "Sort: ${sort.key.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                // Follows the library screen's sort sheet: the active key carries the direction arrow and
                // tapping it flips, tapping another switches to it in whichever direction suits it.
                LibrariesSortKey.entries.forEach { key ->
                    val selected = key == sort.key
                    DropdownMenuItem(
                        text = {
                            Text(
                                key.label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
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

    // Media kinds come from the libraries themselves rather than a fixed list — a server that only holds
    // comics shouldn't offer a "Book" filter — and one kind needs no chips at all.
    if (kinds.size > 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filter.kind == null,
                    onClick = { onFilter(filter.copy(kind = null)) },
                    label = { Text("All types") },
                )
            }
            items(kinds, key = { it }) { k ->
                FilterChip(
                    selected = filter.kind == k,
                    onClick = { onFilter(filter.copy(kind = if (filter.kind == k) null else k)) },
                    label = { Text(k.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

/** The tick a selected menu row carries. */
@Composable
private fun CheckMark() {
    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

/**
 * One library: a mosaic of its most recently touched covers with the series count over it, then the
 * name and what kind of library it is.
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverMosaic(preview?.covers, apiKey, library.name)
            // Only WEB is badged. LOCAL sat on nearly every row, so it read as decoration rather than
            // information; marking the exception is what makes the badge worth looking at. Both ride on
            // the artwork so the strip below belongs to the name alone.
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (library.isWeb) ColorBadge("WEB")
                library.mediaKind?.takeIf { it.isNotBlank() }?.let { ColorBadge(it) }
            }
            seriesCountLabel(preview?.total)?.let { label ->
                // Over artwork, not beside it: the count is the one number worth reading at a glance,
                // and the mosaic is the only surface with room to spare.
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
        // The name gets the strip to itself, at title weight: it is what you are actually picking
        // between, and the mosaic already says everything the badges and the count used to say here.
        Text(
            library.name,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            // Two lines either way, so tiles in a row keep the same height whatever their names do.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun CoverMosaic(covers: List<String>?, apiKey: String, name: String) {
    if (covers == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (covers.size) {
            0 -> InitialPlate(name)
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

/** The library's initial, for a library with no covers to show. */
@Composable
private fun InitialPlate(name: String) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/** `null` while the count is still loading or if it failed — the tile then shows no pill. */
private fun seriesCountLabel(count: Long?): String? = when {
    count == null || count < 0 -> null
    count == 1L -> "1 series"
    else -> "$count series"
}

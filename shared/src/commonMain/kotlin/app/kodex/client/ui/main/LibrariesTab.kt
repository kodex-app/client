package app.kodex.client.ui.main

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import app.kodex.client.data.loadLibraryNavPrefs
import app.kodex.client.data.orderedBy
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.catalog.ColorBadge
import app.kodex.client.ui.catalog.CoverThumb
import app.kodex.client.ui.catalog.seriesCoverUrl
import kotlinx.coroutines.launch

/** Gap between mosaic cells — a seam of card colour, enough to read as separate covers. */
private val MosaicGap = 2.dp

/** Lists the server's libraries as cover tiles; tapping one drills into its series grid. */
@Composable
fun LibrariesTab(session: SessionManager, api: KodexApi, onOpenLibrary: (LibraryDto) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

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
            // Adaptive rather than two fixed columns: a phone lands on two, a tablet widens into
            // more instead of stretching two tiles across the screen.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(libraries, key = { it.id }) { library ->
                    LibraryTile(
                        library = library,
                        preview = previews[library.id],
                        apiKey = current?.apiKey.orEmpty(),
                        onClick = { onOpenLibrary(library) },
                    )
                }
            }
        }
    }
}

/** What a tile draws once its one request lands: the mosaic's covers and the library's total. */
private data class LibraryPreview(val total: Long, val covers: List<String>)

/**
 * One library: a mosaic of its most recently touched covers with the series count over it, then the
 * name and what kind of library it is.
 */
@Composable
private fun LibraryTile(library: LibraryDto, preview: LibraryPreview?, apiKey: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverMosaic(preview?.covers, apiKey, library.name)
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
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Text(
                library.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // Two lines either way, so tiles in a row keep the same height whatever their names do.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            // Only WEB is badged. LOCAL sat on nearly every row, so it read as decoration rather than
            // information; marking the exception is what makes the badge worth looking at. The row
            // still reserves its height so a library with no badges doesn't come up short.
            Row(
                Modifier.heightIn(min = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (library.isWeb) ColorBadge("WEB")
                library.mediaKind?.takeIf { it.isNotBlank() }?.let { ColorBadge(it) }
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

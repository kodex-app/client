package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Lists the server's libraries; tapping one drills into its series grid. */
@Composable
fun LibrariesTab(session: SessionManager, api: KodexApi, onOpenLibrary: (LibraryDto) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

    // Loaded together so the list is ordered and filtered from its first frame — fetching the prefs
    // separately would show the server order for a beat and then reshuffle.
    LoadedContent(
        key = server?.id,
        load = {
            val s = server!!
            val prefs = loadLibraryNavPrefs(api, s.baseUrl, s.apiKey)
            api.libraries(s.baseUrl, s.apiKey).orderedBy(prefs).filterNot { prefs.isHidden(it.id) }
        },
    ) { libraries ->
        // Counts fill in after the list so the rows appear immediately; a failure just leaves that
        // row without a subtitle rather than holding up the screen.
        var counts by remember(libraries) { mutableStateOf<Map<String, Long>>(emptyMap()) }
        LaunchedEffect(libraries, server?.id) {
            val s = server ?: return@LaunchedEffect
            counts = libraries.associate { lib ->
                lib.id to runCatching { api.seriesCountInLibrary(s.baseUrl, s.apiKey, lib.id) }.getOrDefault(-1L)
            }
        }

        if (libraries.isEmpty()) {
            EmptyMessage("No libraries yet.\nCreate one on your server to see it here.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(libraries, key = { it.id }) { library ->
                    LibraryRow(library, counts[library.id], onClick = { onOpenLibrary(library) })
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(library: LibraryDto, seriesCount: Long?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(
                        library.name.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    library.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Left out entirely while the count is loading, so the row doesn't flash a placeholder.
                seriesCountLabel(seriesCount)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            // Colour-coded, sharing the app-wide badge palette — this row used to draw its own flat
            // surfaceVariant chip, which is why WEB/LOCAL/COMIC all read as the same grey.
            ColorBadge(if (library.isWeb) "WEB" else "LOCAL")
            library.mediaKind?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(6.dp))
                ColorBadge(it)
            }
        }
    }
}

/** `null` while the count is still loading or if it failed — the caller then shows no subtitle. */
private fun seriesCountLabel(count: Long?): String? = when {
    count == null || count < 0 -> null
    count == 1L -> "1 series"
    else -> "$count series"
}

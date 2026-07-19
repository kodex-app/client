package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe

/** Lists the server's libraries; tapping one drills into its series grid. */
@Composable
fun LibrariesTab(session: SessionManager, api: KodexApi, onOpenLibrary: (LibraryDto) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

    LoadedContent(
        key = server?.id,
        load = { val s = server!!; api.libraries(s.baseUrl, s.apiKey) },
    ) { libraries ->
        if (libraries.isEmpty()) {
            EmptyMessage("No libraries yet.\nCreate one on your server to see it here.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(libraries, key = { it.id }) { library ->
                    LibraryRow(library, onClick = { onOpenLibrary(library) })
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(library: LibraryDto, onClick: () -> Unit) {
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
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    library.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Chip(if (library.isWeb) "WEB" else "LOCAL")
            library.mediaKind?.let {
                Spacer(Modifier.size(6.dp))
                Chip(it)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

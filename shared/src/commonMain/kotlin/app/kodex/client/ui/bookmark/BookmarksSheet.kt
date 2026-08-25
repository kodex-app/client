package app.kodex.client.ui.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import app.kodex.client.ui.InlineLoadError
import app.kodex.client.ui.friendlyMessage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.ui.sheetMaxHeight
import kotlinx.coroutines.launch

/** A single row rendered in the bookmarks sheet. */
data class BookmarkRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val onOpen: () -> Unit,
    val onDelete: (suspend () -> Unit)?,
)

/**
 * A bottom sheet listing reading bookmarks. [load] fetches the rows (re-run after a delete); tapping a
 * row opens it, the trash icon removes it. Shared by Book detail (per-book) and Series detail (series-wide).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    load: suspend () -> List<BookmarkRow>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rows by remember { mutableStateOf<List<BookmarkRow>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var loadError by remember { mutableStateOf<String?>(null) }
    // An empty fallback here claimed "No bookmarks yet." whenever the fetch failed — the one wrong
    // answer that reads as reassuring, since it says your bookmarks are gone rather than unreachable.
    LaunchedEffect(reload) {
        runCatching { load() }.fold(
            onSuccess = { rows = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
    }

    ModalBottomSheet(modifier = Modifier.heightIn(max = sheetMaxHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (val list = rows) {
                null -> if (loadError != null) InlineLoadError(loadError!!) { reload++ }
                    else Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                else -> if (list.isEmpty()) {
                    Text("No bookmarks yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    list.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().clickable { row.onOpen() }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                if (!row.subtitle.isNullOrBlank()) {
                                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            val del = row.onDelete
                            if (del != null) {
                                IconButton(onClick = { scope.launch { runCatching { del() }; reload++ } }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        }
    }
}

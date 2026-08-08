package app.kodex.client.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * One entry in a series' list — a book, a stored chapter, or a chapter read live from a source.
 *
 * All three used to be separate row composables in two different screens, which is how their
 * typography drifted apart: a change to one silently left the others behind. Slots cover what
 * actually differs (a cover, a selection tick, an unread dot, a "New" chip, a progress marker) while
 * the title/meta layout stays in one place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesEntryRow(
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Read entries fade back, unless they're selected (where the highlight has to stay legible). */
    dimmed: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Before the text: a selection tick, an unread dot, or a cover thumbnail. */
    leading: @Composable (() -> Unit)? = null,
    /** Beside the title: badges such as "New". */
    titleTrailing: @Composable (() -> Unit)? = null,
    /** After the text: reading progress, download state. */
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickable = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f).alpha(if (dimmed && !selected) 0.55f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                )
                titleTrailing?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { it() }
        }
    }
}

package app.kodex.client.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Presentation shared by the two series-detail screens (a library series and a source series being
// browsed). They were copy-pasted between the two, which let them drift — the summary's "Read more"
// was tappable in one and not the other.

/** Series summary capped at 3 lines with a "Read more" toggle when it overflows; tapping toggles too. */
@Composable
fun ExpandableSummary(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(6.dp))
            .clickable(enabled = overflows || expanded) { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { expanded = !expanded })
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** A compact pill that opens a menu: label + dropdown caret, tinted in the primary color. */
@Composable
fun ControlChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** A sort-key menu row: leading check when active, trailing arrow for its direction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortMenuItem(label: String, selected: Boolean, sortDesc: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (selected) Icon(
                if (sortDesc) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (sortDesc) "Descending" else "Ascending",
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}


/**
 * How a series' books/chapters are ordered. SOURCE leaves the server's own ordering alone — for a WEB
 * series that's the order the content source lists them in, which numbering often doesn't reproduce.
 */
enum class SeriesSort { NUMBER, DATE, SOURCE }

/** Sort (number/date + direction), optional translator filter, and refresh controls above a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListControls(
    countLabel: String,
    numberLabel: String,
    sortKey: SeriesSort,
    sortDesc: Boolean,
    onToggleDir: () -> Unit,
    onSetSortKey: (SeriesSort) -> Unit,
    scanlators: List<String>,
    translator: String?,
    onSetTranslator: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var transMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(countLabel)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            val activeLabel = when (sortKey) {
                SeriesSort.NUMBER -> numberLabel
                SeriesSort.DATE -> "Release date"
                SeriesSort.SOURCE -> "Source order"
            }
            ControlChip(activeLabel) { sortMenu = true }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                // Tapping the active key flips direction; tapping another switches to it.
                listOf(
                    SeriesSort.NUMBER to numberLabel,
                    SeriesSort.DATE to "Release date",
                    SeriesSort.SOURCE to "Source order",
                ).forEach { (key, label) ->
                    SortMenuItem(label, selected = sortKey == key, sortDesc = sortDesc) {
                        if (sortKey == key) onToggleDir() else onSetSortKey(key)
                    }
                }
            }
        }
        if (scanlators.size > 1) {
            Spacer(Modifier.width(8.dp))
            Box {
                ControlChip(translator ?: "All translators") { transMenu = true }
                DropdownMenu(expanded = transMenu, onDismissRequest = { transMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All translators") },
                        onClick = { transMenu = false; onSetTranslator(null) },
                        leadingIcon = { if (translator == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                    scanlators.forEach { sc ->
                        DropdownMenuItem(
                            text = { Text(sc) },
                            onClick = { transMenu = false; onSetTranslator(sc) },
                            leadingIcon = { if (translator == sc) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

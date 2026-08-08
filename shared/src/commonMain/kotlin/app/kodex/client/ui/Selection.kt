package app.kodex.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.kodex.client.ui.icons.InvertSelectionIcon
import app.kodex.client.ui.icons.SelectAllIcon

/**
 * Multi-select state for long-press selection mode: a growing/shrinking set of selected item ids.
 * [active] flips the screen into a contextual "selection" top bar; when it empties, selection ends.
 * The reusable Phase-0 enabler behind bulk actions (mark-read, download, …).
 */
class SelectionState<T> {
    private val items = emptyList<T>().toMutableStateList()

    val selected: List<T> get() = items
    val count: Int get() = items.size
    val active: Boolean get() = items.isNotEmpty()

    fun isSelected(id: T): Boolean = id in items

    fun toggle(id: T) {
        if (!items.remove(id)) items.add(id)
    }

    fun selectAll(ids: Collection<T>) {
        items.clear()
        items.addAll(ids)
    }

    /** Select exactly the items in [ids] that aren't currently selected (Mihon-style "invert"). */
    fun selectInverse(ids: Collection<T>) {
        val inverse = ids.filterNot { it in items }
        items.clear()
        items.addAll(inverse)
    }

    fun clear() = items.clear()
}

@Composable
fun <T> rememberSelection(): SelectionState<T> = remember { SelectionState() }

// The contextual chrome that [SelectionState.active] swaps in. The library grid and the series
// detail list each had their own copy; the bars are the same, only the bulk actions differ, so the
// action row is a slot.

/** Contextual top bar for multi-select: the count, cancel, and select-all / invert. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectInverse: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = { Text("$count selected", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            TooltipIconButton("Cancel selection", onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            TooltipIconButton("Select all", onSelectAll) {
                Icon(SelectAllIcon, contentDescription = "Select all")
            }
            TooltipIconButton("Select inverse", onSelectInverse) {
                Icon(InvertSelectionIcon, contentDescription = "Select inverse")
            }
        },
    )
}

/** Contextual bottom bar holding the bulk actions, spaced evenly across the width. */
@Composable
fun SelectionActionBar(actions: @Composable RowScope.() -> Unit) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { actions() }
    }
}

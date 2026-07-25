package app.kodex.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList

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

    fun clear() = items.clear()
}

@Composable
fun <T> rememberSelection(): SelectionState<T> = remember { SelectionState() }

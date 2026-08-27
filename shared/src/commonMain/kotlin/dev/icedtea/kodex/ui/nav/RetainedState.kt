package dev.icedtea.kodex.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * State that survives a screen being covered by another one.
 *
 * The back stack composes only its top entry, so pushing a detail screen unmounts the screen beneath
 * it and every `remember` in that screen dies. That is why returning from a series re-ran the browse
 * feed's first-page load and landed back at the top of the grid. Values held here belong to the
 * navigation host instead, and are dropped when their entry is popped for good.
 *
 * Deliberately in-memory only: this holds whole pages of loaded results, which is far too much to put
 * through `rememberSaveable` (on Android that ends up in the saved-instance Bundle).
 */
class RetainedStateStore {
    private val slots = mutableMapOf<String, MutableMap<String, Any>>()

    /** The bag of retained values belonging to one navigation entry. */
    fun slot(entry: String): MutableMap<String, Any> = slots.getOrPut(entry) { mutableMapOf() }

    /** Drop an entry's values — call when it is popped, so re-opening the screen starts fresh. */
    fun forget(entry: String) {
        slots.remove(entry)
    }
}

/** The current navigation entry's bag. Null outside a navigation host, where [retain] is just `remember`. */
val LocalRetainedSlot = staticCompositionLocalOf<MutableMap<String, Any>?> { null }

/**
 * Like `remember`, but the value outlives this screen being covered by another; it is recreated only
 * once the screen is left for good. [key] only has to be unique within the screen; a null key opts
 * out entirely and makes this plain `remember`, which is how callers expose retention as a choice.
 */
@Composable
fun <T : Any> retain(key: String?, factory: () -> T): T {
    val slot = LocalRetainedSlot.current.takeIf { key != null }
    return remember(slot, key) {
        @Suppress("UNCHECKED_CAST")
        if (slot == null) factory() else slot.getOrPut(key!!) { factory() } as T
    }
}

package app.kodex.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour painted behind the system navigation bar, so the gesture pill / button strip reads as a
 * continuation of whatever bottom surface the current screen shows instead of a slab of plain
 * `surface` beneath it. `null` — the default — leaves the strip to the root surface.
 *
 * Held in a [MutableState] rather than provided as a value because the screen that owns the bottom
 * bar is composed *below* the root that paints the strip. `App` provides the holder; screens write
 * to it through [SystemNavBarColor].
 */
val LocalSystemNavBarColor = staticCompositionLocalOf { mutableStateOf<Color?>(null) }

/**
 * Paints the system navigation-bar strip in [color] for as long as the caller is composed, restoring
 * the default when it leaves. Call it from a screen whose bottom bar should extend under the strip.
 */
@Composable
fun SystemNavBarColor(color: Color) {
    val holder = LocalSystemNavBarColor.current
    DisposableEffect(holder, color) {
        holder.value = color
        onDispose { holder.value = null }
    }
}

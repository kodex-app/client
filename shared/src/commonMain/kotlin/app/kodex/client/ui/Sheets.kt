package app.kodex.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Floor for modal bottom sheets: half the window height, so a short sheet still opens as a
 * proper panel instead of a thin strip. Sheets stay content-sized above this and never exceed
 * the sheet's own maximum.
 */
@Composable
fun sheetMinHeight(fraction: Float = 0.5f): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { (heightPx * fraction).toDp() }
}

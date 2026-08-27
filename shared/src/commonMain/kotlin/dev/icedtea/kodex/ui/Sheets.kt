package dev.icedtea.kodex.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Ceiling for modal bottom sheets: three quarters of the window height, so even a long sheet
 * leaves the page behind it visible and scrolls its overflow internally. Sheets stay
 * content-sized below this.
 */
@Composable
fun sheetMaxHeight(fraction: Float = 0.75f): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { (heightPx * fraction).toDp() }
}

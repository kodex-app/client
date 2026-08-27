package dev.icedtea.kodex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * A [ModalBottomSheet] capped at [sheetMaxHeight] — the app's only bottom sheet.
 *
 * The cap goes on the *content*, never on the sheet's own `modifier`. Material3 anchors the sheet
 * from the constraints its modifier chain hands down: `Expanded` sits at
 * `constraints.maxHeight - sheetHeight`, so a `heightIn(max = 75%)` there redefines the bottom of
 * the screen as three quarters down it and the sheet comes to rest floating in the middle of the
 * page — and dismisses into that same spot rather than off the bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KodexBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    fraction: Float = 0.75f,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight(fraction)), content = content)
    }
}

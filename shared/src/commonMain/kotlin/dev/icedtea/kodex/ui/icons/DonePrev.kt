package dev.icedtea.kodex.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The "mark previous as read" glyph: a check with a small down arrow beside it, traced from Mihon's
 * own `ic_done_prev_24dp` so the action reads the same as it does there.
 *
 * Material ships no such icon, hence the hand-built vector. The fill colour is irrelevant — `Icon`
 * paints these through a tint filter.
 */
val DonePrev: ImageVector by lazy { donePrevBuilder("DonePrev").build() }

/**
 * The unread counterpart: the same check + arrow with the check struck through. The slash stops short
 * of the arrow, so the "previous" half of the glyph stays legible.
 */
val UndonePrev: ImageVector by lazy {
    donePrevBuilder("UndonePrev").apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.1f, 2.7f)
            lineTo(2.7f, 4.1f)
            lineTo(15.5f, 16.9f)
            lineTo(16.9f, 15.5f)
            close()
        }
    }.build()
}

/** The check + down arrow both icons share. */
private fun donePrevBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Check.
    path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 16.2f)
        lineTo(4.8f, 12f)
        lineToRelative(-1.4f, 1.4f)
        lineTo(9f, 19f)
        lineTo(21f, 7f)
        lineToRelative(-1.4f, -1.4f)
        lineTo(9f, 16.2f)
        close()
    }
    // Down arrow, bottom-right.
    path(fill = SolidColor(Color.Black)) {
        moveTo(22f, 18f)
        lineToRelative(-3f, 0f)
        lineToRelative(0f, -4f)
        lineToRelative(-2f, 0f)
        lineToRelative(0f, 4f)
        lineToRelative(-3f, 0f)
        lineToRelative(4f, 4f)
        close()
    }
}

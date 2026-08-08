package app.kodex.client.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

// Icon-only buttons carry their label in a tooltip. Three screens had grown their own copy of this
// (two named `Tip`, one `TooltipIconButton`), so it lives here once.

/** Wraps any content in a plain tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

/** The common case: an [IconButton] whose tooltip is its label. */
@Composable
fun TooltipIconButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Tip(label) { IconButton(onClick = onClick) { icon() } }
}

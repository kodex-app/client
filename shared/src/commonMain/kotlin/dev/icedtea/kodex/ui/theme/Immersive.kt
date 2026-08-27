package dev.icedtea.kodex.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the screen on top wants the window undivided — no app-wide strip reserved for the system
 * navigation bar.
 *
 * Held in a [androidx.compose.runtime.MutableState] rather than provided as a value for the same
 * reason as [LocalSystemNavBarColor]: the screen that decides is composed *below* the root that
 * applies the padding. `App` provides the holder; screens write to it through [ImmersiveContent].
 */
val LocalImmersiveContent = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * Drops the app-wide navigation-bar padding while [immersive] is true.
 *
 * The readers hide the system bars along with their own chrome. While the app reserved the
 * navigation-bar inset around them, that reserved strip collapsed to zero the moment the bars went
 * away and came back when they returned — so the reader's box grew and shrank on every chrome
 * toggle and the page visibly jumped up and down. The readers lay out edge-to-edge and pad their
 * own bars, so they take the whole window instead.
 */
@Composable
fun ImmersiveContent(immersive: Boolean = true) {
    val holder = LocalImmersiveContent.current
    DisposableEffect(holder, immersive) {
        holder.value = immersive
        onDispose { holder.value = false }
    }
}

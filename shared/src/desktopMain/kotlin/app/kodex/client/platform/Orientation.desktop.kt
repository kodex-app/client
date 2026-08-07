package app.kodex.client.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop windows have no fixed orientation — a no-op controller that stays on AUTO.
@Composable
actual fun rememberOrientationController(): OrientationController = remember {
    object : OrientationController {
        override val orientation = ScreenOrientation.AUTO
        override fun cycle() {}
    }
}

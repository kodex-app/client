package app.kodex.client.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS orientation locking needs app-delegate coordination; keep a no-op AUTO controller for now.
@Composable
actual fun rememberOrientationController(): OrientationController = remember {
    object : OrientationController {
        override val orientation = ScreenOrientation.AUTO
        override fun cycle() {}
    }
}

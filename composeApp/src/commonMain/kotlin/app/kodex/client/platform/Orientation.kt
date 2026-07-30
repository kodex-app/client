package app.kodex.client.platform

import androidx.compose.runtime.Composable

/** Requested screen orientation for the reader. AUTO follows the device sensor/system setting. */
enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** Locks/unlocks the screen orientation. [cycle] steps AUTO → PORTRAIT → LANDSCAPE → AUTO. */
interface OrientationController {
    val orientation: ScreenOrientation
    fun cycle()
}

/** Platform orientation control. On Android it drives the Activity; elsewhere it's a no-op (AUTO). */
@Composable
expect fun rememberOrientationController(): OrientationController

package app.kodex.client.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The system-bar appearance the app currently wants, held outside composition because
 * `enableEdgeToEdge` needs to read it from outside one — see [enableKodexEdgeToEdge].
 *
 * `true` means the bar sits on a dark background and therefore needs light icons.
 */
private object BarAppearance {
    @Volatile var statusIsDark: Boolean = false
    @Volatile var navIsDark: Boolean = false
}

/**
 * Goes edge-to-edge without letting androidx own the bar icon colours. Call from `onCreate`, before
 * `super.onCreate`.
 *
 * A plain `enableEdgeToEdge()` installs a hidden view in the decor view that re-runs its whole setup
 * — icon appearance included — on *every* configuration change: rotation, window resize, keyboard,
 * density, uiMode. The default style derives that appearance from the **system** night mode, so as
 * soon as the in-app theme disagrees with the phone (app on dark, system on light) the first config
 * change stomped [StatusBarIcons] and left black icons on the app's dark UI. Pointing its dark-mode
 * detection at [BarAppearance] instead makes those re-applies land on the app's own value.
 */
fun ComponentActivity.enableKodexEdgeToEdge() {
    // Until the theme prefs have been read, mirror what androidx would have done on its own.
    val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    BarAppearance.statusIsDark = systemDark
    BarAppearance.navIsDark = systemDark

    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
            BarAppearance.statusIsDark
        },
        // Transparent rather than androidx's default scrims: the app paints the strip behind the
        // navigation bar itself (the bottom bar's colour — see SystemNavBarColor), and a scrim would
        // only wash that colour out. Same reason for turning contrast enforcement off below.
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
            BarAppearance.navIsDark
        },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

@Composable
actual fun StatusBarIcons(darkIcons: Boolean, navDarkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons, navDarkIcons) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val prevStatus = BarAppearance.statusIsDark
            val prevNav = BarAppearance.navIsDark
            controller.applyAppearance(statusIsDark = !darkIcons, navIsDark = !navDarkIcons)
            onDispose { controller.applyAppearance(prevStatus, prevNav) }
        }
    }
}

/**
 * Writes the appearance to the window *and* to [BarAppearance], so a configuration change replaying
 * androidx's edge-to-edge setup re-derives the same values rather than the system's.
 */
private fun WindowInsetsControllerCompat.applyAppearance(statusIsDark: Boolean, navIsDark: Boolean) {
    BarAppearance.statusIsDark = statusIsDark
    BarAppearance.navIsDark = navIsDark
    // isAppearanceLightBars = true → light bar background → DARK icons (visible on a light UI).
    isAppearanceLightStatusBars = !statusIsDark
    isAppearanceLightNavigationBars = !navIsDark
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

package app.kodex.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.kodex.client.data.AppSettings
import app.kodex.client.platform.dynamicColorScheme
import app.kodex.client.ui.collectAsStateSafe

/**
 * Applies the user's appearance choice from [AppSettings]: theme mode (system/light/dark), the
 * selected Mihon-ported palette (or Monet dynamic colour when enabled + supported), and the AMOLED
 * pure-black override. Reactive — changing any pref in Appearance re-themes the whole app instantly.
 */
@Composable
fun KodexTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val mode by settings.themeMode.collectAsStateSafe()
    val theme by settings.appTheme.collectAsStateSafe()
    val amoled by settings.amoled.collectAsStateSafe()
    val dynamic by settings.dynamicColor.collectAsStateSafe()
    val incognito by settings.incognitoMode.collectAsStateSafe()

    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val dynamicScheme = if (dynamic) dynamicColorScheme(dark) else null
    val scheme = dynamicScheme ?: resolveColorScheme(theme, dark, amoled)

    // Match the system-bar icons to the theme so they stay visible: dark icons in light mode, light in dark.
    // Incognito is the exception: its banner runs its dark indigo under the status bar app-wide, so
    // that bar always needs light icons. The navigation bar is untouched by the banner and keeps the
    // theme's rule. Readers that hide the banner set their own appearance further down the tree.
    app.kodex.client.platform.StatusBarIcons(darkIcons = !dark && !incognito, navDarkIcons = !dark)

    MaterialTheme(colorScheme = scheme, content = content)
}

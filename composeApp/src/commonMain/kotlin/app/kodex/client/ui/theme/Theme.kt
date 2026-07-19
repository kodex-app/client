package app.kodex.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kodex accent — a warm violet, distinct from the web UI's chrome so the app reads as its own thing
// (see the "beautiful, not a React copy" goal) rather than mimicking the browser.
private val Violet = Color(0xFF6D5AE6)
private val VioletDark = Color(0xFFB7A9FF)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1C0066),
    secondary = Color(0xFF5D5C72),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF48454E),
)

private val DarkColors = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF31137F),
    primaryContainer = Color(0xFF493B99),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFC7C4DD),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF121218),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFC9C5D0),
)

@Composable
fun KodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

package app.kodex.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.data.AppSettings
import app.kodex.client.platform.isDynamicColorSupported
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.theme.AppTheme
import app.kodex.client.ui.theme.ThemeMode

/** Appearance: theme mode, AMOLED black, Material You dynamic colour, and the colour-theme picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settings: AppSettings, onBack: () -> Unit) {
    val mode by settings.themeMode.collectAsStateSafe()
    val theme by settings.appTheme.collectAsStateSafe()
    val amoled by settings.amoled.collectAsStateSafe()
    val dynamic by settings.dynamicColor.collectAsStateSafe()

    val effectiveDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicSupported = isDynamicColorSupported()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            SectionHeaderA("Theme mode")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { settings.setThemeMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(m.name.lowercase().replaceFirstChar(Char::uppercase)) }
                }
            }

            ToggleRowA(
                title = "AMOLED black",
                subtitle = "Pure-black backgrounds in dark mode (saves power on OLED)",
                checked = amoled,
                onCheckedChange = { settings.setAmoled(it) },
            )

            if (dynamicSupported) {
                ToggleRowA(
                    title = "Dynamic colour",
                    subtitle = "Use Material You colours from your wallpaper",
                    checked = dynamic,
                    onCheckedChange = { settings.setDynamicColor(it) },
                )
            }

            SectionHeaderA("Colour theme")
            if (dynamic && dynamicSupported) {
                Text(
                    "Turn off dynamic colour to choose a palette.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val enabled = !(dynamic && dynamicSupported)
            AppTheme.entries.chunked(2).forEach { rowThemes ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowThemes.forEach { t ->
                        ThemeSwatchCard(
                            theme = t,
                            dark = effectiveDark,
                            selected = theme == t && enabled,
                            enabled = enabled,
                            onClick = { settings.setAppTheme(t) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    theme: AppTheme,
    dark: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = if (dark) theme.dark else theme.light
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(scheme.primary)
            Spacer(Modifier.width(6.dp))
            Swatch(scheme.secondary)
            Spacer(Modifier.width(6.dp))
            Swatch(scheme.tertiary)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            theme.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(20.dp).clip(CircleShape).background(color))
}

@Composable
private fun SectionHeaderA(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleRowA(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

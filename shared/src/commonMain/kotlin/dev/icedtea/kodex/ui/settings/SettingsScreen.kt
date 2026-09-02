package dev.icedtea.kodex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.persistSetting
import dev.icedtea.kodex.ui.reader.BG_BLACK
import dev.icedtea.kodex.ui.reader.parseReaderDefault
import dev.icedtea.kodex.ui.reader.saveReaderDefault
import dev.icedtea.kodex.ui.reader.BG_GRAY
import dev.icedtea.kodex.ui.reader.BG_WHITE
import dev.icedtea.kodex.ui.reader.DIR_LTR
import dev.icedtea.kodex.ui.reader.DIR_RTL
import dev.icedtea.kodex.ui.reader.MODE_AUTO
import dev.icedtea.kodex.ui.reader.MODE_CONTINUOUS
import dev.icedtea.kodex.ui.reader.MODE_PAGED
import dev.icedtea.kodex.ui.reader.ReaderPrefs
import dev.icedtea.kodex.ui.reader.ReaderSettingsSegmented
import dev.icedtea.kodex.ui.reader.ReaderSettingsSelect
import dev.icedtea.kodex.ui.reader.ReaderSettingsToggle
import dev.icedtea.kodex.ui.reader.SPREAD_DOUBLE
import dev.icedtea.kodex.ui.reader.SPREAD_SINGLE
import dev.icedtea.kodex.ui.reader.ZOOM_HEIGHT
import dev.icedtea.kodex.ui.reader.ZOOM_ORIGINAL
import dev.icedtea.kodex.ui.reader.ZOOM_WIDTH
import dev.icedtea.kodex.ui.reader.ebook.ALIGN_AUTO
import dev.icedtea.kodex.ui.reader.ebook.ALIGN_JUSTIFY
import dev.icedtea.kodex.ui.reader.ebook.ALIGN_LEFT
import dev.icedtea.kodex.ui.reader.ebook.COLUMNS_AUTO
import dev.icedtea.kodex.ui.reader.ebook.COLUMNS_ONE
import dev.icedtea.kodex.ui.reader.ebook.COLUMNS_TWO
import dev.icedtea.kodex.ui.reader.ebook.EbookPrefs
import dev.icedtea.kodex.ui.reader.ebook.FLOW_PAGINATED
import dev.icedtea.kodex.ui.reader.ebook.FLOW_SCROLLED
import dev.icedtea.kodex.ui.reader.ebook.FONT_SIZE_MAX
import dev.icedtea.kodex.ui.reader.ebook.FONT_SIZE_MIN
import dev.icedtea.kodex.ui.reader.ebook.LINE_HEIGHT_MAX
import dev.icedtea.kodex.ui.reader.ebook.LINE_HEIGHT_MIN
import dev.icedtea.kodex.ui.reader.ebook.MARGIN_MAX
import dev.icedtea.kodex.ui.reader.ebook.MARGIN_MIN
import dev.icedtea.kodex.ui.reader.ebook.THEME_AUTO
import dev.icedtea.kodex.ui.reader.ebook.THEME_DARK
import dev.icedtea.kodex.ui.reader.ebook.THEME_LIGHT
import dev.icedtea.kodex.ui.reader.ebook.THEME_SEPIA
import dev.icedtea.kodex.ui.reader.ebook.ebookFontOptions
import dev.icedtea.kodex.ui.reader.ebook.parseEbookDefault
import dev.icedtea.kodex.ui.reader.ebook.saveEbookDefault
import dev.icedtea.kodex.ui.reader.ReaderSettingsSlider
import dev.icedtea.kodex.ui.reader.ReaderSettingsStepper
import kotlin.math.roundToInt
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.collectAsStateSafe
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-user settings backed by the server (`/users/me/settings`): the two series-behaviour prefs
 * (auto-update-on-open, default chapter sort), the reader defaults for each kind of book, and which
 * libraries sync to reading devices.
 *
 * The reader defaults here and a reader's own settings sheet write the same keys, so they are drawn
 * with the same rows from `ui.reader` rather than a second set that could drift from them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = server?.id,
                load = {
                    val s = server!!
                    SettingsData(
                        settings = api.userSettings(s.baseUrl, s.apiKey),
                        libraries = api.libraries(s.baseUrl, s.apiKey),
                        // For the ebook font picker. A server too old to know these endpoints just
                        // leaves the lists empty, and the picker falls back to the publisher's fonts.
                        bundledFonts = runCatching { api.bundledFonts(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()),
                        customFonts = runCatching { api.customFonts(s.baseUrl, s.apiKey) }.getOrDefault(emptyList()),
                    )
                },
            ) { data ->
                val s = server ?: return@LoadedContent
                SettingsForm(session, s.baseUrl, s.apiKey, api, data)
            }
        }
    }
}

private data class SettingsData(
    val settings: JsonObject,
    val libraries: List<dev.icedtea.kodex.network.LibraryDto>,
    val bundledFonts: List<dev.icedtea.kodex.network.BundledFontDto>,
    val customFonts: List<dev.icedtea.kodex.network.CustomFontDto>,
)

private const val CHAPTER_SORT_DEFAULT = "number,desc"

@Composable
private fun SettingsForm(
    session: SessionManager,
    baseUrl: String,
    apiKey: String,
    api: KodexApi,
    data: SettingsData,
) {
    val initial = data.settings
    val libraries = data.libraries
    val snackbar = dev.icedtea.kodex.ui.rememberSnackbar()

    var autoUpdate by remember {
        mutableStateOf(initial["series.autoUpdateOnOpen"]?.jsonPrimitive?.booleanOrNull ?: true)
    }
    var chapterSort by remember {
        mutableStateOf(initial["series.chapterSort"]?.jsonPrimitive?.contentOrNull ?: CHAPTER_SORT_DEFAULT)
    }
    // sync.libraries: empty selection ⇒ all libraries sync (server default).
    var syncLibs by remember {
        mutableStateOf(
            (initial["sync.libraries"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
        )
    }
    var comicPrefs by remember { mutableStateOf(parseReaderDefault(initial, "comic")) }
    var pdfPrefs by remember { mutableStateOf(parseReaderDefault(initial, "pdf")) }
    var ebookPrefs by remember { mutableStateOf(parseEbookDefault(initial)) }

    // On the session's scope, not this screen's: toggling a setting and going straight back is the
    // ordinary way to use this screen, and a write started here would be cancelled by that very
    // navigation. Failures now say so instead of leaving the switch showing a value nobody stored.
    fun save(key: String, value: kotlinx.serialization.json.JsonElement) {
        session.persistSetting(snackbar) { api.saveUserSetting(baseUrl, apiKey, key, value) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SectionHeader("Series")
        SwitchRow(
            title = "Auto-update on open",
            subtitle = "Check for new chapters when opening a series",
            checked = autoUpdate,
            onCheckedChange = { autoUpdate = it; save("series.autoUpdateOnOpen", JsonPrimitive(it)) },
        )
        ChapterSortRow(current = chapterSort, onSelect = { chapterSort = it; save("series.chapterSort", JsonPrimitive(it)) })

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Reader defaults")
        // Says which books these reach, in the same terms the readers' own sheets use for their
        // "Save as default" buttons — this screen and those buttons write the same three keys.
        Text(
            "Used by books without settings of their own. A book you adjust while reading keeps its own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        ReaderDefaultsRows("Comics", comicPrefs) { p ->
            comicPrefs = p
            session.persistSetting(snackbar) { saveReaderDefault(api, baseUrl, apiKey, "comic", p) }
        }
        ReaderDefaultsRows("PDF", pdfPrefs) { p ->
            pdfPrefs = p
            session.persistSetting(snackbar) { saveReaderDefault(api, baseUrl, apiKey, "pdf", p) }
        }
        EbookDefaultsRows(ebookPrefs, data.bundledFonts, data.customFonts) { p ->
            ebookPrefs = p
            session.persistSetting(snackbar) { saveEbookDefault(api, baseUrl, apiKey, p) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Sync to reading devices")
        Text(
            if (syncLibs.isEmpty()) "All libraries sync to KOReader / Kobo." else "${syncLibs.size} librar${if (syncLibs.size == 1) "y" else "ies"} selected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        libraries.forEach { lib ->
            CheckRow(lib.name, lib.id in syncLibs) {
                syncLibs = if (lib.id in syncLibs) syncLibs - lib.id else syncLibs + lib.id
                save("sync.libraries", kotlinx.serialization.json.JsonArray(syncLibs.map { JsonPrimitive(it) }))
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
    }
}

// The reader's own wording and values, not a second copy of them: a setting renamed in the reader
// would otherwise quietly stop matching the one here that writes the same key.
private val READER_MODES = listOf(MODE_AUTO to "Auto", MODE_PAGED to "Paged", MODE_CONTINUOUS to "Continuous")
private val READER_DIRS = listOf(DIR_LTR to "L → R", DIR_RTL to "R → L")
private val READER_ZOOMS = listOf(ZOOM_HEIGHT to "Height", ZOOM_WIDTH to "Width", ZOOM_ORIGINAL to "Original")
private val READER_SPREADS = listOf(SPREAD_SINGLE to "Single", SPREAD_DOUBLE to "Double")
private val READER_BGS = listOf(BG_WHITE to "White", BG_GRAY to "Gray", BG_BLACK to "Black")

/**
 * One kind's defaults, drawn with the same rows as the reader's own settings sheet — same two-column
 * shape, same controls, same words for the same values, so the screen that sets the default and the
 * sheet that overrides it don't look like two different features.
 *
 * It also covers what the sheet covers. Mode/direction/zoom were the three this screen happened to
 * offer; page spread, background and tap-to-turn are stored in exactly the same object and were
 * simply unreachable from here.
 */
@Composable
private fun ReaderDefaultsRows(label: String, prefs: ReaderPrefs, onChange: (ReaderPrefs) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
    )
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReaderSettingsSelect("Layout", prefs.mode, READER_MODES) { onChange(prefs.copy(mode = it)) }
        ReaderSettingsSelect("Fit", prefs.zoom, READER_ZOOMS) { onChange(prefs.copy(zoom = it)) }
        ReaderSettingsSegmented("Pages", prefs.spread, READER_SPREADS) { onChange(prefs.copy(spread = it)) }
        ReaderSettingsSegmented("Direction", prefs.direction, READER_DIRS) { onChange(prefs.copy(direction = it)) }
        // A select rather than the sheet's swatches: this is a list of settings, and three colour
        // tiles here would be the loudest thing on the screen.
        ReaderSettingsSelect("Background", prefs.bg, READER_BGS) { onChange(prefs.copy(bg = it)) }
        ReaderSettingsToggle(
            label = "Tap to turn",
            checked = prefs.tapToTurn,
            description = "Tap the left or right edge to change page",
        ) {
            onChange(prefs.copy(tapToTurn = it))
        }
    }
}

/**
 * Ebook defaults, drawn with the same rows as the ebook reader's own settings sheet.
 *
 * Only the stored prefs appear here. The page-turn animation is a device setting rather than one of
 * these (`AppSettings.ebookPageAnim`), and screen orientation lasts only as long as a reader is open —
 * both belong where you can watch the page change, not on a defaults screen.
 */
@Composable
private fun EbookDefaultsRows(
    prefs: EbookPrefs,
    bundledFonts: List<dev.icedtea.kodex.network.BundledFontDto>,
    customFonts: List<dev.icedtea.kodex.network.CustomFontDto>,
    onChange: (EbookPrefs) -> Unit,
) {
    Text(
        "Ebooks",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
    )
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReaderSettingsSelect("Theme", prefs.theme, EBOOK_THEMES) { onChange(prefs.copy(theme = it)) }
        ReaderSettingsSelect("Font", prefs.fontFamily, ebookFontOptions(prefs.fontFamily, bundledFonts, customFonts)) {
            onChange(prefs.copy(fontFamily = it))
        }
        ReaderSettingsStepper(
            label = "Text size",
            value = "${prefs.fontSize}%",
            canDecrease = prefs.fontSize > FONT_SIZE_MIN,
            canIncrease = prefs.fontSize < FONT_SIZE_MAX,
            onDecrease = { onChange(prefs.copy(fontSize = (prefs.fontSize - 10).coerceAtLeast(FONT_SIZE_MIN))) },
            onIncrease = { onChange(prefs.copy(fontSize = (prefs.fontSize + 10).coerceAtMost(FONT_SIZE_MAX))) },
        )
        ReaderSettingsStepper(
            label = "Line height",
            value = "${prefs.lineHeight}%",
            canDecrease = prefs.lineHeight > LINE_HEIGHT_MIN,
            canIncrease = prefs.lineHeight < LINE_HEIGHT_MAX,
            onDecrease = { onChange(prefs.copy(lineHeight = (prefs.lineHeight - 10).coerceAtLeast(LINE_HEIGHT_MIN))) },
            onIncrease = { onChange(prefs.copy(lineHeight = (prefs.lineHeight + 10).coerceAtMost(LINE_HEIGHT_MAX))) },
        )
        ReaderSettingsSelect("Alignment", prefs.textAlign, EBOOK_ALIGNMENTS) { onChange(prefs.copy(textAlign = it)) }
        ReaderSettingsSelect("Indent", prefs.indent?.toString() ?: "auto", EBOOK_INDENTS) {
            onChange(prefs.copy(indent = if (it == "auto") null else it.toDoubleOrNull()))
        }
        ReaderSettingsSegmented("Layout", prefs.flow, EBOOK_FLOWS) { onChange(prefs.copy(flow = it)) }
        // Column count is a paginated-only choice, exactly as in the reader.
        if (prefs.flow == FLOW_PAGINATED) {
            ReaderSettingsSegmented("Columns", prefs.columns, EBOOK_COLUMNS) { onChange(prefs.copy(columns = it)) }
        }
        ReaderSettingsSlider(
            label = "Margin",
            value = prefs.margin.toFloat(),
            valueText = "${prefs.margin} px",
            valueRange = MARGIN_MIN.toFloat()..MARGIN_MAX.toFloat(),
            steps = (MARGIN_MAX / 8) - 1,
        ) {
            onChange(prefs.copy(margin = it.roundToInt().coerceIn(MARGIN_MIN, MARGIN_MAX)))
        }
    }
}

private val EBOOK_THEMES =
    listOf(THEME_AUTO to "Auto", THEME_LIGHT to "Light", THEME_SEPIA to "Sepia", THEME_DARK to "Dark")
private val EBOOK_ALIGNMENTS = listOf(ALIGN_AUTO to "Auto", ALIGN_LEFT to "Left", ALIGN_JUSTIFY to "Justify")
private val EBOOK_INDENTS = listOf("auto" to "Auto", "0.0" to "None", "1.0" to "1em", "2.0" to "2em")
private val EBOOK_FLOWS = listOf(FLOW_PAGINATED to "Paged", FLOW_SCROLLED to "Scrolled")
private val EBOOK_COLUMNS = listOf(COLUMNS_AUTO to "Auto", COLUMNS_ONE to "One", COLUMNS_TWO to "Two")

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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

private val SORT_OPTIONS = listOf(
    "number,desc" to "Chapter number (newest first)",
    "number,asc" to "Chapter number (oldest first)",
    "releaseDate,desc" to "Release date (newest first)",
    "releaseDate,asc" to "Release date (oldest first)",
)

@Composable
private fun ChapterSortRow(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = SORT_OPTIONS.firstOrNull { it.first == current }?.second ?: SORT_OPTIONS.first().second
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Default chapter sort", style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { open = true }) { Text("Change") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SORT_OPTIONS.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

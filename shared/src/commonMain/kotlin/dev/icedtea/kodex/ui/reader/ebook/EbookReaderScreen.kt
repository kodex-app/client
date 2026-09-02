package dev.icedtea.kodex.ui.reader.ebook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.network.BookmarkDto
import dev.icedtea.kodex.network.BundledFontDto
import dev.icedtea.kodex.network.CustomFontDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.platform.StatusBarIcons
import dev.icedtea.kodex.platform.SystemBarsHidden
import dev.icedtea.kodex.platform.TTS_RATES
import dev.icedtea.kodex.platform.TtsVoice
import dev.icedtea.kodex.ui.KodexBottomSheet
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.persistSetting
import dev.icedtea.kodex.ui.rememberSnackbar
import dev.icedtea.kodex.ui.reader.ChapterListSheet
import dev.icedtea.kodex.ui.reader.ChapterTransitionPage
import dev.icedtea.kodex.ui.reader.IncognitoBadge
import dev.icedtea.kodex.ui.reader.ReaderChapterNav
import dev.icedtea.kodex.ui.reader.ReadModeButton
import dev.icedtea.kodex.ui.reader.ReaderEdge
import dev.icedtea.kodex.ui.reader.ReaderModeOption
import dev.icedtea.kodex.ui.reader.ReaderSettingsCard
import dev.icedtea.kodex.ui.reader.ReaderSettingsChips
import dev.icedtea.kodex.ui.reader.ReaderSettingsFooter
import dev.icedtea.kodex.ui.reader.ReaderSettingsHeader
import dev.icedtea.kodex.ui.reader.ReaderSettingsSectionLabel
import dev.icedtea.kodex.ui.reader.ReaderSettingsSegmented
import dev.icedtea.kodex.ui.reader.ReaderSettingsSlider
import dev.icedtea.kodex.ui.reader.ReaderSettingsStepper
import dev.icedtea.kodex.ui.reader.ReaderSettingsSwatches
import dev.icedtea.kodex.ui.reader.ReaderSwatch
import dev.icedtea.kodex.ui.reader.SETTINGS_SHEET_FRACTION
import dev.icedtea.kodex.ui.reader.SheetGutter
import dev.icedtea.kodex.ui.reader.ToolbarButton
import dev.icedtea.kodex.ui.reader.disabled
import dev.icedtea.kodex.ui.reader.readerBarColor
import dev.icedtea.kodex.ui.reader.readerBarContentColor
import dev.icedtea.kodex.ui.reader.readerBarRaisedColor
import dev.icedtea.kodex.ui.reader.rememberSheetScrollGuard
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything the ebook reader needs, independent of whether the book is a library download or a
 * chapter streamed from a content source. The image reader's [dev.icedtea.kodex.ui.reader.ReaderSource]
 * equivalent — deliberately separate, because a reflowable book has no page count to speak of and
 * positions itself by CFI rather than page number.
 */
class EbookSource(
    val title: String, // top-bar line 1 — the series, when it's known
    val subtitle: String? = null, // top-bar line 2 — this book/chapter within the series
    /** `epub` streams entry-by-entry; `mobi` (incl. KF8/AZW3) and `fb2` are parsed from the whole file. */
    val format: String,
    val origin: EbookOrigin,
    val seriesId: String?, // scope for per-series display settings
    val initialLocator: String?, // foliate CFI to resume at, when there is one
    val initialFraction: Double?, // fallback resume position (0–1)
    /**
     * Records progress. [sectionTotal] is the book's spine length, handed over so the caller can map a
     * fraction onto whatever page proxy its progress store expects.
     */
    val onPersist: suspend (fraction: Double, locator: String?, sectionTotal: Int, completed: Boolean) -> Unit,
    val incognito: Boolean = false,
    val nav: ReaderChapterNav? = null,
    val webUrl: String? = null,
    val bookmarks: EbookBookmarks? = null,
)

/**
 * Bookmarks for a reflowable book. Unlike the image reader's page set these are positions (CFI +
 * fraction), so the reader lists them rather than toggling one for "the current page". Null for
 * streamed source chapters, which aren't library books and have nothing book-scoped to bookmark.
 */
class EbookBookmarks(
    val items: List<BookmarkDto>,
    val add: (locator: String?, fraction: Double, label: String?) -> Unit,
    val delete: (id: String) -> Unit,
)

/** One flattened table-of-contents row, as reported by `reader.js`. */
private data class TocEntry(val label: String, val href: String, val depth: Int)

/** Which boundary the between-chapters overlay is showing. */
private enum class Boundary { START, END }

private val eventJson = Json { ignoreUnknownKeys = true }

/**
 * Reader for reflowable ebooks (EPUB, MOBI/KF8, FB2), rendering the book with the same foliate-js
 * engine the web UI uses, hosted in a WebView over a loopback origin (see [EbookHost]). All the
 * chrome — bars, TOC, settings, bookmarks, cross-chapter navigation — is native Compose and matches
 * the image reader; only the page itself is web content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    source: EbookSource,
    onBack: () -> Unit,
    /** Open this book's series; null when there's no series to open (or no host to navigate). */
    onOpenSeries: (() -> Unit)? = null,
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    val snackbar = rememberSnackbar()
    val orientation = dev.icedtea.kodex.platform.rememberOrientationController()
    val openUrl = dev.icedtea.kodex.platform.rememberUrlOpener()

    val pageAnim by appSettings.ebookPageAnim.collectAsStateSafe()

    var prefs by remember { mutableStateOf<EbookPrefs?>(null) }
    var defaultPrefs by remember { mutableStateOf(EbookPrefs()) }
    var fonts by remember { mutableStateOf<List<CustomFontDto>>(emptyList()) }
    var bundledFonts by remember { mutableStateOf<List<BundledFontDto>>(emptyList()) }
    var handle by remember { mutableStateOf<EbookHostHandle?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Both platforms report Ready immediately, but the gate stays: it is what guarantees the WebView
    // is never mounted before there is an engine to mount it in.
    var engine by remember { mutableStateOf<dev.icedtea.kodex.platform.WebEngineState>(dev.icedtea.kodex.platform.WebEngineState.Preparing(null)) }
    LaunchedEffect(Unit) { dev.icedtea.kodex.platform.ensureWebEngine { engine = it } }

    // Reported by the page on every relocate.
    var fraction by remember { mutableStateOf(source.initialFraction ?: 0.0) }
    var locator by remember { mutableStateOf(source.initialLocator) }
    var chapterLabel by remember { mutableStateOf("") }
    var sectionTotal by remember { mutableStateOf(1) }
    var atStart by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    var toc by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var engineReady by remember { mutableStateOf(false) }

    // ── Read aloud ───────────────────────────────────────────────────────────────────────────────
    // The page produces the text (foliate's TTS blocks) and paints the highlight; the device speaks
    // it. See the read-aloud section of `reader.js` — a WebView has no speechSynthesis on either
    // platform, so unlike the web reader the voice has to live out here.
    val tts = dev.icedtea.kodex.platform.rememberTtsEngine()
    val ttsAvailable by tts.available.collectAsStateSafe()
    val ttsRate by appSettings.ttsRate.collectAsStateSafe()
    val ttsVoice by appSettings.ttsVoice.collectAsStateSafe()
    var ttsOpen by remember { mutableStateOf(false) }
    var ttsPlaying by remember { mutableStateOf(false) }
    var ttsSettingsOpen by remember { mutableStateOf(false) }
    /** The block being read, kept so pause/resume and a voice change can re-speak it. */
    var ttsText by remember { mutableStateOf("") }
    var ttsLang by remember { mutableStateOf("") }
    /**
     * Neither platform engine can pause an utterance, so resuming re-speaks the rest of the block:
     * [ttsSpoken] is how far into the *block* the voice got (absolute), [ttsOffset] where the
     * utterance now speaking starts within it. A reported range maps back with `ttsOffset + start`.
     */
    var ttsSpoken by remember { mutableStateOf(0) }
    var ttsOffset by remember { mutableStateOf(0) }
    /** Shown in the panel in place of the usual readout — currently only "the voice gave up". */
    var ttsNotice by remember { mutableStateOf<String?>(null) }
    /**
     * The settings sheet is open as the *pre-start* picker: nothing is speaking yet and its primary
     * button begins the reading. Opened from the panel instead, it edits a reading already underway.
     */
    var ttsStartPrompt by remember { mutableStateOf(false) }

    var chrome by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var tocOpen by remember { mutableStateOf(false) }
    var chaptersOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var transition by remember { mutableStateOf<Boundary?>(null) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    // The page posts events from the host server's dispatcher; funnel them into the composition.
    val events = remember { Channel<String>(Channel.UNLIMITED) }

    val navigator = rememberWebViewNavigator()

    // Commands go through the host (which the page long-polls), not the WebView's evaluateJavaScript
    // — see EbookHostHandle.send.
    fun call(command: String) = handle?.send(command)

    // ── Read-aloud controls ──────────────────────────────────────────────────────────────────────
    /**
     * Speaks the current block from [from] characters in — the whole of it when resuming from 0.
     * [rate]/[voiceId] are parameters because a settings change has to take effect on this very call:
     * the collected state behind them only catches up on the next recomposition.
     */
    fun speakFrom(from: Int, rate: Float = ttsRate, voiceId: String? = ttsVoice) {
        val text = ttsText
        if (text.isBlank()) return
        ttsNotice = null
        val start = from.coerceIn(0, text.length)
        ttsOffset = start
        ttsSpoken = start
        tts.speak(text.substring(start), ttsLang, rate, voiceId)
    }

    fun stopTts() {
        ttsPlaying = false
        tts.stop()
        call(CMD_TTS_STOP)
        ttsText = ""
        ttsSpoken = 0
        ttsOffset = 0
    }

    /** A line worth hearing a voice say — long enough to judge it, short enough not to be a wait. */
    val ttsSample = "This is how this voice will read your book."

    /**
     * Speaks the sample with the settings being chosen. Playback (if any) stops first: there is one
     * voice, and hearing the sample over the book would tell you nothing about either. With
     * [ttsPlaying] false the engine's progress events are ignored, so the sample can't advance the
     * book — pressing play afterwards resumes from the word the reading had reached.
     */
    fun previewVoice(rate: Float = ttsRate, voiceId: String? = ttsVoice) {
        ttsPlaying = false
        ttsNotice = null
        tts.stop()
        tts.speak(ttsSample, ttsLang, rate, voiceId)
    }

    fun toggleTts() {
        if (ttsOpen) {
            ttsOpen = false
            stopTts()
            return
        }
        // Choose the voice and the speed *before* the first word: the settings are per-device and
        // whatever was left over from the last book is rarely what this one wants. The sheet's
        // "Start reading" is what actually turns the voice on.
        chrome = true
        ttsStartPrompt = true
        ttsSettingsOpen = true
    }

    /** The pre-start picker's primary action. */
    fun startTts() {
        ttsStartPrompt = false
        ttsSettingsOpen = false
        tts.stop() // a sample still playing would be talking over the first block
        ttsOpen = true
        ttsPlaying = true
        // The page answers with the first block from the visible page.
        call(CMD_TTS_START)
    }

    fun toggleTtsPlaying() {
        if (ttsPlaying) {
            ttsPlaying = false
            tts.stop()
            return
        }
        ttsPlaying = true
        if (ttsText.isBlank()) call(CMD_TTS_START) else speakFrom(ttsSpoken)
    }

    // ── Boot: resolve settings, then stand up the host and hand the URL to the WebView ────────────
    LaunchedEffect(source.seriesId, server?.id) {
        val s = server ?: return@LaunchedEffect
        val resolved = resolveEbookPrefs(api, s.baseUrl, s.apiKey, source.seriesId)
        defaultPrefs = resolved.default
        fonts = runCatching { api.customFonts(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        // The shipped OFL faces come from the server too — same files the web reader uses, so a book
        // set to one of them in the browser renders in that font here instead of a system substitute.
        // An older server without the endpoint just leaves the list empty.
        bundledFonts = runCatching { api.bundledFonts(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        prefs = resolved.effective
    }

    // What THEME_AUTO follows. Read off the app's own scheme rather than the system directly, so a
    // book set to Auto matches the app in front of the user even when they have pinned light or dark
    // in Appearance; with Appearance left on System (the default) this is the system theme.
    val appIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val bootPrefs = prefs
    LaunchedEffect(bootPrefs != null, server?.id, engine) {
        val s = server ?: return@LaunchedEffect
        val p = bootPrefs ?: return@LaunchedEffect
        if (engine !is dev.icedtea.kodex.platform.WebEngineState.Ready) return@LaunchedEffect
        if (handle != null) return@LaunchedEffect
        val config = buildJsonObject {
            put("format", source.format)
            // Only a source chapter's images are remote (and only it names a provider to fetch them
            // as), so only it asks the page to route them through the host's proxy.
            put("imageProxy", source.origin is EbookOrigin.SourceChapter)
            put("initialLocator", source.initialLocator)
            put("initialFraction", source.initialFraction)
            putJsonObject("prefs") { p.forPage(appIsDark).putInto(this) }
            put("pageAnim", pageAnim)
            put(
                "fonts",
                buildJsonArray {
                    fonts.forEach { f ->
                        add(
                            buildJsonObject {
                                put("id", f.id)
                                put("family", f.family)
                                put("format", f.format)
                            },
                        )
                    }
                },
            )
            // Handed over whole: `reader.js` builds the @font-face rules straight from the server's
            // descriptors, so re-subsetting a face on the server needs no change out here.
            put(
                "bundledFonts",
                buildJsonArray {
                    bundledFonts.forEach { f ->
                        add(
                            buildJsonObject {
                                put("id", f.id)
                                put("family", f.family)
                                put("fallback", f.fallback)
                                put(
                                    "faces",
                                    buildJsonArray {
                                        f.faces.forEach { face ->
                                            add(
                                                buildJsonObject {
                                                    put("file", face.file)
                                                    put("weight", face.weight)
                                                    put("style", face.style)
                                                    put("unicodeRange", face.unicodeRange)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        handle = runCatching {
            EbookHost.open(
                EbookHostSession(
                    baseUrl = s.baseUrl,
                    apiKey = s.apiKey,
                    origin = source.origin,
                    bootConfigJson = config.toString(),
                    onEvent = { events.trySend(it) },
                ),
            )
        }.getOrElse {
            failure = "Couldn't start the reader (${it.message ?: "unknown error"})."
            null
        }
    }

    DisposableEffect(handle) {
        // Bind the value, not the state: `onDispose { handle?.dispose() }` would re-read the state at
        // teardown, so the null→real transition would tear down the effect and dispose the handle it
        // had just been given — releasing the token before the WebView ever requested reader.html.
        val current = handle
        onDispose { current?.dispose() }
    }

    // ── Events from the page ─────────────────────────────────────────────────────────────────────
    LaunchedEffect(events) {
        for (raw in events) {
            val obj = runCatching { eventJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
            when (obj["type"]?.jsonPrimitive?.content) {
                "ready" -> {
                    engineReady = true
                    sectionTotal = obj["sectionTotal"]?.jsonPrimitive?.intOrNull ?: 1
                    // Known before anything is read, so the voice picker can lead with the voices
                    // that actually speak this book's language.
                    ttsLang = obj["lang"]?.jsonPrimitive?.content.orEmpty()
                    toc = obj["toc"]?.let { el ->
                        runCatching {
                            el.jsonArrayOrEmpty().map { row ->
                                val o = row.jsonObject
                                TocEntry(
                                    label = o["label"]?.jsonPrimitive?.content.orEmpty(),
                                    href = o["href"]?.jsonPrimitive?.content.orEmpty(),
                                    depth = o["depth"]?.jsonPrimitive?.intOrNull ?: 0,
                                )
                            }
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }

                "relocate" -> {
                    fraction = obj["fraction"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    locator = obj["cfi"]?.jsonPrimitive?.contentOrNullSafe()
                    chapterLabel = obj["chapter"]?.jsonPrimitive?.content.orEmpty()
                    sectionTotal = obj["sectionTotal"]?.jsonPrimitive?.intOrNull ?: sectionTotal
                    atStart = obj["atStart"]?.jsonPrimitive?.booleanOrNull ?: false
                    atEnd = obj["atEnd"]?.jsonPrimitive?.booleanOrNull ?: false
                }

                // A tap inside the book toggles the bars, matching the image reader.
                "tap" -> if (!settingsOpen && !tocOpen && !chaptersOpen && !bookmarksOpen) chrome = !chrome

                "key" -> when (obj["key"]?.jsonPrimitive?.content) {
                    "ArrowLeft" -> events.trySend(SYNTHETIC_PREV)
                    "ArrowRight", " " -> events.trySend(SYNTHETIC_NEXT)
                    "Escape" -> if (transition != null) transition = null else onBack()
                }

                // A swipe that ran off the first or last page. foliate clamps that gesture to the
                // book's own bounds and turns nothing, so the page hands the attempt over here —
                // where the neighbouring chapters are known — down the same path as a key or a
                // toolbar press.
                "edge" -> when (obj["dir"]?.jsonPrimitive?.content) {
                    "next" -> events.trySend(SYNTHETIC_NEXT)
                    "prev" -> events.trySend(SYNTHETIC_PREV)
                }

                // One block of text to read aloud. Speaking it is what drives the reading forward:
                // the engine's Done below asks the page for the next one.
                "tts-block" -> {
                    ttsText = obj["text"]?.jsonPrimitive?.content.orEmpty()
                    obj["lang"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { ttsLang = it }
                    ttsSpoken = 0
                    ttsOffset = 0
                    if (ttsPlaying) speakFrom(0)
                }

                // Nothing left to read — the end of the book, not merely of a chapter.
                "tts-end" -> {
                    stopTts()
                    ttsOpen = false
                }

                "error" -> failure = obj["message"]?.jsonPrimitive?.content ?: "Couldn't open this book."

                // Raised by the key handler above, so keyboard turns take the same boundary path as taps.
                SYNTHETIC_PREV_TYPE -> if (atStart && source.nav?.prev != null) transition = Boundary.START else call(CMD_PREV)
                SYNTHETIC_NEXT_TYPE -> if (atEnd && source.nav?.next != null) transition = Boundary.END else call(CMD_NEXT)
            }
        }
    }

    // ── Read aloud: what the voice reports ───────────────────────────────────────────────────────
    LaunchedEffect(tts) {
        tts.events.collect { event ->
            when (event) {
                // The word being spoken, mapped back to an offset into the whole block so the page
                // can find the foliate mark for it and highlight/scroll to it.
                is dev.icedtea.kodex.platform.TtsEvent.Range -> {
                    if (!ttsPlaying) return@collect
                    ttsSpoken = ttsOffset + event.start
                    call(ttsMarkCommand(ttsSpoken))
                }

                dev.icedtea.kodex.platform.TtsEvent.Done -> {
                    if (!ttsPlaying) return@collect
                    call(CMD_TTS_DONE)
                }

                // Reported in the panel rather than over the book: the voice failing is no reason to
                // take away the page being read.
                is dev.icedtea.kodex.platform.TtsEvent.Failed -> {
                    ttsPlaying = false
                    tts.stop()
                    ttsNotice = event.message
                }
            }
        }
    }

    // The voice is a device-wide service, so it keeps reading a book that has closed unless stopped.
    DisposableEffect(tts) { onDispose { tts.stop() } }

    // System playback controls (Android notification / iOS lock screen) for as long as read aloud is
    // on. Listening happens with the reader off screen and the phone in a pocket, so there has to be
    // a way to pause that isn't "find the app again" — and on Android the foreground service behind
    // that notification is also what stops the system killing us mid-chapter.
    dev.icedtea.kodex.platform.TtsMediaControls(
        active = ttsOpen,
        playing = ttsPlaying,
        title = source.subtitle ?: source.title,
        subtitle = listOfNotNull(
            source.subtitle?.let { source.title },
            chapterLabel.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onPlayPause = { toggleTtsPlaying() },
        onSkip = { delta -> call(ttsSkipCommand(delta, paused = !ttsPlaying)) },
        onStop = { ttsOpen = false; stopTts() },
    )

    // ── Progress ─────────────────────────────────────────────────────────────────────────────────
    // Debounced like the web's 600ms, and persisted on the session scope so the write survives the
    // reader closing (a plain LaunchedEffect is cancelled on dispose, dropping the final save).
    LaunchedEffect(fraction, locator) {
        if (!engineReady) return@LaunchedEffect
        delay(600)
        session.persistDetached { source.onPersist(fraction, locator, sectionTotal, fraction >= 0.999) }
    }
    val latest by rememberUpdatedState(Triple(fraction, locator, sectionTotal))
    DisposableEffect(engineReady) {
        onDispose {
            if (engineReady) {
                val (f, l, total) = latest
                session.persistDetached { source.onPersist(f, l, total, f >= 0.999) }
            }
        }
    }

    // Auto-hide the bars after 3s, as the image reader does. Any sheet being open pins them.
    LaunchedEffect(chrome, fraction, settingsOpen, tocOpen, chaptersOpen, bookmarksOpen) {
        if (chrome && !settingsOpen && !tocOpen && !chaptersOpen && !bookmarksOpen) {
            delay(3000.milliseconds)
            chrome = false
        }
    }

    fun update(next: EbookPrefs) {
        prefs = next
        call(prefsCommand(next.forPage(appIsDark)))
        val s = server ?: return
        // Detached, like the progress saves: a settings write started on the composition's scope is
        // cancelled the moment the reader is disposed, and leaving right after changing something is
        // exactly when people leave — which silently dropped the change they had just made.
        session.persistSetting(snackbar, "Couldn't save reader settings.") { saveEbookOverride(api, s.baseUrl, s.apiKey, source.seriesId, next) }
    }

    // Auto has to keep up with the app it follows: the phone crossing into night mode (or the user
    // changing the theme in Appearance) re-themes the page under the reader that is already open.
    // Re-sent rather than reloaded, so it costs a repaint and not the reading position.
    LaunchedEffect(appIsDark, prefs?.theme, handle) {
        val p = prefs ?: return@LaunchedEffect
        if (p.theme == THEME_AUTO) call(prefsCommand(p.forPage(appIsDark)))
    }

    // Turning past either end raises the between-chapters overlay when a sibling exists; the page
    // itself never knows about neighbouring chapters, so the decision has to happen here.
    fun goPrev() = if (atStart && source.nav?.prev != null) transition = Boundary.START else call(CMD_PREV)
    fun goNext() = if (atEnd && source.nav?.next != null) transition = Boundary.END else call(CMD_NEXT)

    fun confirmTransition() {
        when (transition) {
            Boundary.END -> source.nav?.next?.open(ReaderEdge.FIRST)
            Boundary.START -> source.nav?.prev?.open(ReaderEdge.LAST)
            null -> {}
        }
        transition = null
    }

    val theme = (prefs ?: EbookPrefs()).resolvedTheme(appIsDark)
    val pageBg = ebookPageColor(theme)

    // Same rule as the image reader: the toolbar is what sits behind the status bar while the chrome
    // is up, and it follows the app's theme; with the chrome hidden it is the reader page, which has
    // a theme of its own. Neither one alone gets the icon colour right in both states.
    StatusBarIcons(
        darkIcons = if (chrome) {
            MaterialTheme.colorScheme.readerBarColor.luminance() > 0.5f
        } else {
            pageBg.luminance() > 0.5f
        },
    )

    // As in the image reader: the system bars hide and show with the reader's own chrome.
    SystemBarsHidden(hidden = !chrome)

    Box(Modifier.fillMaxSize().background(pageBg)) {
        val url = handle?.readerUrl
        if (url != null) {
            val webState = rememberWebViewState(url)
            DisposableEffect(webState) {
                webState.webSettings.apply {
                    isJavaScriptEnabled = true
                    // The page paints its own themed background; a transparent/!matching one flashes
                    // white between chapter loads in sepia and dark.
                    backgroundColor = pageBg
                    supportZoom = false
                }
                onDispose { }
            }
            WebView(
                state = webState,
                modifier = Modifier.fillMaxSize(),
                captureBackPresses = false,
                navigator = navigator,
            )
            // Keep this up until the page reports `ready`, not merely until the WebView finishes
            // loading the document: the HTML lands in milliseconds, but foliate then fetches the
            // manifest, pulls the spine and paginates. Tying it to loadingState left the reader
            // showing a blank page for that whole stretch. Opaque, so the empty view never shows
            // through.
            if (!engineReady && failure == null) {
                Box(
                    Modifier.fillMaxSize().background(pageBg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (failure == null) {
            // Unreachable on Android/iOS (both are Ready at once), but a platform that has to fetch
            // an engine should say so rather than spin indefinitely.
            when (val e = engine) {
                is dev.icedtea.kodex.platform.WebEngineState.Preparing ->
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (e.progress != null) {
                            CircularProgressIndicator(progress = { e.progress }, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Preparing the reader… ${(e.progress * 100).roundToInt()}%",
                                color = ebookTextColor(theme),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                is dev.icedtea.kodex.platform.WebEngineState.RestartRequired ->
                    Text(
                        "The reader finished installing — restart Kodex to use it.",
                        color = ebookTextColor(theme),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                is dev.icedtea.kodex.platform.WebEngineState.Failed ->
                    Text(
                        e.message,
                        color = ebookTextColor(theme),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                dev.icedtea.kodex.platform.WebEngineState.Ready ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            }
        }

        failure?.let { message ->
            Box(Modifier.fillMaxSize().background(pageBg).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(message, color = ebookTextColor(theme), textAlign = TextAlign.Center)
            }
        }

        // ── Chrome ───────────────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            EbookTopBar(
                title = source.title,
                subtitle = source.subtitle,
                hasBookmarks = source.bookmarks != null,
                // Only once the device reports a working voice: a phone with no engine installed
                // would otherwise offer a button that can never say anything.
                canReadAloud = ttsAvailable && engineReady,
                readingAloud = ttsOpen,
                onToggleReadAloud = { toggleTts() },
                onOpenBookmarks = { bookmarksOpen = true },
                onBack = onBack,
            )
        }

        if (source.incognito) IncognitoBadge(Modifier.align(Alignment.TopCenter))

        // The read-aloud panel rides above the seek bar and, unlike it, does not auto-hide: playback
        // controls that vanish mid-paragraph would be a trap. With the bars gone it drops to the
        // screen edge and takes over their navigation-bar inset.
        Column(Modifier.align(Alignment.BottomCenter)) {
            if (ttsOpen) {
                EbookTtsBar(
                    playing = ttsPlaying,
                    label = ttsNotice ?: "Read aloud · ${formatRate(ttsRate)}",
                    modifier = if (chrome) Modifier else Modifier.navigationBarsPadding(),
                    onPlayPause = { toggleTtsPlaying() },
                    onSkip = { delta -> call(ttsSkipCommand(delta, paused = !ttsPlaying)) },
                    onSettings = { ttsSettingsOpen = true },
                    onClose = { toggleTts() },
                )
            }
            AnimatedVisibility(
                visible = chrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                EbookBottomBar(
                    chapterLabel = chapterLabel,
                    percent = ((scrub ?: (fraction * 100).toFloat())).roundToInt(),
                    sliderValue = scrub ?: (fraction * 100).toFloat(),
                    // Skip buttons move between *books/chapters of the series*, exactly as the image
                    // reader's do. They used to jump spine sections instead, which read as the same
                    // control doing two different things — and was dead weight for a source chapter,
                    // whose ephemeral EPUB has only ever one section. Jumping within a book is what the
                    // contents sheet is for.
                    canPrevChapter = source.nav?.prev != null,
                    canNextChapter = source.nav?.next != null,
                    hasChapters = !source.nav?.chapters.isNullOrEmpty(),
                    hasToc = toc.isNotEmpty(),
                    webEnabled = source.webUrl != null,
                    flow = (prefs ?: EbookPrefs()).flow,
                    onPrevPage = { goPrev() },
                    onNextPage = { goNext() },
                    onPrevChapter = { source.nav?.prev?.open(ReaderEdge.FIRST) },
                    onNextChapter = { source.nav?.next?.open(ReaderEdge.FIRST) },
                    onScrub = { scrub = it },
                    onScrubEnd = {
                        val target = (scrub ?: it) / 100f
                        scrub = null
                        call(fractionCommand(target.toDouble()))
                    },
                    onOpenToc = { tocOpen = true },
                    onOpenChapters = { chaptersOpen = true },
                    onOpenWeb = { source.webUrl?.let(openUrl) },
                    onSetFlow = { update((prefs ?: EbookPrefs()).copy(flow = it)) },
                    onOpenSettings = { settingsOpen = true },
                    onOpenSeries = onOpenSeries,
                )
            }
        }

        // Quiet progress pill while the bars are hidden — the same at-a-glance readout the web keeps.
        // The read-aloud panel occupies that spot when it's up.
        if (!chrome && engineReady && !ttsOpen) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
                Text(
                    listOfNotNull(chapterLabel.takeIf { it.isNotBlank() }, "${(fraction * 100).roundToInt()}%").joinToString(" · "),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }

        transition?.let { boundary ->
            val isNext = boundary == Boundary.END
            val target = if (isNext) source.nav?.next else source.nav?.prev
            if (target != null) {
                // The same screen the image reader shows, driven the same way. There is no pager to
                // put it in here, so it lays over the page and reads the drag itself: carrying on in
                // the direction that got you here commits, dragging back returns to the text.
                val commitThreshold = with(LocalDensity.current) { 72.dp.toPx() }
                ChapterTransitionPage(
                    isNext = isNext,
                    currentTitle = source.subtitle ?: source.title,
                    siblingTitle = target.title,
                    seriesTitle = source.title,
                    onContinue = { confirmTransition() },
                    background = pageBg,
                    modifier = Modifier.pointerInput(boundary, commitThreshold) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                // Forward is a leftward drag, the same as turning a page.
                                val forward = -drag
                                if (isNext && forward >= commitThreshold) confirmTransition()
                                else if (!isNext && forward <= -commitThreshold) confirmTransition()
                                else if (kotlin.math.abs(drag) >= commitThreshold) transition = null
                            },
                        ) { _, amount -> drag += amount }
                    },
                )
            }
        }
    }

    if (settingsOpen) {
        // Taller than the app's other sheets: this one is a panel of settings, and the page behind it
        // is a page of text you are not reading while adjusting how it looks.
        KodexBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            fraction = SETTINGS_SHEET_FRACTION,
        ) {
            EbookSettingsSheet(
                prefs = prefs ?: EbookPrefs(),
                fonts = fonts,
                bundledFonts = bundledFonts,
                orientation = orientation.orientation,
                onOrientation = orientation::set,
                pageAnim = pageAnim,
                onPageAnim = { appSettings.setEbookPageAnim(it); call(animCommand(it)) },
                onChange = ::update,
                onSaveDefault = {
                    val s = server ?: return@EbookSettingsSheet
                    val p = prefs ?: return@EbookSettingsSheet
                    session.persistSetting(snackbar, "Couldn't save reader settings.") { saveEbookDefault(api, s.baseUrl, s.apiKey, p) }
                },
                onReset = {
                    val s = server ?: return@EbookSettingsSheet
                    // With a series, reset drops the override and the book follows the user default
                    // again. Without one there is no override to drop — the default key is where this
                    // book's settings live — so reset means the built-in prefs, which is what
                    // resetEbookOverride stores. Showing `defaultPrefs` there would leave the sheet
                    // claiming one thing while the server held another.
                    val resetTo = if (source.seriesId != null) defaultPrefs else EbookPrefs()
                    if (source.seriesId == null) defaultPrefs = resetTo
                    prefs = resetTo
                    call(prefsCommand(resetTo.forPage(appIsDark)))
                    session.persistSetting(snackbar, "Couldn't save reader settings.") { resetEbookOverride(api, s.baseUrl, s.apiKey, source.seriesId) }
                },
            )
        }
    }

    if (ttsSettingsOpen) {
        // Voices matching the book's language first: a phone can carry dozens, and scrolling past
        // forty of them to find the one that can pronounce this book is the whole difficulty here.
        val voices = remember(ttsAvailable, ttsLang) {
            val all = if (ttsAvailable) tts.voices() else emptyList()
            val language = ttsLang.substringBefore('-').lowercase()
            if (language.isBlank()) all else all.sortedByDescending { it.locale.lowercase().startsWith(language) }
        }
        KodexBottomSheet(
            onDismissRequest = {
                ttsSettingsOpen = false
                // Dismissed instead of started: nothing was turned on, so stop the sample if one is
                // still talking.
                if (ttsStartPrompt) {
                    ttsStartPrompt = false
                    tts.stop()
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TtsSettingsSheet(
                voices = voices,
                voiceId = ttsVoice,
                rate = ttsRate,
                // Neither engine can retune an utterance already speaking, so the change is applied
                // by re-speaking from the last word said rather than the top of the paragraph.
                onVoice = { appSettings.setTtsVoice(it); if (ttsPlaying) speakFrom(ttsSpoken, voiceId = it) },
                onRate = { appSettings.setTtsRate(it); if (ttsPlaying) speakFrom(ttsSpoken, rate = it) },
                onPreview = { previewVoice() },
                onStart = if (ttsStartPrompt) ({ startTts() }) else null,
            )
        }
    }

    if (tocOpen) {
        KodexBottomSheet(onDismissRequest = { tocOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TocSheet(toc, current = chapterLabel) { href ->
                call(hrefCommand(href))
                tocOpen = false
            }
        }
    }

    if (chaptersOpen) {
        KodexBottomSheet(onDismissRequest = { chaptersOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ChapterListSheet(source.nav?.chapters.orEmpty()) { chaptersOpen = false }
        }
    }

    if (bookmarksOpen) {
        val marks = source.bookmarks
        if (marks == null) {
            bookmarksOpen = false
        } else {
            KodexBottomSheet(onDismissRequest = { bookmarksOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                BookmarksSheet(
                    items = marks.items,
                    onAdd = {
                        marks.add(locator, fraction, chapterLabel.takeIf { it.isNotBlank() })
                        bookmarksOpen = false
                    },
                    onOpen = { b ->
                        val cfi = b.locator
                        if (cfi != null) call(hrefCommand(cfi)) else call(fractionCommand(b.fraction ?: 0.0))
                        bookmarksOpen = false
                    },
                    onDelete = { marks.delete(it) },
                )
            }
        }
    }
}

// Keyboard turns re-enter the event loop as these, so they take the same boundary-aware path as the
// on-screen buttons instead of duplicating the logic in the key handler.
private const val SYNTHETIC_PREV_TYPE = "kdx-prev"
private const val SYNTHETIC_NEXT_TYPE = "kdx-next"
private const val SYNTHETIC_PREV = """{"type":"$SYNTHETIC_PREV_TYPE"}"""
private const val SYNTHETIC_NEXT = """{"type":"$SYNTHETIC_NEXT_TYPE"}"""

// ── Chrome ───────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun EbookTopBar(
    title: String,
    subtitle: String?,
    hasBookmarks: Boolean,
    canReadAloud: Boolean,
    readingAloud: Boolean,
    onToggleReadAloud: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onBack: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = content) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = content.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (canReadAloud) {
            IconButton(onClick = onToggleReadAloud) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    contentDescription = if (readingAloud) "Stop reading aloud" else "Read aloud",
                    tint = if (readingAloud) MaterialTheme.colorScheme.primary else content,
                )
            }
        }
        if (hasBookmarks) {
            IconButton(onClick = onOpenBookmarks) {
                Icon(Icons.Outlined.Bookmark, contentDescription = "Bookmarks", tint = content)
            }
        }
    }
}

/**
 * Playback controls for read aloud. Deliberately thin and always-on-screen: it is the only way back
 * out of a voice that is talking, so it must not ride the auto-hiding chrome.
 */
@Composable
private fun EbookTtsBar(
    playing: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    /** +1 / -1 — the next or previous paragraph. */
    onSkip: (Int) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSkip(-1) }) {
            Icon(Icons.Outlined.FastRewind, contentDescription = "Previous paragraph", tint = content)
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = content,
            )
        }
        IconButton(onClick = { onSkip(1) }) {
            Icon(Icons.Outlined.FastForward, contentDescription = "Next paragraph", tint = content)
        }
        Text(
            label,
            Modifier.weight(1f).padding(horizontal = 4.dp),
            color = content.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Speed, contentDescription = "Voice settings", tint = content)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Stop reading aloud", tint = content)
        }
    }
}

/**
 * Voice and speed. The voices are the device's, so this is a device setting rather than one of the
 * per-series display prefs — an engine id from one phone means nothing on another.
 */
@Composable
private fun TtsSettingsSheet(
    voices: List<TtsVoice>,
    voiceId: String?,
    rate: Float,
    onVoice: (String?) -> Unit,
    onRate: (Float) -> Unit,
    onPreview: () -> Unit,
    /** Non-null when this is the pre-start picker: the button that begins the reading. */
    onStart: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(bottom = 24.dp)) {
        Text(
            "Read aloud",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Speed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TTS_RATES.forEach { value ->
                    FilterChip(
                        selected = kotlin.math.abs(value - rate) < 0.01f,
                        onClick = { onRate(value) },
                        label = { Text(formatRate(value)) },
                    )
                }
            }
        }
        // Above the list rather than under it: the list is as long as the phone has voices, and a
        // button that has to be scrolled to is a button nobody finds.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Preview") }
            if (onStart != null) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start reading") }
            }
        }
        Text(
            "Voice",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        )
        LazyColumn {
            item {
                TtsVoiceRow(
                    label = "Automatic",
                    detail = "Match the book's language",
                    selected = voiceId == null,
                    onClick = { onVoice(null) },
                )
            }
            // Deliberately un-keyed: the ids come from the device's speech engine, and engines do
            // repeat a name across locale variants — a duplicate key is a crash, and there is nothing
            // here for a key to preserve anyway.
            items(voices) { voice ->
                TtsVoiceRow(
                    label = voice.name,
                    detail = voice.locale,
                    selected = voice.id == voiceId,
                    onClick = { onVoice(voice.id) },
                )
            }
        }
    }
}

@Composable
private fun TtsVoiceRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** "1×", "1.25×" — trailing zeroes dropped, since most rates are whole or quarter steps. */
private fun formatRate(rate: Float): String {
    val rounded = (rate * 100).roundToInt()
    val whole = rounded / 100
    val cents = rounded % 100
    return when {
        cents == 0 -> "$whole×"
        cents % 10 == 0 -> "$whole.${cents / 10}×"
        else -> "$whole.${cents.toString().padStart(2, '0')}×"
    }
}

@Composable
private fun EbookBottomBar(
    chapterLabel: String,
    percent: Int,
    sliderValue: Float,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    hasChapters: Boolean,
    hasToc: Boolean,
    webEnabled: Boolean,
    flow: String,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onOpenToc: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenWeb: () -> Unit,
    onSetFlow: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSeries: (() -> Unit)?,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    val raised = MaterialTheme.colorScheme.readerBarRaisedColor
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = raised,
        contentColor = content,
        disabledContainerColor = raised,
        disabledContentColor = content.disabled(),
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).navigationBarsPadding()) {
        // Readout: which chapter you're in and how far through the book you are. A reflowable book has
        // no page numbers, so this replaces the image reader's "12 / 180".
        Text(
            listOfNotNull(chapterLabel.takeIf { it.isNotBlank() }, "$percent%").joinToString(" · "),
            color = content.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        // Seek row: skip-chapter buttons at the ends, page turns just inside them, scrubber between.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onPrevChapter, enabled = canPrevChapter, colors = buttonColors) {
                Icon(Icons.Outlined.SkipPrevious, "Previous book")
            }
            IconButton(onClick = onPrevPage) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page", tint = content) }
            Slider(
                value = sliderValue,
                onValueChange = onScrub,
                onValueChangeFinished = { onScrubEnd(sliderValue) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            IconButton(onClick = onNextPage) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page", tint = content) }
            FilledIconButton(onClick = onNextChapter, enabled = canNextChapter, colors = buttonColors) {
                Icon(Icons.Outlined.SkipNext, "Next book")
            }
        }
        // Toolbar row: contents · chapter list · open in web · read mode · settings.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "Book contents" = this file's own TOC; "Books" = the other books of the
            // series. The list icon means the latter in the image reader too, so it stays put here.
            onOpenSeries?.let { open -> ToolbarButton(Icons.Outlined.Book, "Series details", onClick = open) }
            ToolbarButton(Icons.AutoMirrored.Outlined.ChromeReaderMode, "Book contents", enabled = hasToc, onClick = onOpenToc)
            ToolbarButton(Icons.AutoMirrored.Outlined.ViewList, "Books", enabled = hasChapters, onClick = onOpenChapters)
            ToolbarButton(Icons.Outlined.Public, "Open in web", enabled = webEnabled, onClick = onOpenWeb)
            ReadModeButton(EBOOK_READ_MODES, flow, onSetFlow)
            ToolbarButton(Icons.Outlined.Settings, "Settings", onClick = onOpenSettings)
        }
    }
}

/**
 * A reflowable book's reading modes: foliate lays the text out either in pages or as one scroll.
 * Column count is a paginated sub-choice, so it stays in the settings sheet rather than doubling the
 * length of this menu.
 */
private val EBOOK_READ_MODES = listOf(
    ReaderModeOption(FLOW_PAGINATED, "Paged", Icons.Outlined.AutoStories),
    ReaderModeOption(FLOW_SCROLLED, "Scrolled", Icons.Outlined.ArrowDownward),
)

/** The book's own table of contents (foliate's), flattened; indent carries the nesting. */
@Composable
private fun TocSheet(items: List<TocEntry>, current: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 24.dp)) {
        Text(
            "Book contents",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn {
            items(items) { entry ->
                val active = entry.label.isNotBlank() && entry.label == current
                Text(
                    entry.label.ifBlank { "—" },
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSelect(entry.href) }
                        .padding(start = (20 + entry.depth * 14).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BookmarksSheet(
    items: List<BookmarkDto>,
    onAdd: () -> Unit,
    onOpen: (BookmarkDto) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 24.dp)) {
        Text(
            "Bookmarks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Bookmark this spot", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn {
            items(items, key = { it.id }) { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(b) }.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        b.label?.takeIf { it.isNotBlank() } ?: "${((b.fraction ?: 0.0) * 100).roundToInt()}%",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onDelete(b.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EbookSettingsSheet(
    prefs: EbookPrefs,
    fonts: List<CustomFontDto>,
    bundledFonts: List<BundledFontDto>,
    orientation: dev.icedtea.kodex.platform.ScreenOrientation,
    onOrientation: (dev.icedtea.kodex.platform.ScreenOrientation) -> Unit,
    pageAnim: String,
    onPageAnim: (String) -> Unit,
    onChange: (EbookPrefs) -> Unit,
    onSaveDefault: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            // Everything but the footer scrolls as one run: two tabs still did not fit a phone
            // between the pinned chrome, so they bought a mode and a tab row's worth of height
            // without buying the thing tabs are for. The cards carry the grouping instead.
            //
            // fill = false so a short sheet stays short; the cap in KodexBottomSheet turns a long one
            // into a scroll rather than pushing the footer off the bottom.
            Modifier.weight(1f, fill = false)
                .nestedScroll(rememberSheetScrollGuard())
                .verticalScroll(rememberScrollState())
                .padding(start = SheetGutter, end = SheetGutter, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReaderSettingsHeader("Display", "Applies to this series")
            // Theme leads: it is the setting people reopen this sheet for, and the only one whose
            // effect is visible without reading a label.
            val appIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val swatches = remember(appIsDark) { ebookThemeSwatches(appIsDark) }
            ReaderSettingsSwatches("Theme", prefs.theme, swatches) { onChange(prefs.copy(theme = it)) }
            ReaderSettingsSectionLabel("Text")
            EbookTextSettings(prefs, fonts, bundledFonts, onChange)
            ReaderSettingsSectionLabel("Page")
            EbookPageSettings(prefs, pageAnim, onPageAnim, orientation, onOrientation, onChange)
        }
        ReaderSettingsFooter(
            note = "\"Save as default\" applies these to every book without its own settings.",
            onSaveDefault = onSaveDefault,
            onReset = onReset,
        )
    }
}

/**
 * Reading themes as swatches, so the choice is visible rather than named. Auto is painted in whatever
 * it currently resolves to ([appIsDark]) — the tile is then a preview like the other three rather than
 * a mystery box, and it repaints when the app changes appearance.
 */
private fun ebookThemeSwatches(appIsDark: Boolean): List<ReaderSwatch> {
    fun swatch(id: String, label: String, paint: String = id) =
        ReaderSwatch(id, label, ebookPageColor(paint), ebookTextColor(paint), "Aa")
    return listOf(
        swatch(THEME_AUTO, "Auto", paint = if (appIsDark) THEME_DARK else THEME_SEPIA),
        swatch(THEME_LIGHT, "Light"),
        swatch(THEME_SEPIA, "Sepia"),
        swatch(THEME_DARK, "Dark"),
    )
}

/** Everything about the type itself: which face, how big, how loose, how it sits on the line. */
@Composable
private fun EbookTextSettings(
    prefs: EbookPrefs,
    fonts: List<CustomFontDto>,
    bundledFonts: List<BundledFontDto>,
    onChange: (EbookPrefs) -> Unit,
) {
    // The same list the web offers: the book's own fonts, the server's shipped faces, then the
    // user's uploads. The generic `serif`/`sans`/`mono` stacks are legacy values — still rendered
    // when a stored pref names one, but only shown here so that pref has a visible selection.
    val fontOptions = buildList {
        add(FONT_PUBLISHER to "Publisher")
        bundledFonts.forEach { add("bundled:${it.id}" to it.family.ifBlank { it.id }) }
        fonts.forEach { add("custom:${it.id}" to it.family.ifBlank { "Custom" }) }
        val legacy = LEGACY_FONT_STACKS[prefs.fontFamily]
        if (legacy != null) add(prefs.fontFamily to legacy)
    }
    ReaderSettingsCard {
        ReaderSettingsChips("Font", prefs.fontFamily, fontOptions) { onChange(prefs.copy(fontFamily = it)) }
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
    }
    ReaderSettingsCard {
        ReaderSettingsSegmented(
            "Alignment",
            prefs.textAlign,
            listOf(ALIGN_AUTO to "Auto", ALIGN_LEFT to "Left", ALIGN_JUSTIFY to "Justify"),
        ) {
            onChange(prefs.copy(textAlign = it))
        }
        ReaderSettingsSegmented(
            "Paragraph indent",
            prefs.indent?.toString() ?: "auto",
            listOf("auto" to "Auto", "0.0" to "None", "1.0" to "1em", "2.0" to "2em"),
        ) {
            onChange(prefs.copy(indent = if (it == "auto") null else it.toDoubleOrNull()))
        }
    }
}

/** How the text is laid out on the screen: flow, columns, turn animation, margin, orientation. */
@Composable
private fun EbookPageSettings(
    prefs: EbookPrefs,
    pageAnim: String,
    onPageAnim: (String) -> Unit,
    orientation: dev.icedtea.kodex.platform.ScreenOrientation,
    onOrientation: (dev.icedtea.kodex.platform.ScreenOrientation) -> Unit,
    onChange: (EbookPrefs) -> Unit,
) {
    ReaderSettingsCard {
        ReaderSettingsSegmented("Layout", prefs.flow, listOf(FLOW_PAGINATED to "Paged", FLOW_SCROLLED to "Scrolled")) {
            onChange(prefs.copy(flow = it))
        }
        // Column count and the turn animation only mean anything when the text is paginated.
        if (prefs.flow == FLOW_PAGINATED) {
            ReaderSettingsSegmented(
                "Columns",
                prefs.columns,
                listOf(COLUMNS_AUTO to "Auto", COLUMNS_ONE to "One", COLUMNS_TWO to "Two"),
            ) {
                onChange(prefs.copy(columns = it))
            }
            // Unlike the rest of the sheet this one is a device setting, not a per-series override —
            // hence its own callback rather than a copy() of the prefs.
            ReaderSettingsSegmented(
                "Page turn",
                pageAnim,
                listOf(PAGE_ANIM_SLIDE to "Slide", PAGE_ANIM_FLIP to "Flip", PAGE_ANIM_NONE to "None"),
                caption = "Applies to every book on this device.",
                onSelect = onPageAnim,
            )
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
    ReaderSettingsCard {
        // Lives here now that the toolbar's rotation button became the reading-mode picker. Unlike the
        // rest of the sheet this isn't a stored preference — it lasts as long as the reader is open.
        ReaderSettingsSegmented(
            "Screen orientation",
            orientation.name,
            dev.icedtea.kodex.platform.ScreenOrientation.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
            caption = "Lasts until you leave the reader.",
        ) { picked ->
            onOrientation(dev.icedtea.kodex.platform.ScreenOrientation.valueOf(picked))
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────────────────────────

/** Page colours, matching `reader.js`'s THEME_COLORS so the chrome and the page agree. */
private fun ebookPageColor(theme: String): Color = when (theme) {
    THEME_SEPIA -> Color(0xFFF4ECD8)
    THEME_DARK -> Color(0xFF181A1B)
    else -> Color(0xFFFFFFFF)
}

private fun ebookTextColor(theme: String): Color = when (theme) {
    THEME_SEPIA -> Color(0xFF5B4636)
    THEME_DARK -> Color(0xFFCFD2D6)
    else -> Color(0xFF1B1B1B)
}

private fun EbookPrefs.putInto(builder: kotlinx.serialization.json.JsonObjectBuilder) = with(builder) {
    put("flow", flow)
    put("theme", theme)
    put("fontFamily", fontFamily)
    put("columns", columns)
    put("fontSize", fontSize)
    put("lineHeight", lineHeight)
    put("margin", margin)
    put("textAlign", textAlign)
    put("indent", indent)
}

// ── Commands ─────────────────────────────────────────────────────────────────────────────────────
// The wire format `reader.js` dispatches on. Built as JSON rather than JS source so nothing the user
// or the book can influence (a CFI, a TOC href) is ever concatenated into executable text.

private const val CMD_PREV = """{"cmd":"prev"}"""
private const val CMD_NEXT = """{"cmd":"next"}"""

// Read aloud. The page owns the text and the highlight; these are the four things it needs told —
// start, "the voice finished that block", "it is saying the word at this character", and stop.
private const val CMD_TTS_START = """{"cmd":"ttsStart"}"""
private const val CMD_TTS_STOP = """{"cmd":"ttsStop"}"""
private const val CMD_TTS_DONE = """{"cmd":"ttsDone"}"""

/** [delta] is +1/-1 by paragraph; [paused] lets the page highlight the whole block it lands on. */
private fun ttsSkipCommand(delta: Int, paused: Boolean): String =
    buildJsonObject {
        put("cmd", "ttsSkip")
        put("delta", delta)
        put("paused", paused)
    }.toString()

private fun ttsMarkCommand(charIndex: Int): String =
    buildJsonObject {
        put("cmd", "ttsMark")
        put("charIndex", charIndex)
    }.toString()

private fun prefsCommand(prefs: EbookPrefs): String =
    buildJsonObject {
        put("cmd", "prefs")
        putJsonObject("prefs") { prefs.putInto(this) }
    }.toString()

private fun animCommand(value: String): String =
    buildJsonObject {
        put("cmd", "anim")
        put("value", value)
    }.toString()

private fun fractionCommand(fraction: Double): String =
    buildJsonObject {
        put("cmd", "goToFraction")
        put("fraction", fraction)
    }.toString()

private fun hrefCommand(href: String): String =
    buildJsonObject {
        put("cmd", "goTo")
        put("href", href)
    }.toString()


private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonElement.jsonArrayOrEmpty(): List<kotlinx.serialization.json.JsonElement> =
    (this as? kotlinx.serialization.json.JsonArray) ?: emptyList()

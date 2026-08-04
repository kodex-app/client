package app.kodex.client.ui.reader.ebook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookmarkDto
import app.kodex.client.network.CustomFontDto
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.reader.ChapterListSheet
import app.kodex.client.ui.reader.ChapterTransitionOverlay
import app.kodex.client.ui.reader.IncognitoBadge
import app.kodex.client.ui.reader.ReaderChapterNav
import app.kodex.client.ui.reader.ReaderEdge
import app.kodex.client.ui.reader.ToolbarButton
import app.kodex.client.ui.reader.disabled
import app.kodex.client.ui.reader.readerBarColor
import app.kodex.client.ui.reader.readerBarContentColor
import app.kodex.client.ui.reader.readerBarRaisedColor
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * Everything the ebook reader needs, independent of whether the book is a library download or a
 * chapter streamed from a content source. The image reader's [app.kodex.client.ui.reader.ReaderSource]
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
    source: EbookSource,
    onBack: () -> Unit,
    /** Open this book's series; null when there's no series to open (or no host to navigate). */
    onOpenSeries: (() -> Unit)? = null,
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    val orientation = app.kodex.client.platform.rememberOrientationController()
    val openUrl = app.kodex.client.platform.rememberUrlOpener()

    var prefs by remember { mutableStateOf<EbookPrefs?>(null) }
    var defaultPrefs by remember { mutableStateOf(EbookPrefs()) }
    var fonts by remember { mutableStateOf<List<CustomFontDto>>(emptyList()) }
    var handle by remember { mutableStateOf<EbookHostHandle?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Desktop fetches Chromium on first use; mobile is Ready immediately. Gate the host on it so the
    // WebView is never mounted before there's an engine to mount it in.
    var engine by remember { mutableStateOf<app.kodex.client.platform.WebEngineState>(app.kodex.client.platform.WebEngineState.Preparing(null)) }
    LaunchedEffect(Unit) { app.kodex.client.platform.ensureWebEngine { engine = it } }

    // Reported by the page on every relocate.
    var fraction by remember { mutableStateOf(source.initialFraction ?: 0.0) }
    var locator by remember { mutableStateOf(source.initialLocator) }
    var chapterLabel by remember { mutableStateOf("") }
    var sectionTotal by remember { mutableStateOf(1) }
    var atStart by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    var toc by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var engineReady by remember { mutableStateOf(false) }

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

    // ── Boot: resolve settings, then stand up the host and hand the URL to the WebView ────────────
    LaunchedEffect(source.seriesId, server?.id) {
        val s = server ?: return@LaunchedEffect
        val resolved = resolveEbookPrefs(api, s.baseUrl, s.apiKey, source.seriesId)
        defaultPrefs = resolved.default
        fonts = runCatching { api.customFonts(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        prefs = resolved.effective
    }

    val bootPrefs = prefs
    LaunchedEffect(bootPrefs != null, server?.id, engine) {
        val s = server ?: return@LaunchedEffect
        val p = bootPrefs ?: return@LaunchedEffect
        if (engine !is app.kodex.client.platform.WebEngineState.Ready) return@LaunchedEffect
        if (handle != null) return@LaunchedEffect
        val config = buildJsonObject {
            put("format", source.format)
            put("initialLocator", source.initialLocator)
            put("initialFraction", source.initialFraction)
            putJsonObject("prefs") { p.putInto(this) }
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

                "error" -> failure = obj["message"]?.jsonPrimitive?.content ?: "Couldn't open this book."

                // Raised by the key handler above, so keyboard turns take the same boundary path as taps.
                SYNTHETIC_PREV_TYPE -> if (atStart && source.nav?.prev != null) transition = Boundary.START else call(CMD_PREV)
                SYNTHETIC_NEXT_TYPE -> if (atEnd && source.nav?.next != null) transition = Boundary.END else call(CMD_NEXT)
            }
        }
    }

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
            delay(3000)
            chrome = false
        }
    }

    fun update(next: EbookPrefs) {
        prefs = next
        call(prefsCommand(next))
        val s = server ?: return
        scope.launch { runCatching { saveEbookOverride(api, s.baseUrl, s.apiKey, source.seriesId, next) } }
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

    val theme = prefs?.theme ?: THEME_LIGHT
    val pageBg = ebookPageColor(theme)

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
            // Desktop's first ebook pulls down Chromium; say so rather than spin indefinitely.
            when (val e = engine) {
                is app.kodex.client.platform.WebEngineState.Preparing ->
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

                is app.kodex.client.platform.WebEngineState.RestartRequired ->
                    Text(
                        "The reader finished installing — restart Kodex to use it.",
                        color = ebookTextColor(theme),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                is app.kodex.client.platform.WebEngineState.Failed ->
                    Text(
                        e.message,
                        color = ebookTextColor(theme),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                app.kodex.client.platform.WebEngineState.Ready ->
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
                onOpenBookmarks = { bookmarksOpen = true },
                onBack = onBack,
            )
        }

        if (source.incognito) IncognitoBadge(Modifier.align(Alignment.TopCenter))

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                orientation = orientation.orientation,
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
                onCycleOrientation = { orientation.cycle() },
                onOpenSettings = { settingsOpen = true },
                onOpenSeries = onOpenSeries,
            )
        }

        // Quiet progress pill while the bars are hidden — the same at-a-glance readout the web keeps.
        if (!chrome && engineReady) {
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
                ChapterTransitionOverlay(
                    isNext = isNext,
                    title = target.title,
                    onConfirm = { confirmTransition() },
                    onDismiss = { transition = null },
                )
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = rememberModalBottomSheetState()) {
            EbookSettingsSheet(
                prefs = prefs ?: EbookPrefs(),
                fonts = fonts,
                onChange = ::update,
                onSaveDefault = {
                    val s = server ?: return@EbookSettingsSheet
                    val p = prefs ?: return@EbookSettingsSheet
                    scope.launch { runCatching { saveEbookDefault(api, s.baseUrl, s.apiKey, p) } }
                },
                onReset = {
                    val s = server ?: return@EbookSettingsSheet
                    prefs = defaultPrefs
                    call(prefsCommand(defaultPrefs))
                    scope.launch { runCatching { resetEbookOverride(api, s.baseUrl, s.apiKey, source.seriesId) } }
                },
            )
        }
    }

    if (tocOpen) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }, sheetState = rememberModalBottomSheetState()) {
            TocSheet(toc, current = chapterLabel) { href ->
                call(hrefCommand(href))
                tocOpen = false
            }
        }
    }

    if (chaptersOpen) {
        ModalBottomSheet(onDismissRequest = { chaptersOpen = false }, sheetState = rememberModalBottomSheetState()) {
            ChapterListSheet(source.nav?.chapters.orEmpty()) { chaptersOpen = false }
        }
    }

    if (bookmarksOpen) {
        val marks = source.bookmarks
        if (marks == null) {
            bookmarksOpen = false
        } else {
            ModalBottomSheet(onDismissRequest = { bookmarksOpen = false }, sheetState = rememberModalBottomSheetState()) {
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
    onOpenBookmarks: () -> Unit,
    onBack: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = content) }
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
        if (hasBookmarks) {
            IconButton(onClick = onOpenBookmarks) {
                Icon(app.kodex.client.ui.icons.BookmarkIcon, contentDescription = "Bookmarks", tint = content)
            }
        }
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
    orientation: app.kodex.client.platform.ScreenOrientation,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onOpenToc: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenWeb: () -> Unit,
    onCycleOrientation: () -> Unit,
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
                Icon(app.kodex.client.ui.icons.SkipPreviousIcon, "Previous book")
            }
            IconButton(onClick = onPrevPage) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous page", tint = content) }
            Slider(
                value = sliderValue,
                onValueChange = onScrub,
                onValueChangeFinished = { onScrubEnd(sliderValue) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            IconButton(onClick = onNextPage) { Icon(Icons.Filled.KeyboardArrowRight, "Next page", tint = content) }
            FilledIconButton(onClick = onNextChapter, enabled = canNextChapter, colors = buttonColors) {
                Icon(app.kodex.client.ui.icons.SkipNextIcon, "Next book")
            }
        }
        // Toolbar row: contents · chapter list · open in web · orientation · settings.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "Book contents" = this file's own TOC; "Books" = the other books of the
            // series. The list icon means the latter in the image reader too, so it stays put here.
            onOpenSeries?.let { open -> ToolbarButton(Icons.Filled.Info, "Series details", onClick = open) }
            ToolbarButton(app.kodex.client.ui.icons.BookContentsIcon, "Book contents", enabled = hasToc, onClick = onOpenToc)
            ToolbarButton(Icons.AutoMirrored.Filled.List, "Books", enabled = hasChapters, onClick = onOpenChapters)
            ToolbarButton(app.kodex.client.ui.icons.OpenInWebIcon, "Open in web", enabled = webEnabled, onClick = onOpenWeb)
            ToolbarButton(
                app.kodex.client.ui.icons.OrientationIcon,
                "Screen orientation",
                tint = if (orientation == app.kodex.client.platform.ScreenOrientation.AUTO) content else MaterialTheme.colorScheme.primary,
                onClick = onCycleOrientation,
            )
            ToolbarButton(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
        }
    }
}

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
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                        Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.error)
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
    onChange: (EbookPrefs) -> Unit,
    onSaveDefault: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Display", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        ThemeRow(prefs.theme) { onChange(prefs.copy(theme = it)) }

        ChipRow("Layout", prefs.flow, listOf(FLOW_PAGINATED to "Paged", FLOW_SCROLLED to "Scrolled")) {
            onChange(prefs.copy(flow = it))
        }
        // Column count only means anything when the text is paginated.
        if (prefs.flow == FLOW_PAGINATED) {
            ChipRow("Columns", prefs.columns, listOf(COLUMNS_AUTO to "Auto", COLUMNS_ONE to "One", COLUMNS_TWO to "Two")) {
                onChange(prefs.copy(columns = it))
            }
        }

        val fontOptions = buildList {
            add(FONT_PUBLISHER to "Publisher")
            add("serif" to "Serif")
            add("sans" to "Sans")
            add("mono" to "Mono")
            fonts.forEach { add("custom:${it.id}" to it.family.ifBlank { "Custom" }) }
        }
        ChipRow("Font", prefs.fontFamily, fontOptions) { onChange(prefs.copy(fontFamily = it)) }

        StepperRow(
            label = "Text size",
            value = "${prefs.fontSize}%",
            onDecrease = { onChange(prefs.copy(fontSize = (prefs.fontSize - 10).coerceAtLeast(FONT_SIZE_MIN))) },
            onIncrease = { onChange(prefs.copy(fontSize = (prefs.fontSize + 10).coerceAtMost(FONT_SIZE_MAX))) },
        )
        StepperRow(
            label = "Line height",
            value = "${prefs.lineHeight}%",
            onDecrease = { onChange(prefs.copy(lineHeight = (prefs.lineHeight - 10).coerceAtLeast(LINE_HEIGHT_MIN))) },
            onIncrease = { onChange(prefs.copy(lineHeight = (prefs.lineHeight + 10).coerceAtMost(LINE_HEIGHT_MAX))) },
        )

        ChipRow("Alignment", prefs.textAlign, listOf(ALIGN_AUTO to "Auto", ALIGN_LEFT to "Left", ALIGN_JUSTIFY to "Justify")) {
            onChange(prefs.copy(textAlign = it))
        }
        ChipRow(
            "Indent",
            prefs.indent?.toString() ?: "auto",
            listOf("auto" to "Auto", "0.0" to "None", "1.0" to "1em", "2.0" to "2em"),
        ) {
            onChange(prefs.copy(indent = if (it == "auto") null else it.toDoubleOrNull()))
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Margin", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Slider(
                value = prefs.margin.toFloat(),
                onValueChange = { onChange(prefs.copy(margin = it.roundToInt().coerceIn(MARGIN_MIN, MARGIN_MAX))) },
                valueRange = MARGIN_MIN.toFloat()..MARGIN_MAX.toFloat(),
                steps = (MARGIN_MAX / 8) - 1,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSaveDefault, modifier = Modifier.weight(1f)) { Text("Save as default") }
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
        Text(
            "Changes apply to this series. \"Save as default\" applies them to every book without its own setting.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Reading-theme picker, drawn as real swatches so the choice is visible rather than named. */
@Composable
private fun ThemeRow(value: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Theme", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(THEME_LIGHT to "Light", THEME_SEPIA to "Sepia", THEME_DARK to "Dark").forEach { (id, label) ->
                val selected = value == id
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(width = 46.dp, height = 34.dp)
                            .background(ebookPageColor(id), RoundedCornerShape(8.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa", color = ebookTextColor(id), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (v, l) ->
                FilterChip(selected = value == v, onClick = { onSelect(v) }, label = { Text(l) })
            }
        }
    }
}

/** Compact [−] value [+] row, for the settings that step rather than slide. */
@Composable
private fun StepperRow(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onDecrease) { Icon(app.kodex.client.ui.icons.MinusIcon, "Decrease $label") }
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onIncrease) { Icon(Icons.Filled.Add, "Increase $label") }
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

private fun prefsCommand(prefs: EbookPrefs): String =
    buildJsonObject {
        put("cmd", "prefs")
        putJsonObject("prefs") { prefs.putInto(this) }
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

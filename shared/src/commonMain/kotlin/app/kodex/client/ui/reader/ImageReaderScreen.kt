package app.kodex.client.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.platform.StatusBarIcons
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.catalog.imageErrorText
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Everything the reader needs, independent of whether pages come from a book or a streamed source. */
class ReaderSource(
    val title: String, // top-bar line 1 — the series, when it's known
    val subtitle: String? = null, // top-bar line 2 — this chapter/book within the series
    val pageCount: Int,
    val initialPage: Int, // 1-based
    val kind: String, // "comic" | "pdf" — chooses the settings bucket + defaults
    val seriesId: String?, // for per-series settings persistence
    val apiKey: String,
    val pageUrlFor: (page: Int) -> String, // 1-based page → image URL
    val onPersist: suspend (page: Int, completed: Boolean) -> Unit,
    val incognito: Boolean = false, // drives the persistent incognito badge
    val nav: ReaderChapterNav? = null, // sibling chapters for cross-chapter navigation
    val webUrl: String? = null, // "open in web" target (the web UI reader page), or null to disable
    val bookmarks: ReaderBookmarks? = null, // page bookmarks, or null to hide the top-bar action
)

/**
 * Page bookmarks for the reader's top bar. Kodex bookmarks a *page* of a book, so the action marks
 * wherever you are rather than the whole chapter. Null on sources that can't hold one — a streamed
 * source chapter isn't a library book, and the bookmark API is book-scoped.
 */
class ReaderBookmarks(
    val pages: Set<Int>, // 1-based pages currently bookmarked
    val toggle: (page: Int) -> Unit,
)

/** Which page a newly-opened chapter starts on. */
enum class ReaderEdge { FIRST, LAST }

/** A sibling chapter the reader can jump to. [open] switches to it starting at [ReaderEdge]. */
class ReaderChapterRef(
    val title: String,
    val open: (ReaderEdge) -> Unit,
    val preloadPageUrl: ((page: Int) -> String)? = null, // 1-based; for cross-chapter prefetch
)

/** One entry in the chapter-list menu. */
class ReaderChapterItem(val title: String, val active: Boolean, val open: () -> Unit)

/** Cross-chapter navigation context for the reader. */
class ReaderChapterNav(
    val prev: ReaderChapterRef? = null,
    val next: ReaderChapterRef? = null,
    val chapters: List<ReaderChapterItem> = emptyList(),
)

/** Which end of the chapter the between-chapters page is showing. */
private enum class Boundary { START, END }

private fun bgColor(bg: String): Color = when (bg) {
    BG_WHITE -> Color(0xFFFFFFFF)
    BG_BLACK -> Color(0xFF0B0B0C)
    else -> Color(0xFF4A4D52)
}

/**
 * Full image reader (comic/PDF) porting the web ImageReader: paged (single/double, LTR/RTL, tap-to-turn,
 * pinch-zoom) and continuous webtoon modes, auto webtoon detection, fit modes, background, a page
 * scrubber, and a settings sheet with per-series persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageReaderScreen(
    session: SessionManager,
    api: KodexApi,
    source: ReaderSource,
    onBack: () -> Unit,
    /** Open this book's series; null when there's no series to open (or no host to navigate). */
    onOpenSeries: (() -> Unit)? = null,
) {
    val server by session.activeServer.collectAsStateSafe()
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val orientation = app.kodex.client.platform.rememberOrientationController()
    val openUrl = app.kodex.client.platform.rememberUrlOpener()

    var prefs by remember { mutableStateOf<ReaderPrefs?>(null) }
    var defaultPrefs by remember { mutableStateOf(defaultReaderPrefs(source.kind)) }
    var autoMode by remember { mutableStateOf<String?>(null) } // resolved paged/continuous when mode==auto
    var preload by remember { mutableStateOf(PRELOAD_DEFAULT) }
    var autoScroll by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var transition by remember { mutableStateOf<Boundary?>(null) } // between-chapters overlay
    var chaptersOpen by remember { mutableStateOf(false) }

    // Load persisted prefs (series override → user default → built-in) + global preload count.
    LaunchedEffect(source.seriesId, source.kind, server?.id) {
        val s = server ?: return@LaunchedEffect
        val resolved = resolveReaderPrefs(api, s.baseUrl, s.apiKey, source.kind, source.seriesId)
        defaultPrefs = resolved.default
        prefs = resolved.effective
        preload = resolved.preload
    }

    // Auto-detect webtoon vs paged by sampling page aspect ratios (Mihon long-strip heuristic).
    LaunchedEffect(prefs?.mode, source.pageCount) {
        val p = prefs ?: return@LaunchedEffect
        if (p.mode == MODE_AUTO && autoMode == null && source.pageCount > 0) {
            autoMode = detectMode(context, source)
        }
    }

    fun update(next: ReaderPrefs) {
        if (next.mode == MODE_AUTO) autoMode = null // re-probe when Auto reselected
        prefs = next
        val s = server ?: return
        scope.launch { runCatching { saveReaderOverride(api, s.baseUrl, s.apiKey, source.kind, source.seriesId, next) } }
    }

    val p = prefs
    val effectiveMode = when {
        p == null -> null
        p.mode == MODE_AUTO -> autoMode // null while probing
        else -> p.mode
    }

    var page by remember { mutableStateOf(source.initialPage.coerceIn(1, source.pageCount.coerceAtLeast(1))) }
    // Save reading progress on the session scope (not this composition) so it survives page turns AND the
    // reader closing — a plain LaunchedEffect is cancelled on dispose, which dropped the final save.
    LaunchedEffect(page) {
        if (source.pageCount <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(400) // coalesce rapid turns / continuous scrolling
        session.persistDetached { source.onPersist(page, page >= source.pageCount) }
    }
    // Guaranteed final save when leaving the reader (covers exiting right after the last page).
    val latestPage by androidx.compose.runtime.rememberUpdatedState(page)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (source.pageCount > 0) session.persistDetached { source.onPersist(latestPage, latestPage >= source.pageCount) }
        }
    }

    // Auto-scroll only makes sense in continuous mode; cancel it elsewhere.
    LaunchedEffect(effectiveMode) { if (effectiveMode != MODE_CONTINUOUS) autoScroll = false }

    // Prefetch the next [preload] pages into Coil's cache so turns feel instant; spill over into the
    // next chapter's first pages near the end.
    LaunchedEffect(page, preload, effectiveMode, source.pageCount) {
        if (preload <= 0 || source.pageCount <= 0) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        fun prefetch(url: String) {
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", source.apiKey).build())
                    .build(),
            )
        }
        val step = if (effectiveMode == MODE_PAGED && p?.isDouble == true) 2 else 1
        var count = 0
        for (n in (page + step) until (page + step + preload)) {
            if (n > source.pageCount) break
            prefetch(source.pageUrlFor(n)); count++
        }
        val nextUrl = source.nav?.next?.preloadPageUrl
        if (nextUrl != null && count < preload) {
            for (n in 1..(preload - count)) prefetch(nextUrl(n))
        }
    }

    fun updatePreload(v: Int) {
        preload = v.coerceIn(0, PRELOAD_MAX)
        val s = server ?: return
        scope.launch { runCatching { savePreloadCount(api, s.baseUrl, s.apiKey, preload) } }
    }

    fun confirmBoundary(boundary: Boundary) {
        when (boundary) {
            Boundary.END -> source.nav?.next?.open(ReaderEdge.FIRST)
            Boundary.START -> source.nav?.prev?.open(ReaderEdge.LAST)
        }
        transition = null
    }

    fun confirmTransition() {
        transition?.let { confirmBoundary(it) }
    }

    // Central page-turn for the arrow buttons, tap zones and keys. In paged mode, turning past either
    // end moves onto the between-chapters screen (when a sibling exists) and turning again from there
    // commits — the same two steps a swipe takes. Continuous mode simply scrolls.
    fun advance(delta: Int) {
        val pp = p ?: return
        transition?.let { b ->
            val sameWay = (b == Boundary.END && delta > 0) || (b == Boundary.START && delta < 0)
            if (sameWay) {
                confirmBoundary(b)
                return
            }
            // Turning the other way steps back off the boundary screen onto the pages.
            transition = null
            return
        }
        val step = if (effectiveMode == MODE_PAGED && pp.isDouble) 2 else 1
        val target = page + delta * step
        if (effectiveMode == MODE_PAGED) {
            when {
                source.pageCount > 0 && target > source.pageCount -> if (source.nav?.next != null) transition = Boundary.END
                target < 1 -> if (source.nav?.prev != null) transition = Boundary.START
                else -> page = target.coerceIn(1, source.pageCount)
            }
        } else {
            page = target.coerceIn(1, source.pageCount)
        }
    }

    var chrome by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Auto-hide chrome after 3s of no interaction. Bookmarking counts as interaction, so the bar
    // doesn't slide away in the instant between tapping the action and seeing it take.
    LaunchedEffect(chrome, page, settingsOpen, source.bookmarks?.pages) {
        if (chrome && !settingsOpen) {
            kotlinx.coroutines.delay(3000)
            chrome = false
        }
    }

    // What sits behind the status bar changes with the chrome: the toolbar when it's up, the page
    // background when it isn't. The toolbar follows the app's theme, not the reader background, so
    // deciding from the background alone left white icons on a near-white bar in a light theme.
    val barIsLight = MaterialTheme.colorScheme.readerBarColor.luminance() > 0.5f
    StatusBarIcons(darkIcons = if (chrome) barIsLight else p?.bg == BG_WHITE)

    // Physical keyboard navigation (desktop + hardware keyboards). Arrows/space turn pages, Esc exits.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(effectiveMode) { if (effectiveMode != null) runCatching { focusRequester.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (p == null) Color.Black else bgColor(p.bg))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (p == null || effectiveMode == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the transition overlay is up, a forward key confirms it; Escape dismisses.
                if (transition != null) {
                    when (e.key) {
                        Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter -> confirmTransition()
                        Key.Escape -> transition = null
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                if (effectiveMode == MODE_CONTINUOUS) {
                    if (e.key == Key.Escape) { onBack(); return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                val rtl = p.isRtl
                when (e.key) {
                    Key.DirectionRight -> advance(if (rtl) -1 else 1)
                    Key.DirectionLeft -> advance(if (rtl) 1 else -1)
                    Key.DirectionDown, Key.Spacebar -> advance(1)
                    Key.DirectionUp -> advance(-1)
                    Key.Escape -> onBack()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        if (p == null || effectiveMode == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            val step = if (effectiveMode == MODE_PAGED && p.isDouble) 2 else 1
            val rangeEnd = (page + step - 1).coerceAtMost(source.pageCount)
            // Short label for the navigator capsule (the total sits at its other end); the pill shown
            // while the chrome is hidden has no such frame, so it spells the whole thing out.
            val pageLabel = if (step == 2 && rangeEnd > page) "$page–$rangeEnd" else "$page"
            val indicator = "$pageLabel / ${source.pageCount}"

            if (effectiveMode == MODE_CONTINUOUS) {
                ContinuousReader(source, p, page, autoScroll, onPage = { page = it }, onToggleChrome = { chrome = !chrome }, onAutoScrollEnd = { autoScroll = false })
            } else {
                PagedReader(
                    source, p, page,
                    transition = transition,
                    onJump = { page = it },
                    onTransition = { transition = it },
                    onConfirmBoundary = ::confirmBoundary,
                    onToggleChrome = { chrome = !chrome },
                    onTurnPage = { forward -> advance(if (forward) 1 else -1) },
                )
            }

            // Paged edge page-turn buttons (reading order), shown with the chrome.
            if (effectiveMode == MODE_PAGED) {
                AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.CenterStart), enter = leftEdgeEnter, exit = leftEdgeExit) {
                    EdgeButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page") { advance(if (p.isRtl) 1 else -1) }
                }
                AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.CenterEnd), enter = rightEdgeEnter, exit = rightEdgeExit) {
                    EdgeButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page") { advance(if (p.isRtl) -1 else 1) }
                }
            }

            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.TopCenter), enter = topBarEnter, exit = topBarExit) {
                TopBar(
                    title = source.title,
                    subtitle = source.subtitle,
                    bookmarked = source.bookmarks?.pages?.contains(page),
                    onToggleBookmark = { source.bookmarks?.toggle(page) },
                    onBack = onBack,
                )
            }
            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter), enter = bottomBarEnter, exit = bottomBarExit) {
                BottomBar(
                    page = page,
                    pageCount = source.pageCount,
                    pageLabel = pageLabel,
                    rtl = p.isRtl,
                    continuous = effectiveMode == MODE_CONTINUOUS,
                    autoScroll = autoScroll,
                    hasChapters = (source.nav?.chapters?.size ?: 0) > 1,
                    canPrevChapter = source.nav?.prev != null,
                    canNextChapter = source.nav?.next != null,
                    webEnabled = source.webUrl != null,
                    orientation = orientation.orientation,
                    onPrevChapter = { source.nav?.prev?.open(ReaderEdge.FIRST) },
                    onNextChapter = { source.nav?.next?.open(ReaderEdge.FIRST) },
                    onOpenChapters = { chaptersOpen = true },
                    onOpenWeb = { source.webUrl?.let { openUrl(it) } },
                    onCycleOrientation = { orientation.cycle() },
                    onOpenSettings = { settingsOpen = true },
                    onOpenSeries = onOpenSeries,
                    onToggleAutoScroll = { autoScroll = !autoScroll },
                    onSeek = { target -> page = target.coerceIn(1, source.pageCount) },
                    onOpenPicker = { pickerOpen = true },
                )
            }
            // Page pill while the chrome is hidden.
            AnimatedVisibility(!chrome, modifier = Modifier.align(Alignment.BottomCenter), enter = pagePillEnter, exit = pagePillExit) { PagePill(indicator) }
            // Persistent incognito badge (always visible, above the auto-hiding chrome).
            if (source.incognito) IncognitoBadge(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))

        }
    }

    if (chaptersOpen && p != null && source.nav != null) {
        ModalBottomSheet(onDismissRequest = { chaptersOpen = false }, sheetState = rememberModalBottomSheetState()) {
            ChapterListSheet(source.nav.chapters) { chaptersOpen = false }
        }
    }

    if (pickerOpen && p != null) {
        PagePickerDialog(source, current = page, onPick = { page = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
    }

    if (settingsOpen && p != null) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = rememberModalBottomSheetState()) {
            SettingsSheet(
                prefs = p,
                effectiveMode = effectiveMode ?: MODE_PAGED,
                autoScroll = autoScroll,
                onToggleAutoScroll = { autoScroll = !autoScroll },
                preload = preload,
                onPreload = ::updatePreload,
                onChange = ::update,
                onSaveDefault = {
                    val s = server ?: return@SettingsSheet
                    scope.launch { runCatching { saveReaderDefault(api, s.baseUrl, s.apiKey, source.kind, p) } }
                },
                onReset = {
                    val s = server ?: return@SettingsSheet
                    scope.launch {
                        runCatching { resetReaderOverride(api, s.baseUrl, s.apiKey, source.kind, source.seriesId) }
                        prefs = defaultPrefs
                        if (defaultPrefs.mode == MODE_AUTO) autoMode = null
                    }
                    settingsOpen = false
                },
            )
        }
    }
}

// ── Paged ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PagedReader(
    source: ReaderSource,
    prefs: ReaderPrefs,
    page: Int,
    transition: Boundary?,
    onJump: (Int) -> Unit,
    onTransition: (Boundary?) -> Unit,
    onConfirmBoundary: (Boundary) -> Unit,
    onToggleChrome: () -> Unit,
    onTurnPage: (forward: Boolean) -> Unit,
) {
    val double = prefs.isDouble
    val contentSlots = if (double) (source.pageCount + 1) / 2 else source.pageCount
    // The between-chapters screens are pages of the pager, not an overlay: you swipe onto one and
    // swipe again to commit, which is what makes the whole reader swipe-driven. They exist only when
    // there is actually a sibling chapter, so otherwise the pager still ends where the content does.
    val leading = if (source.nav?.prev != null) 1 else 0
    val trailing = if (source.nav?.next != null) 1 else 0
    val slotCount = (leading + contentSlots + trailing).coerceAtLeast(1)
    val lastSlot = slotCount - 1

    fun slotOf(p: Int) = leading + (if (double) (p - 1) / 2 else p - 1)
    fun pageOf(slot: Int) = (slot - leading).let { if (double) it * 2 + 1 else it + 1 }

    val pagerState = rememberPagerState(
        initialPage = slotOf(page).coerceIn(0, lastSlot),
        pageCount = { slotCount },
    )
    var zoomedIn by remember { mutableStateOf(false) }

    // Pager settle -> either a page, or one of the boundary screens.
    LaunchedEffect(pagerState, leading, contentSlots) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { slot ->
            when {
                slot < leading -> onTransition(Boundary.START)
                slot >= leading + contentSlots -> onTransition(Boundary.END)
                else -> {
                    onTransition(null)
                    onJump(pageOf(slot))
                }
            }
        }
    }
    // External move (slider, keys, edge buttons) -> scroll the pager to match.
    val targetSlot = when (transition) {
        Boundary.START -> 0
        Boundary.END -> lastSlot
        null -> slotOf(page).coerceIn(0, lastSlot)
    }
    LaunchedEffect(targetSlot) {
        if (targetSlot != pagerState.currentPage) pagerState.scrollToPage(targetSlot)
    }

    // Swiping *past* a boundary screen commits to the sibling chapter. The pager sits at its own edge
    // there, so that drag arrives here unconsumed; released past the threshold it opens the chapter.
    val density = LocalDensity.current
    val edgeThreshold = with(density) { 72.dp.toPx() }
    val edgeCommit = remember(prefs.isRtl, zoomedIn, edgeThreshold, leading, trailing, lastSlot) {
        object : NestedScrollConnection {
            /** Positive is "towards the next page", which is a leftward drag unless the pager is RTL. */
            private val forwardSign = if (prefs.isRtl) 1f else -1f

            /** Plain field, not snapshot state: it changes every frame and nothing composes off it. */
            private var edgeDrag = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // While zoomed the pager consumes nothing, so every pan would look like an edge drag.
                if (zoomedIn || source != NestedScrollSource.UserInput) return Offset.Zero
                edgeDrag += available.x * forwardSign
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val drag = edgeDrag
                edgeDrag = 0f
                val slot = pagerState.settledPage
                if (drag >= edgeThreshold && trailing > 0 && slot == lastSlot) {
                    onConfirmBoundary(Boundary.END)
                } else if (drag <= -edgeThreshold && leading > 0 && slot == 0) {
                    onConfirmBoundary(Boundary.START)
                }
                return Velocity.Zero
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = prefs.isRtl,
        userScrollEnabled = !zoomedIn,
        modifier = Modifier.fillMaxSize().nestedScroll(edgeCommit),
    ) { slot ->
        when {
            slot < leading -> ChapterTransitionPage(
                isNext = false,
                currentTitle = source.subtitle ?: source.title,
                siblingTitle = source.nav?.prev?.title.orEmpty(),
                seriesTitle = source.title,
                onContinue = { onConfirmBoundary(Boundary.START) },
            )

            slot >= leading + contentSlots -> ChapterTransitionPage(
                isNext = true,
                currentTitle = source.subtitle ?: source.title,
                siblingTitle = source.nav?.next?.title.orEmpty(),
                seriesTitle = source.title,
                onContinue = { onConfirmBoundary(Boundary.END) },
            )

            double -> {
                val left = pageOf(slot)
                val right = left + 1
                val pages = listOfNotNull(left, right.takeIf { it <= source.pageCount })
                val ordered = if (prefs.isRtl) pages.reversed() else pages
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    ordered.forEach { n ->
                        PageImage(
                            url = source.pageUrlFor(n),
                            apiKey = source.apiKey,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxHeight().weight(1f),
                        )
                    }
                }
            }

            else -> ZoomablePage(
                url = source.pageUrlFor(pageOf(slot)),
                apiKey = source.apiKey,
                zoom = prefs.zoom,
                tapToTurn = prefs.tapToTurn,
                rtl = prefs.isRtl,
                onZoomChange = { zoomedIn = it },
                onToggleChrome = onToggleChrome,
                onTurn = onTurnPage,
            )
        }
    }
}

@Composable
private fun ZoomablePage(
    url: String,
    apiKey: String,
    zoom: String,
    tapToTurn: Boolean,
    rtl: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    onTurn: (forward: Boolean) -> Unit,
) {
    // Fit-width / original: scrollable page, tap toggles chrome, swipe (parent pager) turns.
    if (zoom == ZOOM_WIDTH || zoom == ZOOM_ORIGINAL) {
        val scroll = if (zoom == ZOOM_ORIGINAL) {
            Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
        } else {
            Modifier.verticalScroll(rememberScrollState())
        }
        Box(
            Modifier.fillMaxSize().then(scroll).pointerInput(url) { detectTapGestures(onTap = { onToggleChrome() }) },
            contentAlignment = Alignment.TopCenter,
        ) {
            PageImage(
                url = url,
                apiKey = apiKey,
                contentScale = if (zoom == ZOOM_WIDTH) ContentScale.FillWidth else ContentScale.None,
                modifier = if (zoom == ZOOM_WIDTH) Modifier.fillMaxWidth() else Modifier,
            )
        }
        return
    }

    // Fit-height: pinch-zoom + pan + double-tap + tap-to-turn zones.
    var scale by remember(url) { mutableStateOf(1f) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(1) }
    LaunchedEffect(scale) { onZoomChange(scale > 1f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(url, tapToTurn) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f },
                    onTap = { pos ->
                        if (tapToTurn && scale <= 1f) {
                            val third = widthPx / 3f
                            when {
                                pos.x < third -> onTurn(rtl)
                                pos.x > widthPx - third -> onTurn(!rtl)
                                else -> onToggleChrome()
                            }
                        } else onToggleChrome()
                    },
                )
            }
            .pointerInput(url) {
                // Only claim gestures when pinching (2+ fingers) or already zoomed; otherwise leave the
                // single-finger drag unconsumed so the parent pager can turn the page.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                if (scale > 1f) { offsetX += panChange.x; offsetY += panChange.y } else { offsetX = 0f; offsetY = 0f }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PageImage(
            url = url,
            apiKey = apiKey,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
            },
        )
    }
}

// ── Continuous (webtoon) ───────────────────────────────────────────────────────────────────────────

/**
 * Height a not-yet-measured page holds open in the continuous reader. A guess by necessity — the size
 * is only known once the image decodes — so it is deliberately tall: too short and the scroll position
 * lurches forward as each page grows into place, which is far more disorienting than a little slack.
 */
private val CONTINUOUS_PLACEHOLDER_HEIGHT = 560.dp

@Composable
private fun ContinuousReader(
    source: ReaderSource,
    prefs: ReaderPrefs,
    page: Int,
    autoScroll: Boolean,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onAutoScrollEnd: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (page - 1).coerceAtLeast(0))
    val density = LocalDensity.current
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { onPage(it + 1) }
    }
    // External page change (slider/keyboard) → scroll there, but never fight an in-progress finger scroll.
    LaunchedEffect(page) {
        if (!listState.isScrollInProgress && (page - 1) != listState.firstVisibleItemIndex) {
            listState.scrollToItem((page - 1).coerceAtLeast(0))
        }
    }
    // Auto-scroll engine: advance the scroll by scrollSpeed (dp/sec) each frame until the end / stopped.
    LaunchedEffect(autoScroll, prefs.scrollSpeed) {
        if (!autoScroll) return@LaunchedEffect
        val pxPerSec = with(density) { prefs.scrollSpeed.dp.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last).coerceAtLeast(0L)) / 1_000_000_000f
            last = now
            val dy = pxPerSec * dt
            val consumed = listState.scrollBy(dy)
            if (dy > 0f && consumed < dy - 0.5f) { onAutoScrollEnd(); break } // hit the bottom
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { onToggleChrome() })
        },
    ) {
        items(source.pageCount) { i ->
            PageImage(
                url = source.pageUrlFor(i + 1),
                apiKey = source.apiKey,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                placeholderHeight = CONTINUOUS_PLACEHOLDER_HEIGHT,
            )
        }
        source.nav?.next?.let { next ->
            item { ContinuousNextTile(next.title) { next.open(ReaderEdge.FIRST) } }
        }
    }
}

/** Footer tile at the bottom of the continuous reader that opens the next chapter. */
@Composable
private fun ContinuousNextTile(title: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(Color.Black.copy(alpha = 0.4f)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Next book", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

// ── Shared image ───────────────────────────────────────────────────────────────────────────────────

/**
 * One page of the reader.
 *
 * A failed page shows why and offers a retry rather than leaving the reader background bare — a
 * blank page is indistinguishable from a slow one, so a chapter that cannot load at all looks
 * identical to a chapter that is still loading. [retry] re-issues the request by changing the key
 * the [ImageRequest] is remembered under, which is what makes Coil refetch rather than serve its
 * cached failure.
 */
@Composable
private fun PageImage(
    url: String,
    apiKey: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    /**
     * Height to hold open while the page has no intrinsic size yet. Needed only where the slot's own
     * height is unbounded — the continuous reader's [LazyColumn] — because there a placeholder that
     * wraps its content measures to the spinner, collapsing the row to nothing and then snapping to
     * full page height on arrival. That shifts everything below it mid-scroll. Null elsewhere: the
     * paged reader and the thumbnail grid already constrain height, so the placeholder just fills it.
     */
    placeholderHeight: Dp? = null,
) {
    val context = LocalPlatformContext.current
    var attempt by remember(url) { mutableStateOf(0) }
    val request = remember(url, attempt) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", apiKey).build())
            // Only a retry gets its own cache key. The first attempt must keep the default (the URL)
            // or it would miss everything the preloader put in the memory cache under that key.
            .memoryCacheKey(if (attempt == 0) null else "$url#$attempt")
            .build()
    }
    val slot = if (placeholderHeight != null) {
        Modifier.fillMaxWidth().height(placeholderHeight)
    } else {
        Modifier.fillMaxSize()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = { PageSpinner(slot) },
        error = { PageErrorPlate(slot, it.result.throwable.imageErrorText()) { attempt++ } },
    )
}

/** Placeholder while a page is in flight. Muted white so it reads on any of the three backgrounds. */
@Composable
private fun PageSpinner(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
    }
}

/** Failure plate for a reader page: dark, centred, and readable over any page background. */
@Composable
private fun PageErrorPlate(modifier: Modifier, message: String, onRetry: () -> Unit) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(44.dp),
            )
            Text(
                "This page didn't load",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text("Retry", color = Color.White) }
        }
    }
}

// ── Chrome ─────────────────────────────────────────────────────────────────────────────────────────

// The chrome slides rather than expands: sliding keeps each bar at its full measured size for the
// whole animation, so the toolbar contents never reflow while appearing/disappearing. Entering is
// slightly slower than leaving (decelerate in, accelerate out) so the bars feel like they settle.
private const val CHROME_IN_MS = 260
private const val CHROME_OUT_MS = 200
private val chromeInSpec = tween<Float>(CHROME_IN_MS, easing = LinearOutSlowInEasing)
private val chromeOutSpec = tween<Float>(CHROME_OUT_MS, easing = FastOutSlowInEasing)
private val chromeInOffset = tween<IntOffset>(CHROME_IN_MS, easing = LinearOutSlowInEasing)
private val chromeOutOffset = tween<IntOffset>(CHROME_OUT_MS, easing = FastOutSlowInEasing)

/** Top bar: slides down from above the status bar. */
private val topBarEnter = slideInVertically(chromeInOffset) { -it } + fadeIn(chromeInSpec)
private val topBarExit = slideOutVertically(chromeOutOffset) { -it } + fadeOut(chromeOutSpec)

/** Bottom bar: slides up from below the navigation bar. */
private val bottomBarEnter = slideInVertically(chromeInOffset) { it } + fadeIn(chromeInSpec)
private val bottomBarExit = slideOutVertically(chromeOutOffset) { it } + fadeOut(chromeOutSpec)

/** Edge page-turn buttons: slide in from their own screen edge. */
private val leftEdgeEnter = slideInHorizontally(chromeInOffset) { -it } + fadeIn(chromeInSpec)
private val leftEdgeExit = slideOutHorizontally(chromeOutOffset) { -it } + fadeOut(chromeOutSpec)
private val rightEdgeEnter = slideInHorizontally(chromeInOffset) { it } + fadeIn(chromeInSpec)
private val rightEdgeExit = slideOutHorizontally(chromeOutOffset) { it } + fadeOut(chromeOutSpec)

/** Page pill: swaps with the bottom bar, so it slides along the same axis but only a short way. */
private val pagePillEnter = slideInVertically(chromeInOffset) { it / 2 } + fadeIn(chromeInSpec)
private val pagePillExit = slideOutVertically(chromeOutOffset) { it / 2 } + fadeOut(chromeOutSpec)

/**
 * Toolbar fill: the app's own elevated surface tone rather than flat black, so the reader chrome
 * matches the rest of the app (and follows the selected palette, Monet, and the AMOLED override).
 * Kept translucent so the page still shows through — light schemes need a touch more opacity than
 * dark ones to stay legible over a bright page.
 */
internal val ColorScheme.readerBarColor: Color
    get() = surfaceContainerHigh.copy(alpha = if (surface.luminance() < 0.5f) 0.90f else 0.95f)

/**
 * Fill for controls that sit *on* a toolbar — the chapter navigator's slider capsule and its skip
 * buttons. One step brighter than [readerBarColor] so they read as raised, and fully opaque so the
 * page never shows through a control the way it does through the bar itself.
 */
internal val ColorScheme.readerBarRaisedColor: Color get() = surfaceContainerHighest

/** Icons/text on the toolbars, paired with [readerBarColor] so they contrast in either scheme. */
internal val ColorScheme.readerBarContentColor: Color get() = onSurface

/** M3 disabled-content alpha, for page-turn arrows at the first/last page. */
internal fun Color.disabled() = copy(alpha = 0.38f)

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    bookmarked: Boolean?, // null → this source has no bookmarks, so the action is hidden
    onToggleBookmark: () -> Unit,
    onBack: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = content) }
        // Series on top, this chapter beneath it — the chapter is the line that changes as you read,
        // so it gets the quieter treatment and the title keeps the top-bar weight.
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
        if (bookmarked != null) {
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else content,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    page: Int,
    pageCount: Int,
    pageLabel: String,
    rtl: Boolean,
    continuous: Boolean,
    autoScroll: Boolean,
    hasChapters: Boolean,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    webEnabled: Boolean,
    orientation: app.kodex.client.platform.ScreenOrientation,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenWeb: () -> Unit,
    onCycleOrientation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSeries: (() -> Unit)?,
    onToggleAutoScroll: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.readerBarColor).navigationBarsPadding()) {
        ChapterNavigator(
            page = page,
            pageCount = pageCount,
            pageLabel = pageLabel,
            rtl = rtl,
            canPrevChapter = canPrevChapter,
            canNextChapter = canNextChapter,
            onPrevChapter = onPrevChapter,
            onNextChapter = onNextChapter,
            onSeek = onSeek,
            onOpenPicker = onOpenPicker,
        )
        // Toolbar row: auto-scroll (continuous only) · chapter list · open in web · orientation · settings.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (continuous) {
                ToolbarButton(
                    if (autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    "Auto-scroll",
                    tint = if (autoScroll) MaterialTheme.colorScheme.primary else content,
                    onClick = onToggleAutoScroll,
                )
            }
            onOpenSeries?.let { open -> ToolbarButton(Icons.Outlined.Book, "Series details", onClick = open) }
            ToolbarButton(Icons.AutoMirrored.Outlined.ViewList, "Books", enabled = hasChapters, onClick = onOpenChapters)
            ToolbarButton(Icons.Outlined.Public, "Open in web", enabled = webEnabled, onClick = onOpenWeb)
            ToolbarButton(
                Icons.Outlined.ScreenRotation,
                "Screen orientation",
                tint = if (orientation == app.kodex.client.platform.ScreenOrientation.AUTO) content else MaterialTheme.colorScheme.primary,
                onClick = onCycleOrientation,
            )
            ToolbarButton(Icons.Outlined.Settings, "Settings", onClick = onOpenSettings)
        }
    }
}

/**
 * Mihon's chapter navigator: skip-chapter buttons flanking a capsule that reads
 * `current ——slider—— total`. The outer row is pinned LTR whatever the locale and only the capsule
 * follows the series' reading direction, so for an RTL manga the slider fills right-to-left while
 * "skip previous" stays under the reader's left thumb — the two buttons swap which chapter they open.
 */
@Composable
private fun ChapterNavigator(
    page: Int,
    pageCount: Int,
    pageLabel: String,
    rtl: Boolean,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val content = MaterialTheme.colorScheme.readerBarContentColor
    val raised = MaterialTheme.colorScheme.readerBarRaisedColor
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = raised,
        contentColor = content,
        disabledContainerColor = raised,
        disabledContentColor = content.disabled(),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = if (rtl) onNextChapter else onPrevChapter,
                enabled = if (rtl) canNextChapter else canPrevChapter,
                colors = buttonColors,
            ) {
                Icon(Icons.Outlined.SkipPrevious, if (rtl) "Next book" else "Previous book")
            }

            if (pageCount > 1) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Row(
                        Modifier.weight(1f).padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(24.dp)).background(raised)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tapping the current page opens the thumbnail picker.
                        Text(
                            pageLabel,
                            color = content,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(onClick = onOpenPicker).padding(vertical = 8.dp),
                        )
                        Slider(
                            value = (page - 1).toFloat(),
                            onValueChange = { onSeek(it.roundToInt() + 1) },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            steps = sliderSteps(pageCount),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(pageCount.toString(), color = content, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            FilledIconButton(
                onClick = if (rtl) onPrevChapter else onNextChapter,
                enabled = if (rtl) canPrevChapter else canNextChapter,
                colors = buttonColors,
            ) {
                Icon(Icons.Outlined.SkipNext, if (rtl) "Previous book" else "Next book")
            }
        }
    }
}

/**
 * Tick marks on the page slider, which make a short chapter's pages individually tappable. Dropped
 * past [SLIDER_TICK_LIMIT] pages, where the ticks merge into a smear and cost more than they give.
 */
private const val SLIDER_TICK_LIMIT = 40
private fun sliderSteps(pageCount: Int): Int = if (pageCount in 2..SLIDER_TICK_LIMIT) pageCount - 2 else 0

@Composable
internal fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.readerBarContentColor,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else MaterialTheme.colorScheme.readerBarContentColor.disabled())
    }
}

/** Semi-transparent circular page-turn button overlaid at the paged reader's edges. */
@Composable
private fun EdgeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.padding(8.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = desc)
    }
}

/** Small centered page pill shown while the chrome is hidden. */
@Composable
private fun PagePill(text: String) {
    Box(Modifier.padding(bottom = 10.dp)) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

/** Indigo "Incognito" pill pinned to the top, above the auto-hiding chrome. */
@Composable
internal fun IncognitoBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 6.dp).background(Color(0xFF4A3F8F).copy(alpha = 0.92f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Incognito", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}


/** Chapter/book list in a bottom sheet; tapping a row jumps to that chapter. */
@Composable
internal fun ChapterListSheet(chapters: List<ReaderChapterItem>, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 24.dp)) {
        Text("Books", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp))
        LazyColumn {
            items(chapters) { ch ->
                Text(
                    ch.title.ifBlank { "—" },
                    color = if (ch.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (ch.active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { if (!ch.active) ch.open(); onClose() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Thumbnail grid to jump to any page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagePickerDialog(source: ReaderSource, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Go to page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    modifier = Modifier.heightIn(max = 480.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..source.pageCount).toList(), key = { it }) { n ->
                        Column(
                            Modifier.clickable { onPick(n) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(112.dp)
                                    .border(
                                        if (n == current) 2.dp else 1.dp,
                                        if (n == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(6.dp),
                                    ),
                            ) {
                                PageImage(source.pageUrlFor(n), source.apiKey, ContentScale.Crop, Modifier.fillMaxSize())
                            }
                            Text("$n", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ── Settings sheet ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSheet(
    prefs: ReaderPrefs,
    effectiveMode: String,
    autoScroll: Boolean,
    onToggleAutoScroll: () -> Unit,
    preload: Int,
    onPreload: (Int) -> Unit,
    onChange: (ReaderPrefs) -> Unit,
    onSaveDefault: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Reader settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        SegRow("Layout", prefs.mode, listOf(MODE_AUTO to "Auto", MODE_PAGED to "Paged", MODE_CONTINUOUS to "Continuous")) {
            onChange(prefs.copy(mode = it))
        }
        SegRow("Fit", prefs.zoom, listOf(ZOOM_HEIGHT to "Height", ZOOM_WIDTH to "Width", ZOOM_ORIGINAL to "Original")) {
            onChange(prefs.copy(zoom = it))
        }
        if (effectiveMode == MODE_PAGED) {
            SegRow("Pages", prefs.spread, listOf(SPREAD_SINGLE to "Single", SPREAD_DOUBLE to "Double")) {
                onChange(prefs.copy(spread = it))
            }
            SegRow("Direction", prefs.direction, listOf(DIR_LTR to "L → R", DIR_RTL to "R → L")) {
                onChange(prefs.copy(direction = it))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tap to turn", Modifier.weight(1f))
                Switch(checked = prefs.tapToTurn, onCheckedChange = { onChange(prefs.copy(tapToTurn = it)) })
            }
        }
        if (effectiveMode == MODE_CONTINUOUS) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-scroll", Modifier.weight(1f))
                Switch(checked = autoScroll, onCheckedChange = { onToggleAutoScroll() })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Scroll speed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = prefs.scrollSpeed.toFloat(),
                    onValueChange = { onChange(prefs.copy(scrollSpeed = it.toInt().coerceIn(SCROLL_SPEED_MIN, SCROLL_SPEED_MAX))) },
                    valueRange = SCROLL_SPEED_MIN.toFloat()..SCROLL_SPEED_MAX.toFloat(),
                )
            }
        }
        SegRow("Background", prefs.bg, listOf(BG_WHITE to "White", BG_GRAY to "Gray", BG_BLACK to "Black")) {
            onChange(prefs.copy(bg = it))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Preload pages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRELOAD_OPTIONS.forEach { n ->
                    FilterChip(selected = preload == n, onClick = { onPreload(n) }, label = { Text("$n") })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSaveDefault, modifier = Modifier.weight(1f)) { Text("Save as default") }
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
    }
}

@Composable
internal fun SegRow(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, l) ->
                FilterChip(selected = value == v, onClick = { onSelect(v) }, label = { Text(l) })
            }
        }
    }
}

// ── Auto-detect ──────────────────────────────────────────────────────────────────────────────────

private const val WEBTOON_RATIO = 3f

private suspend fun detectMode(context: coil3.PlatformContext, source: ReaderSource): String {
    val samples = probePages(source.pageCount)
    val ratios = samples.mapNotNull { imageAspectRatio(context, source.pageUrlFor(it), source.apiKey) }
    val tall = ratios.count { it >= WEBTOON_RATIO }
    return if (ratios.isNotEmpty() && tall * 2 >= ratios.size) MODE_CONTINUOUS else MODE_PAGED
}

private fun probePages(pageCount: Int): List<Int> = when {
    pageCount <= 1 -> listOf(1)
    pageCount == 2 -> listOf(1, 2)
    else -> listOf(1, 2, minOf(pageCount, maxOf(3, pageCount / 2)))
}

private suspend fun imageAspectRatio(context: coil3.PlatformContext, url: String, apiKey: String): Float? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", apiKey).build())
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    val image = (result as? SuccessResult)?.image ?: return null
    return if (image.width > 0) image.height.toFloat() / image.width else null
}

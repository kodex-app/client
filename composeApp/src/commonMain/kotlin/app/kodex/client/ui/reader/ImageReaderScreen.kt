package app.kodex.client.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.platform.StatusBarIcons
import app.kodex.client.ui.collectAsStateSafe
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.launch

/** Everything the reader needs, independent of whether pages come from a book or a streamed source. */
class ReaderSource(
    val title: String,
    val pageCount: Int,
    val initialPage: Int, // 1-based
    val kind: String, // "comic" | "pdf" — chooses the settings bucket + defaults
    val seriesId: String?, // for per-series settings persistence
    val apiKey: String,
    val pageUrlFor: (page: Int) -> String, // 1-based page → image URL
    val onPersist: suspend (page: Int, completed: Boolean) -> Unit,
)

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
fun ImageReaderScreen(session: SessionManager, api: KodexApi, source: ReaderSource, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    var prefs by remember { mutableStateOf<ReaderPrefs?>(null) }
    var defaultPrefs by remember { mutableStateOf(defaultReaderPrefs(source.kind)) }
    var autoMode by remember { mutableStateOf<String?>(null) } // resolved paged/continuous when mode==auto

    // Load persisted prefs (series override → user default → built-in).
    LaunchedEffect(source.seriesId, source.kind, server?.id) {
        val s = server ?: return@LaunchedEffect
        val resolved = resolveReaderPrefs(api, s.baseUrl, s.apiKey, source.kind, source.seriesId)
        defaultPrefs = resolved.default
        prefs = resolved.effective
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
    LaunchedEffect(page) {
        if (source.pageCount > 0) runCatching { source.onPersist(page, page >= source.pageCount) }
    }

    var chrome by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Auto-hide chrome after 3s of no interaction.
    LaunchedEffect(chrome, page, settingsOpen) {
        if (chrome && !settingsOpen) {
            kotlinx.coroutines.delay(3000)
            chrome = false
        }
    }

    // Reader background is dark (gray/black) except for the White option → light status-bar icons.
    StatusBarIcons(darkIcons = p?.bg == BG_WHITE)

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
                val step = if (effectiveMode == MODE_PAGED && p.isDouble) 2 else 1
                val rtl = p.isRtl && effectiveMode == MODE_PAGED
                val fwd = { page = (page + step).coerceAtMost(source.pageCount) }
                val back = { page = (page - step).coerceAtLeast(1) }
                when (e.key) {
                    Key.DirectionRight -> if (rtl) back() else fwd()
                    Key.DirectionLeft -> if (rtl) fwd() else back()
                    Key.DirectionDown, Key.Spacebar -> fwd()
                    Key.DirectionUp -> back()
                    Key.Escape -> onBack()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        if (p == null || effectiveMode == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            if (effectiveMode == MODE_CONTINUOUS) {
                ContinuousReader(source, p, page, onPage = { page = it }, onToggleChrome = { chrome = !chrome })
            } else {
                PagedReader(source, p, page, onJump = { page = it }, onToggleChrome = { chrome = !chrome })
            }

            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.TopCenter)) {
                TopBar(source.title, onBack) { settingsOpen = true }
            }
            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomBar(page, source.pageCount, p.isRtl) { target -> page = target.coerceIn(1, source.pageCount) }
            }
        }
    }

    if (settingsOpen && p != null) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = rememberModalBottomSheetState()) {
            SettingsSheet(
                prefs = p,
                effectiveMode = effectiveMode ?: MODE_PAGED,
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
    onJump: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val double = prefs.isDouble
    val slotCount = if (double) (source.pageCount + 1) / 2 else source.pageCount
    val initialSlot = (if (double) (page - 1) / 2 else page - 1).coerceIn(0, (slotCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialSlot, pageCount = { slotCount })
    var zoomedIn by remember { mutableStateOf(false) }

    // Pager settle → current page.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { slot ->
            onJump(if (double) slot * 2 + 1 else slot + 1)
        }
    }
    // External jump (slider) → scroll pager.
    val targetSlot = if (double) (page - 1) / 2 else page - 1
    LaunchedEffect(targetSlot) {
        if (targetSlot != pagerState.currentPage) pagerState.scrollToPage(targetSlot.coerceIn(0, (slotCount - 1).coerceAtLeast(0)))
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = prefs.isRtl,
        userScrollEnabled = !zoomedIn,
        modifier = Modifier.fillMaxSize(),
    ) { slot ->
        if (double) {
            val left = slot * 2 + 1
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
        } else {
            val n = slot + 1
            ZoomablePage(
                url = source.pageUrlFor(n),
                apiKey = source.apiKey,
                zoom = prefs.zoom,
                tapToTurn = prefs.tapToTurn,
                rtl = prefs.isRtl,
                onZoomChange = { zoomedIn = it },
                onToggleChrome = onToggleChrome,
                onTurn = { forward ->
                    val next = pagerState.currentPage + if (forward) 1 else -1
                    if (next in 0 until slotCount) scope.launch { pagerState.animateScrollToPage(next) }
                },
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

@Composable
private fun ContinuousReader(
    source: ReaderSource,
    prefs: ReaderPrefs,
    page: Int,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (page - 1).coerceAtLeast(0))
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { onPage(it + 1) }
    }
    // External page change (slider/keyboard) → scroll there, but never fight an in-progress finger scroll.
    LaunchedEffect(page) {
        if (!listState.isScrollInProgress && (page - 1) != listState.firstVisibleItemIndex) {
            listState.scrollToItem((page - 1).coerceAtLeast(0))
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
            )
        }
    }
}

// ── Shared image ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageImage(url: String, apiKey: String, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", apiKey).build())
        .build()
    AsyncImage(model = request, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

// ── Chrome ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        Text(title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White) }
    }
}

@Composable
private fun BottomBar(page: Int, pageCount: Int, rtl: Boolean, onSeek: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("$page / $pageCount", color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (pageCount > 1) {
            Slider(
                value = (page - 1).toFloat(),
                onValueChange = { onSeek(it.toInt() + 1) },
                valueRange = 0f..(pageCount - 1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
            )
        }
    }
}

// ── Settings sheet ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSheet(
    prefs: ReaderPrefs,
    effectiveMode: String,
    onChange: (ReaderPrefs) -> Unit,
    onSaveDefault: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        SegRow("Background", prefs.bg, listOf(BG_WHITE to "White", BG_GRAY to "Gray", BG_BLACK to "Black")) {
            onChange(prefs.copy(bg = it))
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSaveDefault, modifier = Modifier.weight(1f)) { Text("Save as default") }
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
    }
}

@Composable
private fun SegRow(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
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

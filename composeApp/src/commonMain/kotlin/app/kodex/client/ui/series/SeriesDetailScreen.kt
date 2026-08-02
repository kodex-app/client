@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kodex.client.ui.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesChapterDto
import app.kodex.client.network.SeriesDetailDto
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.SelectionState
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.main.OpenSourceReader
import app.kodex.client.ui.rememberSelection
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

private data class SeriesContent(
    val detail: SeriesDetailDto,
    val books: List<BookDto>,
    val chapters: List<SeriesChapterDto>,
    val subseries: List<app.kodex.client.network.SeriesDto> = emptyList(),
    val libraryName: String? = null,
    val sourceName: String? = null,
    val libraries: List<app.kodex.client.network.LibraryDto> = emptyList(),
)

private data class Resume(val label: String, val open: () -> Unit, val openIncognito: () -> Unit)

/**
 * A series: cover + metadata header with a Read/Continue button, then its content — the books grid
 * for LOCAL series, or the source chapter list for WEB (followed) series. Supports series-level
 * actions (mark read/unread, refresh chapters/metadata) and long-press multi-select bulk actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    session: SessionManager,
    api: KodexApi,
    seriesId: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenMigrate: (seriesId: String, providerId: String, sourceSeriesId: String, title: String) -> Unit = { _, _, _, _ -> },
    onOpenReaderAt: (bookId: String, page: Int) -> Unit = { _, _ -> },
    onOpenSeries: (String) -> Unit = {},
    onOpenReaderIncognito: (String) -> Unit = {},
    onOpenSourceReaderIncognito: OpenSourceReader = { _, _, _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val selection = rememberSelection<String>()
    var reloadTick by remember { mutableIntStateOf(0) }
    var sortDesc by remember { mutableStateOf(true) }
    var sortByDate by remember { mutableStateOf(false) } // false = by chapter number, true = by release date
    var translator by remember { mutableStateOf<String?>(null) } // scanlator filter; null = all
    var menuOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    fun reload() { selection.clear(); reloadTick++ }

    fun runAction(message: String?, block: suspend () -> Unit) {
        server ?: return
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { message?.let { snackbar?.show(it) }; reload() },
                onFailure = { snackbar?.show("Action failed. Please try again.") },
            )
        }
    }

    var phase by remember { mutableStateOf<SeriesPhase>(SeriesPhase.Loading) }
    LaunchedEffect(seriesId, server?.id, reloadTick) {
        val s0 = server ?: return@LaunchedEffect
        phase = SeriesPhase.Loading
        phase = runCatching {
            val detail = api.seriesDetail(s0.baseUrl, s0.apiKey, seriesId)
            val subs = runCatching { api.subSeries(s0.baseUrl, s0.apiKey, seriesId) }.getOrDefault(emptyList())
            val libs = runCatching { api.libraries(s0.baseUrl, s0.apiKey) }.getOrDefault(emptyList())
            val libName = detail.libraryId?.let { lid -> libs.firstOrNull { it.id == lid }?.name }
            val srcName = detail.sourceProviderId?.let { pid -> runCatching { api.contentSources(s0.baseUrl, s0.apiKey).firstOrNull { it.id == pid }?.displayName }.getOrNull() }
            if (detail.isWeb) SeriesContent(detail, emptyList(), api.seriesChapters(s0.baseUrl, s0.apiKey, seriesId), subs, libName, srcName, libs)
            else SeriesContent(detail, api.seriesBooks(s0.baseUrl, s0.apiKey, seriesId), emptyList(), subs, libName, srcName, libs)
        }.fold({ SeriesPhase.Ready(it) }, { SeriesPhase.Error(it.friendlyMessage()) })
    }

    val content = (phase as? SeriesPhase.Ready)?.content
    val errorMsg = (phase as? SeriesPhase.Error)?.message
    val s = server
    val detail = content?.detail
    val isWeb = detail?.isWeb == true

    // Libraries the series could move to: same kind as its current one, excluding where it already is.
    // (Matches the web — offered only for WEB series, whose location is just a DB link.)
    val eligibleLibraries = content?.libraries.orEmpty().filter { it.isWeb == isWeb && it.id != detail?.libraryId }
    val canMove = isWeb && eligibleLibraries.isNotEmpty()

    // Chapters visible after the translator filter — drives the list and "select all"/"inverse".
    val visibleChapters = content?.chapters
        ?.let { chs -> if (translator == null) chs else chs.filter { it.scanlator == translator } }
        ?: emptyList()

    // Ids available for "select all" depend on the layout.
    val allIds = when {
        content == null -> emptyList()
        isWeb -> visibleChapters.map { it.chapterId }
        else -> content.books.map { it.id }
    }

    // The resume action (Start Reading / Continue) — surfaced as a floating button.
    val resume = when {
        content == null -> null
        isWeb -> webResume(
            content.chapters.sortedWith(compareBy(nullsLast()) { it.number }),
            content.detail.sourceProviderId.orEmpty(), content.detail.id,
            onOpenReader, onOpenSourceReader, onOpenReaderIncognito, onOpenSourceReaderIncognito,
        )
        else -> localResume(content.books, onOpenReader, onOpenReaderIncognito)
    }

    // Scroll state drives the collapsing toolbar: the title fades in (and the bar turns opaque, masking
    // content behind the status bar) once the header has scrolled up past the toolbar. Both layouts
    // (chapter list and book list) are LazyColumns, so they share one list state.
    val listState = rememberLazyListState()
    val titleVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 280 }
    }
    val barColor by animateColorAsState(
        if (titleVisible) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
        label = "seriesBarColor",
    )

    Scaffold(
            topBar = {
                if (selection.active) {
                    SelectionBar(
                        count = selection.count,
                        onClose = { selection.clear() },
                        onSelectAll = { selection.selectAll(allIds) },
                        onSelectInverse = { selection.selectInverse(allIds) },
                    )
                } else {
                    // Mihon-style: transparent over the backdrop, but the title fades in and the bar turns
                    // opaque once scrolled (which also masks list content passing behind the status bar).
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = barColor,
                            scrolledContainerColor = barColor,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        title = {
                            AnimatedVisibility(titleVisible && detail != null, enter = fadeIn(), exit = fadeOut()) {
                                Text(detail?.title.orEmpty(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            if (content != null) {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Series actions") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Mark all read") }, onClick = {
                                        menuOpen = false; runAction("Marked series read") { api.markSeriesRead(s!!.baseUrl, s.apiKey, seriesId, true) }
                                    })
                                    DropdownMenuItem(text = { Text("Mark all unread") }, onClick = {
                                        menuOpen = false; runAction("Marked series unread") { api.markSeriesRead(s!!.baseUrl, s.apiKey, seriesId, false) }
                                    })
                                    if (isWeb) {
                                        DropdownMenuItem(text = { Text("Refresh chapters") }, onClick = {
                                            menuOpen = false; runAction("Chapters refreshed") { api.refreshSeriesChapters(s!!.baseUrl, s.apiKey, seriesId) }
                                        })
                                        val prov = detail?.sourceProviderId
                                        val ext = detail?.sourceSeriesId
                                        if (prov != null && ext != null) {
                                            DropdownMenuItem(text = { Text("Migrate to another source") }, onClick = {
                                                menuOpen = false; onOpenMigrate(seriesId, prov, ext, detail.title)
                                            })
                                        }
                                    }
                                    if (canMove) {
                                        DropdownMenuItem(text = { Text("Move to another library") }, onClick = { menuOpen = false; moveOpen = true })
                                    }
                                    DropdownMenuItem(text = { Text("Refresh metadata") }, onClick = {
                                        menuOpen = false; runAction("Refreshing metadata…") { api.refreshSeriesMetadata(s!!.baseUrl, s.apiKey, seriesId) }
                                    })
                                    DropdownMenuItem(text = { Text("Re-analyze") }, onClick = {
                                        menuOpen = false; runAction("Analyzing…") { api.analyzeSeries(s!!.baseUrl, s.apiKey, seriesId) }
                                    })
                                    DropdownMenuItem(text = { Text("Edit metadata") }, onClick = { menuOpen = false; editOpen = true })
                                    DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { menuOpen = false; bookmarksOpen = true })
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (selection.active) {
                    SelectionBottomBar(
                        isWeb = isWeb,
                        onMarkRead = { markSelected(api, s, content, selection, read = true, ::runAction) },
                        onMarkUnread = { markSelected(api, s, content, selection, read = false, ::runAction) },
                        onDownload = { downloadSelected(api, s, content, selection, snackbar, scope) { reload() } },
                    )
                }
            },
            floatingActionButton = {
                if (resume != null && !selection.active) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.SmallFloatingActionButton(onClick = resume.openIncognito) {
                            Icon(app.kodex.client.ui.icons.IncognitoIcon, contentDescription = "Read incognito")
                        }
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = resume.open,
                            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            text = { Text(resume.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val topInset = padding.calculateTopPadding()
            // Bottom inset for content = the navigation-bar height. Read directly from WindowInsets (not
            // just the Scaffold padding) so the last row always clears the system nav bar / gesture pill.
            val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            val bottomInset = maxOf(padding.calculateBottomPadding(), navBottom)
            Box(Modifier.fillMaxSize()) {
                // Blurred cover backdrop from the top (behind the toolbar) down to just below the cover.
                if (content != null && s != null) {
                    SeriesBackdrop(s.baseUrl, s.apiKey, content.detail, topInset + 210.dp)
                }
                when {
                    errorMsg != null && content == null -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorRetry(errorMsg) { reloadTick++ } }
                    content == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
                    s != null && isWeb -> ChaptersLayout(
                        s.baseUrl, s.apiKey, content, visibleChapters,
                        sortDesc = sortDesc, sortByDate = sortByDate,
                        onToggleDir = { sortDesc = !sortDesc }, onSetSortByDate = { sortByDate = it },
                        translator = translator, onSetTranslator = { translator = it },
                        onRefresh = { runAction("Chapters refreshed") { api.refreshSeriesChapters(s.baseUrl, s.apiKey, seriesId) } },
                        selection = selection,
                        onOpenReader = onOpenReader, onOpenSourceReader = onOpenSourceReader,
                        onOpenReaderIncognito = onOpenReaderIncognito, onOpenSourceReaderIncognito = onOpenSourceReaderIncognito,
                        listState = listState, topInset = topInset, bottomInset = bottomInset,
                    )
                    s != null -> BooksLayout(
                        s.baseUrl, s.apiKey, content,
                        sortDesc = sortDesc, sortByDate = sortByDate,
                        onToggleDir = { sortDesc = !sortDesc }, onSetSortByDate = { sortByDate = it },
                        onRefresh = { runAction("Refreshing metadata…") { api.refreshSeriesMetadata(s.baseUrl, s.apiKey, seriesId) } },
                        selection = selection, onOpenBook = onOpenBook, onOpenSeries = onOpenSeries,
                        listState = listState, topInset = topInset, bottomInset = bottomInset,
                    )
                }
            }
        }

    if (moveOpen && s != null) {
        MoveLibraryDialog(
            libraries = eligibleLibraries,
            onDismiss = { moveOpen = false },
            onPick = { target ->
                moveOpen = false
                runAction("Moved to ${target.name}") { api.moveSeries(s.baseUrl, s.apiKey, listOf(seriesId), target.id) }
            },
        )
    }

    if (editOpen && content != null && s != null) {
        SeriesMetadataSheet(
            detail = content.detail,
            onDismiss = { editOpen = false },
            onSave = { patch ->
                editOpen = false
                runAction("Series updated") { api.updateSeriesMetadata(s.baseUrl, s.apiKey, seriesId, patch) }
            },
        )
    }

    if (bookmarksOpen && s != null) {
        app.kodex.client.ui.bookmark.BookmarksSheet(
            onDismiss = { bookmarksOpen = false },
            load = {
                api.seriesBookmarks(s.baseUrl, s.apiKey, seriesId).map { bm ->
                    app.kodex.client.ui.bookmark.BookmarkRow(
                        id = bm.id,
                        title = bm.label?.takeIf { it.isNotBlank() } ?: (bm.bookName ?: "Bookmark"),
                        subtitle = listOfNotNull(bm.bookName?.takeIf { bm.label != null && bm.label.isNotBlank() }, bm.page?.let { "Page $it" }).joinToString(" · ").ifBlank { null },
                        onOpen = { bookmarksOpen = false; bm.bookId?.let { bid -> bm.page?.let { onOpenReaderAt(bid, it) } ?: onOpenReader(bid) } },
                        onDelete = bm.bookId?.let { bid -> { api.deleteBookmark(s.baseUrl, s.apiKey, bid, bm.id) } },
                    )
                }
            },
        )
    }
    }

private sealed interface SeriesPhase {
    data object Loading : SeriesPhase
    data class Error(val message: String) : SeriesPhase
    data class Ready(val content: SeriesContent) : SeriesPhase
}

/** Picker to move the series into another (same-kind) library. */
@Composable
private fun MoveLibraryDialog(
    libraries: List<app.kodex.client.network.LibraryDto>,
    onDismiss: () -> Unit,
    onPick: (app.kodex.client.network.LibraryDto) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to library") },
        text = {
            Column {
                libraries.forEach { lib ->
                    Text(
                        lib.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(lib) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
        }
    }
}

// ── Selection bars ─────────────────────────────────────────────────────────────────────────────
/** Contextual top bar while items are multi-selected: count + Select all / Select inverse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectInverse: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = { Text("$count selected", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            Tip("Cancel selection") {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
            }
        },
        actions = {
            Tip("Select all") {
                IconButton(onClick = onSelectAll) { Icon(app.kodex.client.ui.icons.SelectAllIcon, contentDescription = "Select all") }
            }
            Tip("Select inverse") {
                IconButton(onClick = onSelectInverse) { Icon(app.kodex.client.ui.icons.InvertSelectionIcon, contentDescription = "Select inverse") }
            }
        },
    )
}

/** Wraps an action with a plain tooltip shown on long-press / hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

/** Contextual bottom action bar for multi-select — the bulk functions as icon buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomBar(
    isWeb: Boolean,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tip("Mark as read") {
                IconButton(onClick = onMarkRead) { Icon(Icons.Filled.Check, contentDescription = "Mark as read") }
            }
            Tip("Mark as unread") {
                IconButton(onClick = onMarkUnread) { Icon(app.kodex.client.ui.icons.MarkUnreadIcon, contentDescription = "Mark as unread") }
            }
            if (isWeb) Tip("Download") {
                IconButton(onClick = onDownload) { Icon(app.kodex.client.ui.icons.DownloadIcon, contentDescription = "Download") }
            }
        }
    }
}

// ── Bulk action helpers ────────────────────────────────────────────────────────────────────────
private fun markSelected(
    api: KodexApi,
    server: app.kodex.client.data.model.ServerConnection?,
    content: SeriesContent?,
    selection: SelectionState<String>,
    read: Boolean,
    run: (String?, suspend () -> Unit) -> Unit,
) {
    server ?: return; content ?: return
    val ids = selection.selected.toList()
    if (ids.isEmpty()) return
    val label = if (read) "Marked read" else "Marked unread"
    if (content.detail.isWeb) {
        run(label) { api.markChaptersRead(server.baseUrl, server.apiKey, content.detail.id, ids, read) }
    } else {
        val byId = content.books.associateBy { it.id }
        run(label) {
            ids.forEach { id ->
                val book = byId[id] ?: return@forEach
                if (read) api.markBookRead(server.baseUrl, server.apiKey, book)
                else api.markBookUnread(server.baseUrl, server.apiKey, id)
            }
        }
    }
}

private fun downloadSelected(
    api: KodexApi,
    server: app.kodex.client.data.model.ServerConnection?,
    content: SeriesContent?,
    selection: SelectionState<String>,
    snackbar: app.kodex.client.ui.SnackbarController?,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    server ?: return; content ?: return
    val detail = content.detail
    val ids = selection.selected.toList()
    if (!detail.isWeb || ids.isEmpty()) return
    scope.launch {
        runCatching {
            // Resolve the followed series' library to target the download endpoint.
            val ref = api.followedSeriesRef(server.baseUrl, server.apiKey, detail.sourceProviderId!!, detail.sourceSeriesId!!)
                ?: error("not followed")
            api.downloadWebSeries(server.baseUrl, server.apiKey, ref.libraryId, detail.id, ids)
        }.fold(
            onSuccess = { snackbar?.show("Downloading ${ids.size} chapter(s)"); onDone() },
            onFailure = { snackbar?.show("Couldn't start download.") },
        )
    }
}

// ── Layouts ────────────────────────────────────────────────────────────────────────────────────
@Composable
private fun BooksLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    sortDesc: Boolean,
    sortByDate: Boolean,
    onToggleDir: () -> Unit,
    onSetSortByDate: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    selection: SelectionState<String>,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    listState: LazyListState,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val ascending = if (sortByDate) content.books.sortedWith(compareBy(nullsLast()) { it.releaseDate })
    else content.books.sortedBy { it.number }
    val display = if (sortDesc) ascending.asReversed() else ascending
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 96.dp + bottomInset)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SeriesHeader(
                    baseUrl, apiKey, content.detail,
                    countLabel = "${content.books.size} ${if (content.books.size == 1) "book" else "books"}",
                    unread = content.books.count { it.readProgress?.completed != true },
                    libraryName = content.libraryName,
                    sourceName = content.sourceName,
                )
                if (content.subseries.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    app.kodex.client.ui.catalog.CoverSection("Sub-series", content.subseries, key = { it.id }) { sub ->
                        CoverCard(
                            coverUrl = app.kodex.client.ui.catalog.seriesCoverUrl(baseUrl, sub),
                            apiKey = apiKey,
                            title = sub.title,
                            subtitle = app.kodex.client.ui.catalog.seriesSubtitle(sub),
                            unread = app.kodex.client.ui.catalog.seriesUnreadBadge(sub),
                            onClick = { onOpenSeries(sub.id) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                SeriesListControls(
                    countLabel = "Books · ${content.books.size}",
                    numberLabel = "Book number",
                    sortByDate = sortByDate, sortDesc = sortDesc, onToggleDir = onToggleDir, onSetSortByDate = onSetSortByDate,
                    scanlators = emptyList(), translator = null, onSetTranslator = {},
                    onRefresh = onRefresh,
                )
            }
        }
        items(display, key = { it.id }) { book ->
            BookRow(baseUrl, apiKey, book, selection, onOpenBook)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** One book as a full-bleed list row: thumbnail + title + #number/meta, with a selection highlight. */
@Composable
private fun BookRow(
    baseUrl: String,
    apiKey: String,
    book: BookDto,
    selection: SelectionState<String>,
    onOpenBook: (String) -> Unit,
) {
    val selected = selection.isSelected(book.id)
    val read = book.readProgress?.completed == true
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(
                onClick = { if (selection.active) selection.toggle(book.id) else onOpenBook(book.id) },
                onLongClick = { selection.toggle(book.id) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection.active) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(6.dp))) {
            CoverImage(bookCoverUrl(baseUrl, book.id), apiKey, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(if (read && !selected) 0.55f else 1f)) {
            Text(bookLabel(book), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            val meta = listOfNotNull(
                "#" + (if (book.number % 1.0 == 0.0) book.number.toInt().toString() else book.number.toString()),
                "${book.pageCount} pages".takeIf { book.pageCount > 0 },
                book.releaseDate?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Spacer(Modifier.height(2.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChaptersLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    visibleChapters: List<SeriesChapterDto>,
    sortDesc: Boolean,
    sortByDate: Boolean,
    onToggleDir: () -> Unit,
    onSetSortByDate: (Boolean) -> Unit,
    translator: String?,
    onSetTranslator: (String?) -> Unit,
    onRefresh: () -> Unit,
    selection: SelectionState<String>,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenReaderIncognito: (String) -> Unit,
    onOpenSourceReaderIncognito: OpenSourceReader,
    listState: LazyListState,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val providerId = content.detail.sourceProviderId.orEmpty()
    val seriesId = content.detail.id
    val scanlators = content.chapters.mapNotNull { it.scanlator?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
    val ascending = if (sortByDate) visibleChapters.sortedWith(compareBy(nullsLast()) { it.releaseDate })
    else visibleChapters.sortedWith(compareBy(nullsLast()) { it.number })
    val display = if (sortDesc) ascending.asReversed() else ascending
    val downloaded = visibleChapters.count { it.downloaded }
    // Rows are full-bleed (their selection highlight spans the width), so the list itself has no side padding.
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 96.dp + bottomInset)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SeriesHeader(
                    baseUrl, apiKey, content.detail,
                    countLabel = "${visibleChapters.size} chapters · $downloaded downloaded",
                    unread = visibleChapters.count { !it.read },
                    libraryName = content.libraryName,
                    sourceName = content.sourceName,
                )
                Spacer(Modifier.height(20.dp))
                SeriesListControls(
                    countLabel = "Chapters · ${visibleChapters.size}",
                    numberLabel = "Chapter number",
                    sortByDate = sortByDate, sortDesc = sortDesc, onToggleDir = onToggleDir, onSetSortByDate = onSetSortByDate,
                    scanlators = scanlators, translator = translator, onSetTranslator = onSetTranslator,
                    onRefresh = onRefresh,
                )
            }
        }
        items(display, key = { it.chapterId }) { chapter ->
            ChapterRow(chapter, providerId, seriesId, selection, onOpenReader, onOpenSourceReader)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** Sort (number/date + direction), optional translator filter, and refresh controls above a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesListControls(
    countLabel: String,
    numberLabel: String,
    sortByDate: Boolean,
    sortDesc: Boolean,
    onToggleDir: () -> Unit,
    onSetSortByDate: (Boolean) -> Unit,
    scanlators: List<String>,
    translator: String?,
    onSetTranslator: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var transMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(countLabel)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            ControlChip(if (sortByDate) "Release date" else numberLabel) { sortMenu = true }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMenuItem(numberLabel, selected = !sortByDate, sortDesc = sortDesc) { if (sortByDate) onSetSortByDate(false) else onToggleDir() }
                SortMenuItem("Release date", selected = sortByDate, sortDesc = sortDesc) { if (!sortByDate) onSetSortByDate(true) else onToggleDir() }
            }
        }
        if (scanlators.size > 1) {
            Spacer(Modifier.width(8.dp))
            Box {
                ControlChip(translator ?: "All translators") { transMenu = true }
                DropdownMenu(expanded = transMenu, onDismissRequest = { transMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All translators") },
                        onClick = { transMenu = false; onSetTranslator(null) },
                        leadingIcon = { if (translator == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                    scanlators.forEach { sc ->
                        DropdownMenuItem(
                            text = { Text(sc) },
                            onClick = { transMenu = false; onSetTranslator(sc) },
                            leadingIcon = { if (translator == sc) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
        }
    }
}

/** A compact pill that opens a menu: label + dropdown caret, tinted in the primary color. */
@Composable
private fun ControlChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** A sort-key menu row: leading check when active, trailing arrow for its direction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuItem(label: String, selected: Boolean, sortDesc: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (selected) Icon(
                if (sortDesc) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (sortDesc) "Descending" else "Ascending",
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun ChapterRow(
    chapter: SeriesChapterDto,
    providerId: String,
    seriesId: String,
    selection: SelectionState<String>,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    val selected = selection.isSelected(chapter.chapterId)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(
                onClick = {
                    if (selection.active) {
                        selection.toggle(chapter.chapterId)
                    } else {
                        val bookId = chapter.bookId
                        if (bookId != null) onOpenReader(bookId)
                        else onOpenSourceReader(providerId, chapter.chapterId, seriesId, chapter.name)
                    }
                },
                onLongClick = { selection.toggle(chapter.chapterId) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection.active) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(Modifier.weight(1f).alpha(if (chapter.read && !selected) 0.55f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chapter.name?.takeIf { it.isNotBlank() } ?: chapterNumberLabel(chapter),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                if (chapter.isNew) {
                    Spacer(Modifier.width(8.dp))
                    MetaChip("New")
                }
            }
            val meta = listOfNotNull(
                chapter.number?.let { n -> "#" + (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) },
                if (chapter.downloaded) "Downloaded" else "Stream",
                chapter.scanlator?.takeIf { it.isNotBlank() },
                chapter.releaseDate?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Mihon-style backdrop: the cover, blurred, fading into the page background under the header. */
@Composable
private fun SeriesBackdrop(baseUrl: String, apiKey: String, detail: SeriesDetailDto, height: androidx.compose.ui.unit.Dp) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(height)) {
        CoverImage(
            seriesCoverUrl(baseUrl, detail.id, detail.coverUrl),
            apiKey,
            Modifier.fillMaxSize().blur(20.dp).alpha(0.55f),
        )
        // Top-to-bottom scrim so toolbar icons stay legible and the backdrop dissolves into the content.
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.30f),
                    0.65f to surface.copy(alpha = 0.75f),
                    1f to surface,
                ),
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesHeader(
    baseUrl: String,
    apiKey: String,
    detail: SeriesDetailDto,
    countLabel: String,
    unread: Int,
    libraryName: String? = null,
    sourceName: String? = null,
) {
    Column {
        Row {
            Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) {
                CoverImage(seriesCoverUrl(baseUrl, detail.id, detail.coverUrl), apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                // Author / artist (artist shown only when it differs from the author).
                val people = listOfNotNull(
                    detail.author.takeIf { it.isNotBlank() },
                    detail.artist.takeIf { it.isNotBlank() && !it.equals(detail.author, ignoreCase = true) },
                ).joinToString(" · ")
                if (people.isNotBlank()) {
                    Text(people, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(countLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unread > 0) {
                    Text("$unread unread", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (detail.publisher.isNotBlank()) {
                    Text(detail.publisher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val provenance = listOfNotNull(
                    sourceName?.takeIf { it.isNotBlank() },
                    detail.language.takeIf { it.isNotBlank() }?.uppercase(),
                ).joinToString(" · ")
                if (provenance.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(provenance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The library this series belongs to — shown so it's clear which shelf it came from.
                if (!libraryName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Library · $libraryName", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val chips = (detail.genres + detail.tags).distinct()
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(15).forEach { MetaChip(it) }
            }
        }
        if (detail.summary.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            ExpandableSummary(detail.summary)
        }
    }
}

/** Series summary capped at 3 lines with a "Read more" toggle when it overflows; tapping toggles too. */
@Composable
private fun ExpandableSummary(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(6.dp))
            .clickable(enabled = overflows || expanded) { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { expanded = !expanded })
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun localResume(books: List<BookDto>, onOpenReader: (String) -> Unit, onOpenReaderIncognito: (String) -> Unit): Resume? {
    if (books.isEmpty()) return null
    val inProgress = books.firstOrNull { it.readProgress?.completed == false }
    val firstUnread = books.firstOrNull { it.readProgress == null }
    val target = inProgress ?: firstUnread ?: books.first()
    val label = when {
        inProgress != null -> "Continue"
        firstUnread != null -> "Start Reading"
        else -> "Read again"
    }
    return Resume(label, { onOpenReader(target.id) }, { onOpenReaderIncognito(target.id) })
}

private fun webResume(
    chapters: List<SeriesChapterDto>,
    providerId: String,
    seriesId: String,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenReaderIncognito: (String) -> Unit,
    onOpenSourceReaderIncognito: OpenSourceReader,
): Resume? {
    if (chapters.isEmpty()) return null
    val firstUnread = chapters.firstOrNull { !it.read }
    val target = firstUnread ?: chapters.first()
    val label = when {
        firstUnread == null -> "Read again"
        target.page != null -> "Continue"
        else -> "Start Reading"
    }
    val bookId = target.bookId
    val open = { if (bookId != null) onOpenReader(bookId) else onOpenSourceReader(providerId, target.chapterId, seriesId, target.name) }
    val openIncognito = { if (bookId != null) onOpenReaderIncognito(bookId) else onOpenSourceReaderIncognito(providerId, target.chapterId, seriesId, target.name) }
    return Resume(label, open, openIncognito)
}

private fun bookLabel(book: BookDto): String = book.title.ifBlank { book.numberDisplay ?: "Book" }

private fun chapterNumberLabel(c: SeriesChapterDto): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

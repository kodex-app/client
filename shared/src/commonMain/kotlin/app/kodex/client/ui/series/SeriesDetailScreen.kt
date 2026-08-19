@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kodex.client.ui.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesChapterDto
import app.kodex.client.network.SeriesDetailDto
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.SelectionState
import app.kodex.client.ui.SelectionActionBar
import app.kodex.client.ui.SelectionTopBar
import app.kodex.client.ui.TooltipIconButton
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.SeriesBackdrop
import app.kodex.client.ui.nav.retain
import app.kodex.client.ui.catalog.SeriesDetailList
import app.kodex.client.ui.catalog.SeriesEntryRow
import app.kodex.client.ui.catalog.SeriesHeader
import app.kodex.client.ui.catalog.SeriesListControls
import app.kodex.client.ui.catalog.SeriesSort
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.icons.DonePrev
import app.kodex.client.ui.icons.UndonePrev
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

/**
 * The resume action behind the floating button. [target] names what it will actually open (and where
 * in it), so the button isn't a blind "Continue" — with dozens of books, which one it means is the
 * first thing you want to know.
 */
private data class Resume(
    val label: String,
    val target: String,
    val open: () -> Unit,
    val openIncognito: () -> Unit,
)

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
    // Retained: opening the reader unmounts this screen, and losing the loaded series here meant
    // coming back re-fetched everything from a spinner and threw away the scroll position.
    val st = retain("seriesDetail") { SeriesDetailState() }
    var sortDesc by st.sortDesc
    var sortKey by st.sortKey
    var translator by st.translator // scanlator filter; null = all
    var menuOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    fun reload() { selection.clear(); st.forceFull = true; reloadTick++ }

    fun runAction(message: String?, block: suspend () -> Unit) {
        server ?: return
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { message?.let { snackbar?.show(it) }; reload() },
                onFailure = { snackbar?.show("Action failed. Please try again.") },
            )
        }
    }

    var phase by st.phase
    LaunchedEffect(seriesId, server?.id, reloadTick) {
        val s0 = server ?: return@LaunchedEffect
        // Only fall back to the spinner when there is nothing on screen yet. Returning from the reader
        // (or reloading after a bulk action) refreshes in place, so read state updates without the list
        // flashing away — which is also what keeps the scroll position meaningful.
        if (phase !is SeriesPhase.Ready) phase = SeriesPhase.Loading
        phase = runCatching {
            val detail = api.seriesDetail(s0.baseUrl, s0.apiKey, seriesId)
            // Only on a first load of this series or an explicit reload — see SeriesDetailState.
            if (st.ancillaryFor != seriesId || st.forceFull) {
                st.subSeries = runCatching { api.subSeries(s0.baseUrl, s0.apiKey, seriesId) }.getOrDefault(emptyList())
                st.libraries = runCatching { api.libraries(s0.baseUrl, s0.apiKey) }.getOrDefault(emptyList())
                st.sourceName = detail.sourceProviderId?.let { pid ->
                    runCatching { api.contentSources(s0.baseUrl, s0.apiKey).firstOrNull { it.id == pid }?.displayName }.getOrNull()
                }
                st.ancillaryFor = seriesId
                st.forceFull = false
            }
            val subs = st.subSeries
            val libs = st.libraries
            // Looked up fresh from the cached list: a move changes the series' libraryId, not the
            // set of libraries, so the name still resolves without re-fetching them.
            val libName = detail.libraryId?.let { lid -> libs.firstOrNull { it.id == lid }?.name }
            val srcName = st.sourceName
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
    val listState = st.list
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
                    SelectionTopBar(
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
                                        val prov = detail.sourceProviderId
                                        val ext = detail.sourceSeriesId
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
                        // "Previous" needs one anchor to be relative to, so the menu is offered only
                        // while exactly one entry is selected (the same rule Mihon uses).
                        showPrevious = selection.count == 1,
                        onMarkRead = { markSelected(api, s, content, selection, read = true, ::runAction) },
                        onMarkUnread = { markSelected(api, s, content, selection, read = false, ::runAction) },
                        onMarkPreviousRead = {
                            markPrevious(api, s, content, selection, visibleChapters, read = true, snackbar, ::runAction)
                        },
                        onMarkPreviousUnread = {
                            markPrevious(api, s, content, selection, visibleChapters, read = false, snackbar, ::runAction)
                        },
                        onDownload = { downloadSelected(api, s, content, selection, snackbar, scope) { reload() } },
                    )
                }
            },
            floatingActionButton = {
                if (resume != null && !selection.active) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.SmallFloatingActionButton(onClick = resume.openIncognito) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = "Read incognito")
                        }
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = resume.open,
                            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            text = {
                                Column {
                                    Text(resume.label, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        resume.target,
                                        // The cap is what actually truncates. maxLines alone doesn't
                                        // bound width, so a long chapter title stretched the whole FAB
                                        // across the screen rather than ellipsing.
                                        modifier = Modifier.widthIn(max = RESUME_TARGET_MAX_WIDTH),
                                        style = MaterialTheme.typography.labelSmall,
                                        // Muted against the container so the action still reads first.
                                        color = LocalContentColor.current.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
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
                    errorMsg != null && content == null -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorRetry(errorMsg) { reload() } }
                    content == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
                    s != null && isWeb -> ChaptersLayout(
                        s.baseUrl, s.apiKey, content, visibleChapters,
                        sortDesc = sortDesc, sortKey = sortKey,
                        onToggleDir = { sortDesc = !sortDesc }, onSetSortKey = { sortKey = it },
                        translator = translator, onSetTranslator = { translator = it },
                        onRefresh = { runAction("Chapters refreshed") { api.refreshSeriesChapters(s.baseUrl, s.apiKey, seriesId) } },
                        selection = selection,
                        onOpenReader = onOpenReader, onOpenSourceReader = onOpenSourceReader,
                        onOpenReaderIncognito = onOpenReaderIncognito, onOpenSourceReaderIncognito = onOpenSourceReaderIncognito,
                        listState = listState, topInset = topInset, bottomInset = bottomInset,
                    )
                    s != null -> BooksLayout(
                        s.baseUrl, s.apiKey, content,
                        sortDesc = sortDesc, sortKey = sortKey,
                        onToggleDir = { sortDesc = !sortDesc }, onSetSortKey = { sortKey = it },
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

    // Fetched only once the sheet opens: the label list is only ever needed by the editor.
    var allLabels by remember { mutableStateOf<List<app.kodex.client.network.LabelDto>>(emptyList()) }
    LaunchedEffect(editOpen, s?.id) {
        if (!editOpen) return@LaunchedEffect
        val srv = s ?: return@LaunchedEffect
        allLabels = runCatching { api.labels(srv.baseUrl, srv.apiKey) }.getOrDefault(emptyList())
    }

    if (editOpen && content != null && s != null) {
        SeriesMetadataSheet(
            detail = content.detail,
            labels = allLabels,
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


/**
 * Contextual bottom action bar for multi-select — the bulk functions as icon buttons.
 *
 * The two "previous" actions sit next to their plain counterparts, before Download. They only mean
 * anything against a single anchor, so they appear at [showPrevious] and their tooltips carry the
 * distinction the glyphs alone can't.
 */
@Composable
private fun SelectionBottomBar(
    isWeb: Boolean,
    showPrevious: Boolean,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPreviousRead: () -> Unit,
    onMarkPreviousUnread: () -> Unit,
    onDownload: () -> Unit,
) {
    SelectionActionBar {
        TooltipIconButton("Mark as read", onMarkRead) { Icon(Icons.Outlined.DoneAll, contentDescription = "Mark as read") }
        TooltipIconButton("Mark as unread", onMarkUnread) { Icon(Icons.Outlined.RemoveDone, contentDescription = "Mark as unread") }
        if (showPrevious) {
            TooltipIconButton("Mark previous as read", onMarkPreviousRead) {
                Icon(DonePrev, contentDescription = "Mark previous as read")
            }
            TooltipIconButton("Mark previous as unread", onMarkPreviousUnread) {
                Icon(UndonePrev, contentDescription = "Mark previous as unread")
            }
        }
        if (isWeb) {
            TooltipIconButton("Download", onDownload) { Icon(Icons.Filled.Download, contentDescription = "Download") }
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
) = markIds(api, server, content, selection.selected.toList(), read, if (read) "Marked read" else "Marked unread", run)

/**
 * Mark everything *before* the selected entry — the "I started this series at book 40" catch-up, and
 * its undo. The anchor itself is left alone; the plain "Mark as read" next to it covers that.
 */
private fun markPrevious(
    api: KodexApi,
    server: app.kodex.client.data.model.ServerConnection?,
    content: SeriesContent?,
    selection: SelectionState<String>,
    visibleChapters: List<SeriesChapterDto>,
    read: Boolean,
    snackbar: app.kodex.client.ui.SnackbarController?,
    run: (String?, suspend () -> Unit) -> Unit,
) {
    val ids = idsBeforeSelection(content, selection, visibleChapters)
    if (ids.isEmpty()) {
        snackbar?.show("Nothing comes before this one.")
        return
    }
    markIds(api, server, content, ids, read, "Marked ${ids.size} earlier as ${if (read) "read" else "unread"}", run)
}

/**
 * The entries before the selected one, in the series own ascending order rather than the current view
 * order — "previous" means earlier in the series, not further up a list you happen to have reversed.
 * Empty unless exactly one entry is selected, and empty when that one is already the first.
 */
private fun idsBeforeSelection(
    content: SeriesContent?,
    selection: SelectionState<String>,
    visibleChapters: List<SeriesChapterDto>,
): List<String> {
    content ?: return emptyList()
    val anchor = selection.selected.singleOrNull() ?: return emptyList()
    // Chapters go by the filtered list, the same one "select all" operates on: a scanlator filter hides
    // entries, and silently marking hidden ones would be a surprise.
    val ordered = if (content.detail.isWeb) {
        visibleChapters.sortedWith(compareBy(nullsLast()) { it.number }).map { it.chapterId }
    } else {
        content.books.sortedBy { it.number }.map { it.id }
    }
    val at = ordered.indexOf(anchor)
    return if (at <= 0) emptyList() else ordered.subList(0, at)
}

/** The one path that marks a set of ids read/unread, whichever kind of series this is. */
private fun markIds(
    api: KodexApi,
    server: app.kodex.client.data.model.ServerConnection?,
    content: SeriesContent?,
    ids: List<String>,
    read: Boolean,
    label: String,
    run: (String?, suspend () -> Unit) -> Unit,
) {
    server ?: return; content ?: return
    if (ids.isEmpty()) return
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
    sortKey: SeriesSort,
    onToggleDir: () -> Unit,
    onSetSortKey: (SeriesSort) -> Unit,
    onRefresh: () -> Unit,
    selection: SelectionState<String>,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    listState: LazyListState,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    // SOURCE keeps the server's own ordering untouched; the others sort a copy of it.
    val ascending = when (sortKey) {
        SeriesSort.DATE -> content.books.sortedWith(compareBy(nullsLast()) { it.releaseDate })
        SeriesSort.NUMBER -> content.books.sortedBy { it.number }
        SeriesSort.SOURCE -> content.books
    }
    val display = if (sortDesc) ascending.asReversed() else ascending
    SeriesDetailList(listState, topInset, bottomInset, header = {
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
            sortKey = sortKey, sortDesc = sortDesc, onToggleDir = onToggleDir, onSetSortKey = onSetSortKey,
            scanlators = emptyList(), translator = null, onSetTranslator = {},
            onRefresh = onRefresh,
        )
    }) {
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
    SeriesEntryRow(
        title = bookLabel(book),
        meta = listOfNotNull(
            "#" + (if (book.number % 1.0 == 0.0) book.number.toInt().toString() else book.number.toString()),
            "${book.pageCount} pages".takeIf { book.pageCount > 0 },
            book.releaseDate?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = { if (selection.active) selection.toggle(book.id) else onOpenBook(book.id) },
        onLongClick = { selection.toggle(book.id) },
        dimmed = read,
        selected = selected,
        leading = {
            if (selection.active) SelectionTick(selected)
            Box(Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(6.dp))) {
                CoverImage(bookCoverUrl(baseUrl, book.id), apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
        },
    )
}

/** Selection tick shared by both rows while multi-select is active. */
@Composable
private fun SelectionTick(selected: Boolean) {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 12.dp),
    )
}

@Composable
private fun ChaptersLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    visibleChapters: List<SeriesChapterDto>,
    sortDesc: Boolean,
    sortKey: SeriesSort,
    onToggleDir: () -> Unit,
    onSetSortKey: (SeriesSort) -> Unit,
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
    // SOURCE is the order the content source itself lists them in — the stored catalogue order,
    // which is what /series/{id}/chapters already returns.
    val ascending = when (sortKey) {
        SeriesSort.DATE -> visibleChapters.sortedWith(compareBy(nullsLast()) { it.releaseDate })
        SeriesSort.NUMBER -> visibleChapters.sortedWith(compareBy(nullsLast()) { it.number })
        SeriesSort.SOURCE -> visibleChapters
    }
    val display = if (sortDesc) ascending.asReversed() else ascending
    val downloaded = visibleChapters.count { it.downloaded }
    // Rows are full-bleed (their selection highlight spans the width), so the list itself has no side padding.
    SeriesDetailList(listState, topInset, bottomInset, header = {
        SeriesHeader(
            baseUrl, apiKey, content.detail,
            countLabel = "${visibleChapters.size} books · $downloaded downloaded",
            unread = visibleChapters.count { !it.read },
            libraryName = content.libraryName,
            sourceName = content.sourceName,
        )
        Spacer(Modifier.height(20.dp))
        SeriesListControls(
            countLabel = "Books · ${visibleChapters.size}",
            numberLabel = "Book number",
            sortKey = sortKey, sortDesc = sortDesc, onToggleDir = onToggleDir, onSetSortKey = onSetSortKey,
            scanlators = scanlators, translator = translator, onSetTranslator = onSetTranslator,
            onRefresh = onRefresh,
        )
    }) {
        items(display, key = { it.chapterId }) { chapter ->
            ChapterRow(chapter, providerId, seriesId, selection, onOpenReader, onOpenSourceReader)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
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
    SeriesEntryRow(
        title = chapter.name?.takeIf { it.isNotBlank() } ?: chapterNumberLabel(chapter),
        meta = listOfNotNull(
            chapter.number?.let { n -> "#" + (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) },
            if (chapter.downloaded) "Downloaded" else "Stream",
            chapter.scanlator?.takeIf { it.isNotBlank() },
            chapter.releaseDate?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
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
        dimmed = chapter.read,
        selected = selected,
        leading = if (selection.active) ({ SelectionTick(selected) }) else null,
        titleTrailing = if (chapter.isNew) ({ MetaChip("New") }) else null,
    )
}

/** Mihon-style backdrop: the cover, blurred, fading into the page background under the header. */
@Composable
private fun SeriesBackdrop(baseUrl: String, apiKey: String, detail: SeriesDetailDto, height: androidx.compose.ui.unit.Dp) {
    SeriesBackdrop(seriesCoverUrl(baseUrl, detail.id, detail.coverUrl), apiKey, height)
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
    SeriesHeader(
        coverUrl = seriesCoverUrl(baseUrl, detail.id, detail.coverUrl),
        apiKey = apiKey,
        title = detail.title,
        chips = (detail.genres + detail.tags).distinct(),
        summary = detail.summary,
    ) {
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



/**
 * How wide the resume button's second line may get. Chapter titles run long (scanlation group, volume,
 * language tags all end up in there), and the FAB grows to fit whatever it is handed — at full length
 * it spanned the screen and buried the cover behind it.
 */
private val RESUME_TARGET_MAX_WIDTH = 200.dp

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
    // Where in it, but only when resuming part-way — "page 1" on a fresh book is noise.
    val at = target.readProgress?.page?.takeIf { inProgress != null && it > 0 }?.let { " · page $it" }.orEmpty()
    return Resume(label, bookLabel(target) + at, { onOpenReader(target.id) }, { onOpenReaderIncognito(target.id) })
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
    val at = target.page?.takeIf { it > 0 }?.let { " · page $it" }.orEmpty()
    return Resume(label, chapterTargetLabel(target) + at, open, openIncognito)
}

private fun bookLabel(book: BookDto): String = book.title.ifBlank { book.numberDisplay ?: "Book" }

/** What the resume button names for a source chapter: its own title, else its number. */
private fun chapterTargetLabel(c: SeriesChapterDto): String =
    c.name?.takeIf { it.isNotBlank() } ?: chapterNumberLabel(c)

private fun chapterNumberLabel(c: SeriesChapterDto): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

/**
 * The parts of the screen that must outlive it being covered by the reader: the loaded series, how the
 * list is sorted and filtered, and where it was scrolled to.
 */
private class SeriesDetailState {
    val phase = mutableStateOf<SeriesPhase>(SeriesPhase.Loading)
    val sortDesc = mutableStateOf(true)
    val sortKey = mutableStateOf(SeriesSort.NUMBER)
    val translator = mutableStateOf<String?>(null)
    val list = LazyListState()

    // ── Ancillary data, cached across re-entry ───────────────────────────────────────────────────
    // Returning from the reader re-runs the load effect (the screen was unmounted while covered), and
    // that is wanted: reading changes read state, which this screen displays. But only the series
    // itself and its books/chapters carry read state. The library list, the source's display name and
    // the sub-series cannot change while a chapter is open, so re-fetching them turned every back
    // press into five sequential round trips — the visible "reload". Fetched once per series instead.
    /** Which series [libraries]/[subSeries]/[sourceName] belong to; null means not fetched yet. */
    var ancillaryFor: String? = null
    var libraries: List<app.kodex.client.network.LibraryDto> = emptyList()
    var subSeries: List<app.kodex.client.network.SeriesDto> = emptyList()
    var sourceName: String? = null

    /** Set by an explicit reload (pull, bulk action, retry), which refreshes the ancillary data too. */
    var forceFull = false
}

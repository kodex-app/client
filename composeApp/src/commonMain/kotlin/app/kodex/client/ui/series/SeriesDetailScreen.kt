@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kodex.client.ui.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    var menuOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

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
            val libName = detail.libraryId?.let { lid -> runCatching { api.libraries(s0.baseUrl, s0.apiKey).firstOrNull { it.id == lid }?.name }.getOrNull() }
            val srcName = detail.sourceProviderId?.let { pid -> runCatching { api.contentSources(s0.baseUrl, s0.apiKey).firstOrNull { it.id == pid }?.displayName }.getOrNull() }
            if (detail.isWeb) SeriesContent(detail, emptyList(), api.seriesChapters(s0.baseUrl, s0.apiKey, seriesId), subs, libName, srcName)
            else SeriesContent(detail, api.seriesBooks(s0.baseUrl, s0.apiKey, seriesId), emptyList(), subs, libName, srcName)
        }.fold({ SeriesPhase.Ready(it) }, { SeriesPhase.Error(it.friendlyMessage()) })
    }

    val content = (phase as? SeriesPhase.Ready)?.content
    val errorMsg = (phase as? SeriesPhase.Error)?.message
    val s = server
    val detail = content?.detail
    val isWeb = detail?.isWeb == true

    // Ids available for "select all" depend on the layout.
    val allIds = when {
        content == null -> emptyList()
        isWeb -> content.chapters.map { it.chapterId }
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
    // content behind the status bar) once the header has scrolled up past the toolbar.
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val titleVisible by remember(isWeb) {
        derivedStateOf {
            val idx: Int; val off: Int
            if (isWeb) { idx = listState.firstVisibleItemIndex; off = listState.firstVisibleItemScrollOffset }
            else { idx = gridState.firstVisibleItemIndex; off = gridState.firstVisibleItemScrollOffset }
            idx > 0 || off > 280
        }
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
                        isWeb = isWeb,
                        onClose = { selection.clear() },
                        onSelectAll = { selection.selectAll(allIds) },
                        onMarkRead = { markSelected(api, s, content, selection, read = true, ::runAction) },
                        onMarkUnread = { markSelected(api, s, content, selection, read = false, ::runAction) },
                        onDownload = { downloadSelected(api, s, content, selection, snackbar, scope) { reload() } },
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
            val bottomInset = padding.calculateBottomPadding()
            Box(Modifier.fillMaxSize()) {
                // Blurred cover backdrop from the top (behind the toolbar) down to just below the cover.
                if (content != null && s != null) {
                    SeriesBackdrop(s.baseUrl, s.apiKey, content.detail, topInset + 210.dp)
                }
                when {
                    errorMsg != null && content == null -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorRetry(errorMsg) { reloadTick++ } }
                    content == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
                    s != null && isWeb -> ChaptersLayout(s.baseUrl, s.apiKey, content, sortDesc, { sortDesc = !sortDesc }, selection, onOpenReader, onOpenSourceReader, onOpenReaderIncognito, onOpenSourceReaderIncognito, listState, topInset, bottomInset)
                    s != null -> BooksLayout(s.baseUrl, s.apiKey, content, selection, onOpenBook, onOpenReader, onOpenSeries, onOpenReaderIncognito, gridState, topInset, bottomInset)
                }
            }
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

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
        }
    }
}

// ── Selection top bar ──────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    isWeb: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = { Text("$count selected", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
        },
        actions = {
            IconButton(onClick = onMarkRead) { Icon(Icons.Filled.Check, contentDescription = "Mark read") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Mark unread") }, onClick = { menu = false; onMarkUnread() })
                if (isWeb) DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
                DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; onSelectAll() })
            }
        },
    )
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
    selection: SelectionState<String>,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenReaderIncognito: (String) -> Unit,
    gridState: LazyGridState,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 96.dp + bottomInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeader(
                baseUrl, apiKey, content.detail,
                countLabel = "${content.books.size} ${if (content.books.size == 1) "book" else "books"}",
                unread = content.books.count { it.readProgress?.completed != true },
                libraryName = content.libraryName,
                sourceName = content.sourceName,
            )
        }
        if (content.subseries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
        }
        if (content.books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Books · ${content.books.size}") }
            items(content.books, key = { it.id }) { book ->
                val selected = selection.isSelected(book.id)
                Box(
                    Modifier.combinedClickable(
                        onClick = { if (selection.active) selection.toggle(book.id) else onOpenBook(book.id) },
                        onLongClick = { selection.toggle(book.id) },
                    ).then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)) else Modifier),
                ) {
                    CoverCard(
                        coverUrl = bookCoverUrl(baseUrl, book.id),
                        apiKey = apiKey,
                        title = bookLabel(book),
                        subtitle = book.numberDisplay,
                        unread = null,
                        onClick = { if (selection.active) selection.toggle(book.id) else onOpenBook(book.id) },
                        width = null,
                    )
                    if (selected) Icon(Icons.Filled.Check, "Selected", Modifier.align(Alignment.TopStart).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ChaptersLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    sortDesc: Boolean,
    onToggleSort: () -> Unit,
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
    val ascending = content.chapters.sortedWith(compareBy(nullsLast()) { it.number })
    val display = if (sortDesc) ascending.asReversed() else ascending
    val downloaded = ascending.count { it.downloaded }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 96.dp + bottomInset)) {
        item {
            SeriesHeader(
                baseUrl, apiKey, content.detail,
                countLabel = "${ascending.size} chapters · $downloaded downloaded",
                unread = ascending.count { !it.read },
                libraryName = content.libraryName,
                sourceName = content.sourceName,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Chapters · ${ascending.size}")
                Spacer(Modifier.width(8.dp))
                Text(
                    if (sortDesc) "Newest first" else "Oldest first",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .combinedClickable(onClick = onToggleSort, onLongClick = onToggleSort)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
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
            .padding(vertical = 12.dp),
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
                    sourceName?.takeIf { it.isNotBlank() } ?: libraryName?.takeIf { it.isNotBlank() },
                    detail.language.takeIf { it.isNotBlank() }?.uppercase(),
                ).joinToString(" · ")
                if (provenance.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(provenance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Series summary capped at 3 lines with a "Read more" toggle when it overflows. */
@Composable
private fun ExpandableSummary(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column {
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

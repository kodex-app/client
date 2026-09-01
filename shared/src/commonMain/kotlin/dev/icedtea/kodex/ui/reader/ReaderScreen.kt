package dev.icedtea.kodex.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.data.model.ServerConnection
import dev.icedtea.kodex.network.BookDto
import dev.icedtea.kodex.network.BookmarkDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.ReadProgressDto
import dev.icedtea.kodex.ui.catalog.bookPageUrl
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.reader.ebook.EbookBookmarks
import dev.icedtea.kodex.ui.reader.ebook.EbookOrigin
import dev.icedtea.kodex.ui.reader.ebook.EbookReaderScreen
import dev.icedtea.kodex.ui.reader.ebook.EbookSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState

    /**
     * [progress] is fetched separately from the book: `BookDto.readProgress` carries only page and
     * completed, and a reflowable book resumes from the CFI that only `/read-progress` returns.
     *
     * [complete] is false while the reader is drawing a book opened straight from its sibling row —
     * enough to render pages, with [progress] and the bookmarks still in flight behind it.
     */
    data class Ready(val book: BookDto, val progress: ReadProgressDto?, val complete: Boolean = true) : ReaderState
}

/** The book currently open; [edge] is set when arriving from a sibling (start of it / end of it). */
private data class BookTarget(val id: String, val edge: ReaderEdge? = null)

/** Everything a book needs before its first page can be drawn, loaded as one unit. */
private data class BookBundle(val book: BookDto, val progress: ReadProgressDto?, val bookmarks: List<BookmarkDto>)

/**
 * The three requests in parallel rather than one after another - they are independent, and run in
 * series they turned every chapter turn into three sequential round trips.
 */
private suspend fun loadBundle(api: KodexApi, s: ServerConnection, id: String): BookBundle = coroutineScope {
    val book = async { api.book(s.baseUrl, s.apiKey, id) }
    val progress = async { runCatching { api.readProgress(s.baseUrl, s.apiKey, id) }.getOrNull() }
    val bookmarks = async { runCatching { api.bookBookmarks(s.baseUrl, s.apiKey, id) }.getOrDefault(emptyList()) }
    BookBundle(book.await(), progress.await(), bookmarks.await())
}

/**
 * Reader for a downloaded local book (comic/DIVINA + PDF). EPUB is gated with a message. The series'
 * other books drive cross-chapter navigation (prev/next + the chapter menu); jumping to a sibling swaps
 * the book in place so back returns to the series, not a chain of readers (matching the web).
 */
@Composable
fun ReaderScreen(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    bookId: String,
    onBack: () -> Unit,
    startPage: Int? = null,
    incognito: Boolean = false,
    onOpenSeriesFromReader: ((dev.icedtea.kodex.ui.main.DetailRoute) -> Unit)? = null,
) {
    val server by session.activeServer.collectAsStateSafe()
    var target by remember(bookId) { mutableStateOf(BookTarget(bookId)) }
    var state by remember(bookId) { mutableStateOf<ReaderState>(ReaderState.Loading) }
    var siblings by remember(bookId) { mutableStateOf<List<BookDto>>(emptyList()) }
    // Series name for the top bar's first line; BookDto carries only the book's own title.
    var seriesTitle by remember(bookId) { mutableStateOf<String?>(null) }
    // Page bookmarks for the open book, keyed by target so a chapter swap reloads them.
    var bookmarks by remember(bookId) { mutableStateOf<List<BookmarkDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    // Survives the per-book remount below; see ReaderSeriesState.
    val seriesState = rememberReaderSeriesState(bookId)

    // Sibling books already loaded, so turning into one is instant. Filled by [ReaderChapterRef.preload]
    // while you are still reading the tail of the current book - the same trick Mihon's
    // ReaderViewModel.preload plays, and what keeps a chapter turn from starting at a blank screen.
    val loaded = remember(bookId) { mutableStateMapOf<String, BookBundle>() }
    val inFlight = remember(bookId) { mutableMapOf<String, Deferred<Result<BookBundle>>>() }

    /**
     * One fetch per book, shared by the preloader and by opening it for real. Turning a book while its
     * preload is still running then waits on that request rather than firing a second copy of it.
     *
     * The result is carried rather than thrown: a failed `async` would take the screen's whole scope
     * down with it, and a book that won't load is something the caller decides what to do about.
     */
    fun bundleAsync(s: ServerConnection, id: String): Deferred<Result<BookBundle>> =
        inFlight.getOrPut(id) {
            scope.async {
                runCatching { loadBundle(api, s, id) }
                    .onSuccess { loaded[id] = it }
                    .also { inFlight.remove(id) } // so a failure can be retried by opening it again
            }
        }

    fun preloadBook(id: String) {
        val s = server ?: return
        if (loaded.containsKey(id)) return
        bundleAsync(s, id)
    }

    suspend fun reloadBookmarks(s: ServerConnection, id: String) {
        bookmarks = runCatching { api.bookBookmarks(s.baseUrl, s.apiKey, id) }.getOrDefault(emptyList())
        loaded[id]?.let { loaded[id] = it.copy(bookmarks = bookmarks) }
    }

    LaunchedEffect(target.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        val open = state as? ReaderState.Ready
        // Already seeded from the preload cache by openBook - refetching would only put the spinner
        // back for the length of a round trip that has already happened.
        if (open?.book?.id == target.id && open.complete) return@LaunchedEffect
        // A book opened from its sibling row is already on screen showing pages; it only needs the
        // bundle filled in behind it, so it must not be thrown back to the loading shell here.
        if (open?.book?.id != target.id) {
            state = ReaderState.Loading
            bookmarks = emptyList()
        }
        val bundle = loaded[target.id]?.let { Result.success(it) } ?: bundleAsync(s, target.id).await()
        bundle
            .onSuccess { bookmarks = it.bookmarks; state = ReaderState.Ready(it.book, it.progress) }
            .onFailure {
                // Only a book with nothing on screen becomes an error. One already being read keeps
                // its pages — those come from their own requests — and simply goes without bookmarks.
                if ((state as? ReaderState.Ready)?.book?.id != target.id) state = ReaderState.Error(it.friendlyMessage())
            }
    }
    // Sibling books for cross-chapter navigation (ordered by number ascending by the API).
    //
    // Latched rather than read live off the open book: a swap passes through Loading, where there is
    // no book and so no series id, and keying the fetch on that emptied the sibling list and fetched
    // it again on every chapter turn. Every book here belongs to one series, so once is enough.
    val loadedSeriesId = (state as? ReaderState.Ready)?.book?.seriesId
    var seriesKey by remember(bookId) { mutableStateOf<String?>(null) }
    LaunchedEffect(loadedSeriesId) { if (loadedSeriesId != null) seriesKey = loadedSeriesId }
    LaunchedEffect(seriesKey, server?.id) {
        val s = server ?: return@LaunchedEffect
        val sid = seriesKey ?: return@LaunchedEffect
        siblings = runCatching { api.seriesBooks(s.baseUrl, s.apiKey, sid) }.getOrDefault(emptyList())
        seriesTitle = runCatching { api.seriesDetail(s.baseUrl, s.apiKey, sid) }.getOrNull()
            ?.let { it.title.ifBlank { it.name } }?.takeIf { it.isNotBlank() }
    }

    // Swap the open book, in one frame: seeding the new state alongside the new target is what turns
    // the swap into a page turn instead of a trip through the black loading shell.
    fun openBook(b: BookDto, edge: ReaderEdge) {
        val ready = loaded[b.id]
        target = BookTarget(b.id, edge)
        bookmarks = ready?.bookmarks.orEmpty()
        state = when {
            ready != null -> ReaderState.Ready(ready.book, ready.progress)
            // Not warmed yet — open on the sibling row anyway. It already carries everything a page
            // needs (page count, media type), and arriving from a boundary pins the page at one end,
            // so the progress the bundle would bring has nothing left to say. The pages then load as
            // pages always do, each behind its own spinner, instead of the turn being held at a black
            // screen until a round trip that only fills in bookmarks comes back.
            b.pageCount > 0 -> ReaderState.Ready(b, progress = null, complete = false)
            // No page count on the row (a reflowable book, or a payload without one): there is nothing
            // to draw until the book itself is fetched.
            else -> ReaderState.Loading
        }
    }

    when (val st = state) {
        is ReaderState.Loading -> ReaderShell(onBack) { Spinner() }
        is ReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is ReaderState.Ready -> {
            val s = server
            val book = st.book
            val ebookFormat = foliateFormat(book.mediaType)
            // "Series details" in the reader toolbar. Standalone books have no series to open.
            val openSeries: (() -> Unit)? = book.seriesId?.let { sid ->
                onOpenSeriesFromReader?.let { open -> { open(dev.icedtea.kodex.ui.main.DetailRoute.SeriesDetail(sid)) } }
            }
            when {
                s == null -> ReaderShell(onBack) { ReaderMessage("Not signed in.") }
                ebookFormat != null -> {
                    val current = target
                    val source = rememberEbookSource(
                        api = api,
                        server = s,
                        book = book,
                        format = ebookFormat,
                        edge = current.edge,
                        progress = st.progress,
                        seriesTitle = seriesTitle,
                        siblings = siblings,
                        bookmarks = bookmarks,
                        incognito = incognito,
                        scope = scope,
                        onOpenSibling = ::openBook,
                        onBookmarksChanged = { reloadBookmarks(s, book.id) },
                    )
                    // The reader keeps its own position state, so a book swap has to remount it.
                    key(current.id) { EbookReaderScreen(session, api, appSettings, source, onBack, openSeries) }
                }

                book.pageCount <= 0 -> ReaderShell(onBack) { ReaderMessage("This book has no readable pages.") }
                else -> {
                    val current = target
                    // Toggle the bookmark on a page: drop the existing one, or add one if there isn't
                    // any. Reloads afterwards so the top bar reflects what the server actually stored.
                    val bookmarkPages = remember(bookmarks) { bookmarks.mapNotNull { it.page }.toSet() }
                    val toggleBookmark: (Int) -> Unit = { pg ->
                        val existing = bookmarks.firstOrNull { it.page == pg }
                        scope.launch {
                            runCatching {
                                if (existing != null) {
                                    api.deleteBookmark(s.baseUrl, s.apiKey, book.id, existing.id)
                                } else {
                                    api.addBookmark(s.baseUrl, s.apiKey, book.id, pg, label = null)
                                }
                            }
                            reloadBookmarks(s, book.id)
                        }
                    }
                    val source = remember(current, s.baseUrl, siblings, seriesTitle, bookmarkPages, book.pageCount, st.complete) {
                        val idx = siblings.indexOfFirst { it.id == book.id }
                        val nav = if (siblings.size > 1 && idx >= 0) {
                            fun ref(b: BookDto?) = b?.let { sib ->
                                ReaderChapterRef(
                                    title = chapterTitle(sib),
                                    open = { edge -> openBook(sib, edge) },
                                    preloadPageUrl = { pg -> bookPageUrl(s.baseUrl, sib.id, pg) },
                                    preload = { preloadBook(sib.id) },
                                )
                            }
                            ReaderChapterNav(
                                prev = ref(siblings.getOrNull(idx - 1)),
                                next = ref(siblings.getOrNull(idx + 1)),
                                chapters = siblings.map { sib -> readerChapterItem(sib, book.id) { openBook(sib, ReaderEdge.FIRST) } },
                            )
                        } else null
                        val bookLabel = book.title.ifBlank { book.numberDisplay ?: "Reading" }
                        ReaderSource(
                            // With the series known it takes the top line and the book becomes the
                            // subtitle; standalone books keep their own title on the top line.
                            title = seriesTitle ?: bookLabel,
                            subtitle = bookLabel.takeIf { seriesTitle != null },
                            pageCount = book.pageCount,
                            initialPage = when (current.edge) {
                                ReaderEdge.FIRST -> 1
                                ReaderEdge.LAST -> book.pageCount
                                null -> startPage?.coerceIn(1, book.pageCount) ?: book.readProgress?.page ?: 1
                            },
                            kind = if (book.mediaType?.contains("pdf", ignoreCase = true) == true) "pdf" else "comic",
                            seriesId = book.seriesId,
                            apiKey = s.apiKey,
                            pageUrlFor = { pg -> bookPageUrl(s.baseUrl, book.id, pg) },
                            onPersist = if (incognito) ({ _, _ -> }) else ({ pg, completed -> api.saveReadProgress(s.baseUrl, s.apiKey, book.id, pg, completed) }),
                            incognito = incognito,
                            nav = nav,
                            webUrl = "${s.baseUrl}/books/${book.id}/read",
                            // Held back until this book's own bookmarks have arrived. A book opened
                            // from its sibling row has none loaded yet, and the server does not merge
                            // duplicates — bookmarking a page it already holds one for would file a
                            // second copy rather than clearing the first.
                            bookmarks = if (st.complete) ReaderBookmarks(bookmarkPages, toggleBookmark) else null,
                        )
                    }
                    // The reader keeps its own page state, so a book swap has to remount it - but its
                    // prefs and detected mode belong to the series, so they are held out here.
                    key(current.id) { ImageReaderScreen(session, api, source, onBack, openSeries, seriesState) }
                }
            }
        }
    }
}

private fun chapterTitle(book: BookDto): String = book.title.ifBlank { book.numberDisplay ?: "Book" }

/**
 * A book-list row for [book], carrying the read state the list draws its markers from.
 *
 * `readProgress` is the list payload's own state — good enough for "read / where I left off"; the
 * open book's live position comes from the reader itself, and that row is marked by [currentId]
 * regardless of what the list says.
 */
private fun readerChapterItem(book: BookDto, currentId: String, open: () -> Unit) = ReaderChapterItem(
    title = chapterTitle(book),
    active = book.id == currentId,
    read = book.readProgress?.completed == true,
    progressPage = book.readProgress?.page?.takeIf { book.readProgress?.completed != true },
    open = open,
)

/** Black full-screen shell with a persistent back button (for loading/error/gate states). */
@Composable
internal fun ReaderShell(onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        content()
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
internal fun BoxScope.Spinner() =
    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

@Composable
internal fun ReaderMessage(text: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = Color.White, textAlign = TextAlign.Center)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", color = Color.White) }
        }
    }
}

/**
 * The foliate engine that reads this media type, or null for image-based books (comics and PDFs,
 * whose pages the server rasterizes). Mirrors the web reader's dispatch table.
 */
private fun foliateFormat(mediaType: String?): String? = when (mediaType) {
    "application/epub+zip" -> "epub"
    "application/x-mobipocket-ebook", "application/x-mobi8-ebook" -> "mobi"
    "application/x-fictionbook+xml" -> "fb2"
    else -> null
}

/**
 * Builds the [EbookSource] for a library book: where its bytes come from, where it resumes, and the
 * sibling books that drive cross-chapter navigation.
 */
@Composable
private fun rememberEbookSource(
    api: KodexApi,
    server: ServerConnection,
    book: BookDto,
    format: String,
    edge: ReaderEdge?,
    progress: ReadProgressDto?,
    seriesTitle: String?,
    siblings: List<BookDto>,
    bookmarks: List<BookmarkDto>,
    incognito: Boolean,
    scope: CoroutineScope,
    onOpenSibling: (BookDto, ReaderEdge) -> Unit,
    onBookmarksChanged: suspend () -> Unit,
): EbookSource = remember(book.id, server.baseUrl, siblings, seriesTitle, bookmarks, progress, edge, incognito) {
    val idx = siblings.indexOfFirst { it.id == book.id }
    val nav = if (siblings.size > 1 && idx >= 0) {
        fun ref(b: BookDto?) = b?.let { sib ->
            ReaderChapterRef(chapterTitle(sib), { e -> onOpenSibling(sib, e) })
        }
        ReaderChapterNav(
            prev = ref(siblings.getOrNull(idx - 1)),
            next = ref(siblings.getOrNull(idx + 1)),
            chapters = siblings.map { sib ->
                readerChapterItem(sib, book.id) { onOpenSibling(sib, ReaderEdge.FIRST) }
            },
        )
    } else {
        null
    }
    val bookLabel = book.title.ifBlank { book.numberDisplay ?: "Reading" }
    EbookSource(
        title = seriesTitle ?: bookLabel,
        subtitle = bookLabel.takeIf { seriesTitle != null },
        format = format,
        origin = EbookOrigin.Book(book.id),
        seriesId = book.seriesId,
        // Arriving from a sibling pins the very start/end; otherwise resume where the CFI left off.
        initialLocator = if (edge == null) progress?.locator else null,
        initialFraction = when (edge) {
            ReaderEdge.FIRST -> 0.0
            ReaderEdge.LAST -> 1.0
            null -> progress?.fraction ?: 0.0
        },
        onPersist = if (incognito) {
            { _, _, _, _ -> }
        } else {
            { fraction, locator, sectionTotal, completed ->
                // A reflowable book has no real pages, but progress is stored per page everywhere
                // else; keep a spine-based proxy so lists and "continue reading" still work.
                val page = (fraction * sectionTotal.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
                api.saveReadProgress(
                    server.baseUrl, server.apiKey, book.id,
                    page = page, completed = completed, locator = locator, fraction = fraction,
                )
            }
        },
        incognito = incognito,
        nav = nav,
        webUrl = "${server.baseUrl}/books/${book.id}/read",
        bookmarks = EbookBookmarks(
            items = bookmarks,
            add = { locator, fraction, label ->
                scope.launch {
                    runCatching { api.addEbookBookmark(server.baseUrl, server.apiKey, book.id, locator, fraction, label) }
                    onBookmarksChanged()
                }
            },
            delete = { id ->
                scope.launch {
                    runCatching { api.deleteBookmark(server.baseUrl, server.apiKey, book.id, id) }
                    onBookmarksChanged()
                }
            },
        ),
    )
}

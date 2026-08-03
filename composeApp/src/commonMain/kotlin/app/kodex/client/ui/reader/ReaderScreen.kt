package app.kodex.client.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.BookDto
import app.kodex.client.network.BookmarkDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.ReadProgressDto
import app.kodex.client.ui.catalog.bookPageUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.reader.ebook.EbookBookmarks
import app.kodex.client.ui.reader.ebook.EbookOrigin
import app.kodex.client.ui.reader.ebook.EbookReaderScreen
import app.kodex.client.ui.reader.ebook.EbookSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState

    /**
     * [progress] is fetched separately from the book: `BookDto.readProgress` carries only page and
     * completed, and a reflowable book resumes from the CFI that only `/read-progress` returns.
     */
    data class Ready(val book: BookDto, val progress: ReadProgressDto?) : ReaderState
}

/** The book currently open; [edge] is set when arriving from a sibling (start of it / end of it). */
private data class BookTarget(val id: String, val edge: ReaderEdge? = null)

/**
 * Reader for a downloaded local book (comic/DIVINA + PDF). EPUB is gated with a message. The series'
 * other books drive cross-chapter navigation (prev/next + the chapter menu); jumping to a sibling swaps
 * the book in place so back returns to the series, not a chain of readers (matching the web).
 */
@Composable
fun ReaderScreen(
    session: SessionManager,
    api: KodexApi,
    bookId: String,
    onBack: () -> Unit,
    startPage: Int? = null,
    incognito: Boolean = false,
    onGoHome: (() -> Unit)? = null,
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

    suspend fun reloadBookmarks(s: ServerConnection, id: String) {
        bookmarks = runCatching { api.bookBookmarks(s.baseUrl, s.apiKey, id) }.getOrDefault(emptyList())
    }
    LaunchedEffect(target.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        bookmarks = emptyList()
        reloadBookmarks(s, target.id)
    }

    LaunchedEffect(target.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        state = ReaderState.Loading
        state = runCatching {
            val book = api.book(s.baseUrl, s.apiKey, target.id)
            val progress = runCatching { api.readProgress(s.baseUrl, s.apiKey, target.id) }.getOrNull()
            ReaderState.Ready(book, progress)
        }.getOrElse { ReaderState.Error(it.friendlyMessage()) }
    }
    // Sibling books for cross-chapter navigation (ordered by number ascending by the API).
    val loadedSeriesId = (state as? ReaderState.Ready)?.book?.seriesId
    LaunchedEffect(loadedSeriesId, server?.id) {
        val s = server ?: return@LaunchedEffect
        siblings = if (loadedSeriesId != null) runCatching { api.seriesBooks(s.baseUrl, s.apiKey, loadedSeriesId) }.getOrDefault(emptyList()) else emptyList()
        seriesTitle = if (loadedSeriesId != null) {
            runCatching { api.seriesDetail(s.baseUrl, s.apiKey, loadedSeriesId) }.getOrNull()
                ?.let { it.title.ifBlank { it.name } }?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    // Swap the open book. Drop to Loading here (not only in the effect) so the reader never renders the
    // new book's pages against the old page count.
    fun openBook(b: BookDto, edge: ReaderEdge) {
        state = ReaderState.Loading
        target = BookTarget(b.id, edge)
    }

    when (val st = state) {
        is ReaderState.Loading -> ReaderShell(onBack) { Spinner() }
        is ReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is ReaderState.Ready -> {
            val s = server
            val book = st.book
            val ebookFormat = foliateFormat(book.mediaType)
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
                    key(current.id) { EbookReaderScreen(session, api, source, onBack, onGoHome) }
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
                    val source = remember(current, s.baseUrl, siblings, seriesTitle, bookmarkPages, book.pageCount) {
                        val idx = siblings.indexOfFirst { it.id == book.id }
                        val nav = if (siblings.size > 1 && idx >= 0) {
                            fun ref(b: BookDto?) = b?.let { sib ->
                                ReaderChapterRef(chapterTitle(sib), { edge -> openBook(sib, edge) }, { pg -> bookPageUrl(s.baseUrl, sib.id, pg) })
                            }
                            ReaderChapterNav(
                                prev = ref(siblings.getOrNull(idx - 1)),
                                next = ref(siblings.getOrNull(idx + 1)),
                                chapters = siblings.map { sib -> ReaderChapterItem(chapterTitle(sib), sib.id == book.id, { openBook(sib, ReaderEdge.FIRST) }) },
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
                            bookmarks = ReaderBookmarks(bookmarkPages, toggleBookmark),
                        )
                    }
                    // The reader keeps its own page state, so a book swap has to remount it.
                    key(current.id) { ImageReaderScreen(session, api, source, onBack, onGoHome) }
                }
            }
        }
    }
}

private fun chapterTitle(book: BookDto): String = book.title.ifBlank { book.numberDisplay ?: "Chapter" }

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
internal fun ReaderMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, textAlign = TextAlign.Center)
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
                ReaderChapterItem(chapterTitle(sib), sib.id == book.id) { onOpenSibling(sib, ReaderEdge.FIRST) }
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

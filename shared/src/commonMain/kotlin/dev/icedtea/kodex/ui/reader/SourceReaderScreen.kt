package dev.icedtea.kodex.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.data.model.ServerConnection
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.ReadProgressDto
import dev.icedtea.kodex.ui.catalog.sourcePageUrl
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.main.SourceSeriesContext
import dev.icedtea.kodex.ui.reader.ebook.EbookOrigin
import dev.icedtea.kodex.ui.reader.ebook.EbookReaderScreen
import dev.icedtea.kodex.ui.reader.ebook.EbookSource
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** [dev.icedtea.kodex.network.SourceDescriptor.kind] of a novel source — its chapters are text, not page images. */
private const val KIND_BOOK = "BOOK"

private sealed interface SourceReaderState {
    data object Loading : SourceReaderState
    data class Error(val message: String) : SourceReaderState
    data class Ready(val chapterId: String, val pageCount: Int, val progress: ReadProgressDto?) : SourceReaderState
}

/**
 * Page count + resume position for one streamed chapter, fetched in parallel: the count makes the
 * server reach out to the remote source, so running the two in series doubled the wait before the
 * reader could draw anything.
 */
private suspend fun loadChapter(
    api: KodexApi,
    s: ServerConnection,
    providerId: String,
    chapterId: String,
): SourceReaderState.Ready = coroutineScope {
    val pageCount = async { api.sourceChapterPageCount(s.baseUrl, s.apiKey, providerId, chapterId) }
    val progress = async { runCatching { api.sourceProgress(s.baseUrl, s.apiKey, providerId, chapterId) }.getOrNull() }
    SourceReaderState.Ready(chapterId, pageCount.await(), progress.await())
}

/** Kodex web UI source-reader deep link for this chapter (mirrors the web's `/source-read` route). */
private fun sourceReadUrl(baseUrl: String, providerId: String, chapterId: String, chapterName: String?, seriesId: String?): String =
    buildString {
        append(baseUrl.trimEnd('/'))
        append("/source-read?provider=").append(providerId.encodeURLParameter())
        append("&chapter=").append(chapterId.encodeURLParameter())
        chapterName?.takeIf { it.isNotBlank() }?.let { append("&title=").append(it.encodeURLParameter()) }
        seriesId?.let { append("&series=").append(it.encodeURLParameter()) }
    }

/** The chapter currently open; [edge] is set when arriving from a sibling (start of it / end of it). */
private data class ChapterTarget(val id: String, val name: String?, val edge: ReaderEdge? = null)

/**
 * A sibling chapter for reader navigation, normalised so both paths into this screen feed one nav
 * builder: a Browse read supplies the source's live chapter list, a followed series its stored
 * (tracked-catalogue) one.
 *
 * [read]/[page] are the per-user state the book list marks rows with; the live source list carries
 * none, so they stay unset for a Browse read.
 */
private data class NavChapter(
    val id: String,
    val name: String,
    val number: Double?,
    val read: Boolean = false,
    val page: Int? = null,
)

/**
 * Streams a content-source chapter's pages directly — no download (the Mihon-style path). Feeds the
 * shared ImageReader once the page count + resume position are known.
 *
 * With a [sourceSeries] (reading straight from Browse, series not followed) the source's live chapter
 * list also drives prev/next navigation and the chapter menu; jumping to a sibling swaps the chapter in
 * place, so back returns to the series page instead of unwinding a chain of chapters (matching the web).
 */
@Composable
fun SourceReaderScreen(
    session: SessionManager,
    api: KodexApi,
    appSettings: AppSettings,
    providerId: String,
    chapterId: String,
    seriesId: String?,
    chapterName: String?,
    onBack: () -> Unit,
    sourceSeries: SourceSeriesContext? = null,
    incognito: Boolean = false,
    onOpenSeriesFromReader: ((dev.icedtea.kodex.ui.main.DetailRoute) -> Unit)? = null,
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var target by remember(chapterId) { mutableStateOf(ChapterTarget(chapterId, chapterName)) }
    var state by remember(chapterId) { mutableStateOf<SourceReaderState>(SourceReaderState.Loading) }
    // Prefs and the detected reading mode belong to the series, so they are held above the per-chapter
    // remount below instead of being refetched and re-probed on every turn. See ReaderSeriesState.
    val seriesState = rememberReaderSeriesState(providerId, seriesId, sourceSeries?.externalId)

    // Neighbouring chapters already resolved, so committing the between-chapters page swaps straight
    // into pages. Warmed by [ReaderChapterRef.preload] near either end of the current chapter, the way
    // Mihon's ReaderViewModel.preload warms its adjacent chapters while you read.
    val loaded = remember(chapterId) { mutableStateMapOf<String, SourceReaderState.Ready>() }
    val loading = remember(chapterId) { mutableSetOf<String>() }

    fun preloadChapter(id: String) {
        val s = server ?: return
        if (loaded.containsKey(id) || !loading.add(id)) return
        scope.launch {
            runCatching { loadChapter(api, s, providerId, id) }
                .onSuccess { loaded[id] = it }
                .onFailure { loading.remove(id) } // opening it for real will try again, and report why
        }
    }

    // Sibling chapters for cross-chapter navigation. Both entry points have a chapter list available:
    // a Browse read carries the source series' identity and queries the source live, while a followed
    // series is opened by local id and reads the tracked catalogue the series screen itself lists.
    // (Missing the second case left the chapter menu and prev/next permanently disabled there.)
    var siblings by remember(sourceSeries?.externalId, seriesId) { mutableStateOf<List<NavChapter>>(emptyList()) }
    // Series name for the top bar's first line — carried in for a Browse read, fetched for a followed one.
    var followedTitle by remember(seriesId) { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceSeries?.externalId, seriesId, server?.id) {
        val s = server ?: return@LaunchedEffect
        siblings = runCatching {
            when {
                sourceSeries != null ->
                    api.sourceChapters(s.baseUrl, s.apiKey, sourceSeries.providerId, sourceSeries.externalId)
                        .map { NavChapter(it.externalId, it.name, it.number) }
                seriesId != null ->
                    api.seriesChapters(s.baseUrl, s.apiKey, seriesId)
                        .map { NavChapter(it.chapterId, it.name.orEmpty(), it.number, read = it.read, page = it.page) }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        if (sourceSeries == null && seriesId != null) {
            followedTitle = runCatching { api.seriesDetail(s.baseUrl, s.apiKey, seriesId) }.getOrNull()
                ?.let { it.title.ifBlank { it.name } }?.takeIf { it.isNotBlank() }
        }
    }

    // Which reader this chapter needs. `sourceSeries` already knows for a Browse read; a followed
    // series arrives by local id with no such hint, so ask the server what kind the provider is —
    // the same lookup the web does. Null means "not resolved yet".
    var isNovel by remember(providerId) { mutableStateOf(sourceSeries?.isNovel?.takeIf { it }) }
    // Also kept for "Series details": reopening a Browse series needs the source descriptor, which
    // only this lookup has.
    var descriptor by remember(providerId) { mutableStateOf<dev.icedtea.kodex.network.SourceDescriptor?>(null) }
    LaunchedEffect(providerId, server?.id) {
        val s = server ?: return@LaunchedEffect
        val found = runCatching {
            api.contentSources(s.baseUrl, s.apiKey).firstOrNull { it.id == providerId }
        }.getOrNull()
        descriptor = found
        if (isNovel == null) isNovel = found?.kind == KIND_BOOK
    }

    /**
     * A followed chapter reopens its local series; a Browse read reopens the source series screen,
     * rebuilding a seed from the identity the reader was handed.
     */
    val openSeries: (() -> Unit)? = onOpenSeriesFromReader?.let { open ->
        val d = descriptor
        when {
            seriesId != null -> ({ open(dev.icedtea.kodex.ui.main.DetailRoute.SeriesDetail(seriesId)) })
            sourceSeries != null && d != null -> ({
                open(
                    dev.icedtea.kodex.ui.main.DetailRoute.SourceSeries(
                        d,
                        dev.icedtea.kodex.network.SourceSearchResult(
                            providerId = sourceSeries.providerId,
                            externalId = sourceSeries.externalId,
                            title = sourceSeries.title,
                            coverUrl = sourceSeries.coverUrl,
                        ),
                    ),
                )
            })
            else -> null
        }
    }

    LaunchedEffect(target.id, server?.id, isNovel) {
        val s = server ?: return@LaunchedEffect
        // Already seeded from the preload cache by openChapter - refetching would only put the spinner
        // back for the length of a round trip that has already happened.
        if ((state as? SourceReaderState.Ready)?.chapterId == target.id) return@LaunchedEffect
        // A novel chapter has no page images to count; the ebook reader resolves it from its manifest.
        when (isNovel) {
            null -> return@LaunchedEffect
            true -> {
                state = SourceReaderState.Loading
                state = runCatching {
                    SourceReaderState.Ready(target.id, 0, api.sourceProgress(s.baseUrl, s.apiKey, providerId, target.id))
                }.getOrElse { SourceReaderState.Error(it.friendlyMessage()) }
            }

            false -> {
                state = SourceReaderState.Loading
                state = runCatching { loadChapter(api, s, providerId, target.id) }
                    .onSuccess { loaded[target.id] = it }
                    .getOrElse { SourceReaderState.Error(it.friendlyMessage()) }
            }
        }
    }

    // Swap the open chapter, in one frame when it has been preloaded: seeding the new state alongside
    // the new target is what turns the swap into a page turn instead of a trip through the black
    // loading shell. An un-warmed chapter still falls back to Loading rather than rendering its pages
    // against the previous chapter's page count.
    fun openChapter(chapter: NavChapter, edge: ReaderEdge) {
        val ready = loaded[chapter.id]
        target = ChapterTarget(chapter.id, chapter.name.takeIf { it.isNotBlank() }, edge)
        state = ready ?: SourceReaderState.Loading
    }

    val nav = server?.let { s ->
        rememberChapterNav(s, providerId, siblings, target.id, ::openChapter, ::preloadChapter)
    }

    when (val st = state) {
        is SourceReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is SourceReaderState.Loading -> ReaderShell(onBack) { Spinner() }

        is SourceReaderState.Ready -> {
            val s = server
            when {
                s == null -> ReaderShell(onBack) { ReaderMessage("Not signed in.") }
                isNovel == true -> {
                    val current = target
                    val chapterLabel = current.name?.takeIf { it.isNotBlank() }
                    val series = sourceSeries?.title?.takeIf { it.isNotBlank() } ?: followedTitle
                    val ebook = remember(current, s.baseUrl, st, nav, followedTitle, incognito) {
                        EbookSource(
                            title = series ?: chapterLabel ?: "Reading",
                            subtitle = chapterLabel.takeIf { series != null },
                            // The core builds an ephemeral single-chapter EPUB for BOOK sources, so
                            // this reads through exactly the same path as a downloaded EPUB.
                            format = "epub",
                            origin = EbookOrigin.SourceChapter(providerId, current.id),
                            seriesId = seriesId ?: sourceSeries?.let { "src:${it.providerId}:${it.externalId}" },
                            initialLocator = null,
                            // Source progress has no CFI column — it stores a 0–100 page proxy, which
                            // maps back to a fraction (the same convention the web uses).
                            initialFraction = when (current.edge) {
                                ReaderEdge.FIRST -> 0.0
                                ReaderEdge.LAST -> 1.0
                                null -> st.progress?.takeIf { !it.completed }?.let { (it.page / 100.0).coerceIn(0.0, 1.0) } ?: 0.0
                            },
                            onPersist = if (incognito) {
                                { _, _, _, _ -> }
                            } else {
                                { fraction, _, _, completed ->
                                    api.saveSourceProgress(
                                        s.baseUrl, s.apiKey, providerId, current.id,
                                        page = (fraction * 100).roundToInt().coerceAtLeast(1),
                                        completed = completed,
                                        seriesId = seriesId, chapterName = current.name,
                                        sourceSeriesId = sourceSeries?.externalId.takeIf { seriesId == null },
                                        sourceSeriesName = sourceSeries?.title.takeIf { seriesId == null },
                                        sourceCoverUrl = sourceSeries?.coverUrl.takeIf { seriesId == null },
                                    )
                                }
                            },
                            incognito = incognito,
                            nav = nav,
                            webUrl = sourceReadUrl(s.baseUrl, providerId, current.id, current.name, seriesId),
                            // Bookmarks are book-scoped server-side; a streamed chapter isn't a book.
                            bookmarks = null,
                        )
                    }
                    key(current.id) { EbookReaderScreen(session, api, appSettings, ebook, onBack, openSeries) }
                }

                st.pageCount <= 0 -> ReaderShell(onBack) {
                    ReaderMessage("This chapter can't be streamed right now —\nthe source returned no pages.")
                }
                else -> {
                    val current = target
                    val source = remember(current, s.baseUrl, st, nav, followedTitle) {
                        val chapterLabel = current.name?.takeIf { it.isNotBlank() }
                        val series = sourceSeries?.title?.takeIf { it.isNotBlank() } ?: followedTitle
                        ReaderSource(
                            // Series on the top line, this chapter beneath; with no series name known
                            // the chapter takes the top line rather than leaving it blank.
                            title = series ?: chapterLabel ?: "Reading",
                            subtitle = chapterLabel.takeIf { series != null },
                            pageCount = st.pageCount,
                            initialPage = when (current.edge) {
                                ReaderEdge.FIRST -> 1
                                ReaderEdge.LAST -> st.pageCount
                                null -> st.progress?.takeIf { !it.completed }?.page ?: 1
                            },
                            kind = "comic",
                            // Display settings follow the series: its local id when followed, else the
                            // source series itself, so every chapter of it shares one set (as in the web).
                            seriesId = seriesId ?: sourceSeries?.let { "src:${it.providerId}:${it.externalId}" },
                            apiKey = s.apiKey,
                            // Source page images are 0-indexed; the reader speaks 1-based pages.
                            pageUrlFor = { pg -> sourcePageUrl(s.baseUrl, providerId, current.id, pg - 1) },
                            onPersist = if (incognito) ({ _, _ -> }) else ({ pg, completed ->
                                api.saveSourceProgress(
                                    s.baseUrl, s.apiKey, providerId, current.id,
                                    page = pg, completed = completed,
                                    seriesId = seriesId, chapterName = current.name,
                                    // Browse reads have no local series: cache the source series' identity
                                    // on the progress record so History can render the entry.
                                    sourceSeriesId = sourceSeries?.externalId.takeIf { seriesId == null },
                                    sourceSeriesName = sourceSeries?.title.takeIf { seriesId == null },
                                    sourceCoverUrl = sourceSeries?.coverUrl.takeIf { seriesId == null },
                                )
                            }),
                            incognito = incognito,
                            nav = nav,
                            // "Open in web" → this chapter in the Kodex web UI's source reader (on the server).
                            webUrl = sourceReadUrl(s.baseUrl, providerId, current.id, current.name, seriesId),
                        )
                    }
                    // The reader keeps its own page state, so a chapter swap has to remount it - but its
                    // prefs and detected mode belong to the series, so they are held out here.
                    key(current.id) { ImageReaderScreen(session, api, source, onBack, openSeries, seriesState) }
                }
            }
        }
    }
}

/**
 * Prev/next + the chapter menu, built from whichever chapter list [siblings] came from. Sorted
 * newest-first to match the tracked-catalogue convention, so reading order runs *up* the list.
 */
@Composable
private fun rememberChapterNav(
    server: ServerConnection,
    providerId: String,
    siblings: List<NavChapter>,
    currentId: String,
    open: (NavChapter, ReaderEdge) -> Unit,
    preload: (String) -> Unit,
): ReaderChapterNav? = remember(server.baseUrl, providerId, siblings, currentId) {
    if (siblings.size < 2) return@remember null
    val sorted = siblings.sortedByDescending { it.number ?: Double.NEGATIVE_INFINITY }
    val index = sorted.indexOfFirst { it.id == currentId }
    if (index < 0) return@remember null

    fun label(c: NavChapter): String = c.name.ifBlank { chapterLabel(c) }
    fun ref(c: NavChapter?): ReaderChapterRef? = c?.let {
        ReaderChapterRef(
            title = label(it),
            open = { edge -> open(it, edge) },
            preloadPageUrl = { pg -> sourcePageUrl(server.baseUrl, providerId, it.id, pg - 1) },
            preload = { preload(it.id) },
        )
    }
    ReaderChapterNav(
        prev = ref(sorted.getOrNull(index + 1)),
        next = ref(sorted.getOrNull(index - 1)),
        chapters = sorted.map { c ->
            ReaderChapterItem(
                title = label(c),
                active = c.id == currentId,
                read = c.read,
                progressPage = c.page?.takeIf { !c.read },
            ) { open(c, ReaderEdge.FIRST) }
        },
    )
}

private fun chapterLabel(c: NavChapter): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Book ${n.toInt()}" else "Book $n" } ?: "Book"

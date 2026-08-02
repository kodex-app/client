package app.kodex.client.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.KodexApi
import app.kodex.client.network.ReadProgressDto
import app.kodex.client.network.SourceChapter
import app.kodex.client.ui.catalog.sourcePageUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.main.SourceSeriesContext
import io.ktor.http.encodeURLQueryComponent

private sealed interface SourceReaderState {
    data object Loading : SourceReaderState
    data class Error(val message: String) : SourceReaderState
    data class Ready(val pageCount: Int, val progress: ReadProgressDto?) : SourceReaderState
}

/** Kodex web UI source-reader deep link for this chapter (mirrors the web's `/source-read` route). */
private fun sourceReadUrl(baseUrl: String, providerId: String, chapterId: String, chapterName: String?, seriesId: String?): String =
    buildString {
        append(baseUrl.trimEnd('/'))
        append("/source-read?provider=").append(providerId.encodeURLQueryComponent())
        append("&chapter=").append(chapterId.encodeURLQueryComponent())
        chapterName?.takeIf { it.isNotBlank() }?.let { append("&title=").append(it.encodeURLQueryComponent()) }
        seriesId?.let { append("&series=").append(it.encodeURLQueryComponent()) }
    }

/** The chapter currently open; [edge] is set when arriving from a sibling (start of it / end of it). */
private data class ChapterTarget(val id: String, val name: String?, val edge: ReaderEdge? = null)

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
    providerId: String,
    chapterId: String,
    seriesId: String?,
    chapterName: String?,
    onBack: () -> Unit,
    sourceSeries: SourceSeriesContext? = null,
    incognito: Boolean = false,
) {
    val server by session.activeServer.collectAsStateSafe()
    var target by remember(chapterId) { mutableStateOf(ChapterTarget(chapterId, chapterName)) }
    var state by remember(chapterId) { mutableStateOf<SourceReaderState>(SourceReaderState.Loading) }

    // Sibling chapters for cross-chapter navigation (Browse reads only — a followed series is read
    // through the series detail screen, which owns its tracked catalogue).
    var siblings by remember(sourceSeries?.externalId) { mutableStateOf<List<SourceChapter>>(emptyList()) }
    LaunchedEffect(sourceSeries?.externalId, server?.id) {
        val s = server ?: return@LaunchedEffect
        val ctx = sourceSeries ?: return@LaunchedEffect
        siblings = runCatching { api.sourceChapters(s.baseUrl, s.apiKey, ctx.providerId, ctx.externalId) }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(target.id, server?.id) {
        val s = server ?: return@LaunchedEffect
        if (sourceSeries?.isNovel == true) return@LaunchedEffect
        state = SourceReaderState.Loading
        state = runCatching {
            val pageCount = api.sourceChapterPageCount(s.baseUrl, s.apiKey, providerId, target.id)
            val progress = api.sourceProgress(s.baseUrl, s.apiKey, providerId, target.id)
            SourceReaderState.Ready(pageCount, progress)
        }.getOrElse { SourceReaderState.Error(it.friendlyMessage()) }
    }

    // Swap the open chapter. State drops back to Loading here (not just in the effect above) so the
    // reader never renders the new chapter's pages against the old one's page count.
    fun openChapter(chapter: SourceChapter, edge: ReaderEdge) {
        state = SourceReaderState.Loading
        target = ChapterTarget(chapter.externalId, chapter.name.takeIf { it.isNotBlank() }, edge)
    }

    val nav = server?.let { s -> rememberChapterNav(s, sourceSeries, siblings, target.id, ::openChapter) }

    when (val st = state) {
        is SourceReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is SourceReaderState.Loading ->
            if (sourceSeries?.isNovel == true) {
                ReaderShell(onBack) { ReaderMessage("Novel chapters aren't supported in the app yet.") }
            } else {
                ReaderShell(onBack) { Spinner() }
            }

        is SourceReaderState.Ready -> {
            val s = server
            when {
                s == null -> ReaderShell(onBack) { ReaderMessage("Not signed in.") }
                st.pageCount <= 0 -> ReaderShell(onBack) {
                    ReaderMessage("This chapter can't be streamed right now —\nthe source returned no pages.")
                }
                else -> {
                    val current = target
                    val source = remember(current, s.baseUrl, st, nav) {
                        ReaderSource(
                            title = current.name?.takeIf { it.isNotBlank() } ?: sourceSeries?.title ?: "Reading",
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
                    // The reader keeps its own page state, so a chapter swap has to remount it.
                    key(current.id) { ImageReaderScreen(session, api, source, onBack) }
                }
            }
        }
    }
}

/**
 * Prev/next + the chapter menu for a Browse read, built from the source's live chapter list. Sorted
 * newest-first to match the tracked-catalogue convention, so reading order runs *up* the list.
 */
@Composable
private fun rememberChapterNav(
    server: ServerConnection,
    sourceSeries: SourceSeriesContext?,
    siblings: List<SourceChapter>,
    currentId: String,
    open: (SourceChapter, ReaderEdge) -> Unit,
): ReaderChapterNav? = remember(server.baseUrl, sourceSeries, siblings, currentId) {
    if (sourceSeries == null || siblings.size < 2) return@remember null
    val sorted = siblings.sortedByDescending { it.number ?: Double.NEGATIVE_INFINITY }
    val index = sorted.indexOfFirst { it.externalId == currentId }
    if (index < 0) return@remember null

    fun label(c: SourceChapter): String = c.name.ifBlank { chapterLabel(c) }
    fun ref(c: SourceChapter?): ReaderChapterRef? = c?.let {
        ReaderChapterRef(
            title = label(it),
            open = { edge -> open(it, edge) },
            preloadPageUrl = { pg -> sourcePageUrl(server.baseUrl, sourceSeries.providerId, it.externalId, pg - 1) },
        )
    }
    ReaderChapterNav(
        prev = ref(sorted.getOrNull(index + 1)),
        next = ref(sorted.getOrNull(index - 1)),
        chapters = sorted.map { c ->
            ReaderChapterItem(label(c), active = c.externalId == currentId) { open(c, ReaderEdge.FIRST) }
        },
    )
}

private fun chapterLabel(c: SourceChapter): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

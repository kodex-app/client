package app.kodex.client.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.ReadProgressDto
import app.kodex.client.ui.catalog.sourcePageUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage

private sealed interface SourceReaderState {
    data object Loading : SourceReaderState
    data class Error(val message: String) : SourceReaderState
    data class Ready(val pageCount: Int, val progress: ReadProgressDto?) : SourceReaderState
}

/**
 * Streams a content-source chapter's pages directly — no download (the Mihon-style path). Feeds the
 * shared ImageReader once the page count + resume position are known.
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
    incognito: Boolean = false,
) {
    val server by session.activeServer.collectAsStateSafe()
    var state by remember(chapterId) { mutableStateOf<SourceReaderState>(SourceReaderState.Loading) }

    LaunchedEffect(chapterId, server?.id) {
        val s = server ?: return@LaunchedEffect
        state = SourceReaderState.Loading
        state = runCatching {
            val pageCount = api.sourceChapterPageCount(s.baseUrl, s.apiKey, providerId, chapterId)
            val progress = api.sourceProgress(s.baseUrl, s.apiKey, providerId, chapterId)
            SourceReaderState.Ready(pageCount, progress)
        }.getOrElse { SourceReaderState.Error(it.friendlyMessage()) }
    }

    when (val st = state) {
        is SourceReaderState.Loading -> ReaderShell(onBack) { Spinner() }
        is SourceReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is SourceReaderState.Ready -> {
            val s = server
            when {
                s == null -> ReaderShell(onBack) { ReaderMessage("Not signed in.") }
                st.pageCount <= 0 -> ReaderShell(onBack) {
                    ReaderMessage("This chapter can't be streamed right now —\nthe source returned no pages.")
                }
                else -> {
                    val source = remember(chapterId, s.baseUrl, st.pageCount) {
                        ReaderSource(
                            title = chapterName?.takeIf { it.isNotBlank() } ?: "Reading",
                            pageCount = st.pageCount,
                            initialPage = st.progress?.takeIf { !it.completed }?.page ?: 1,
                            kind = "comic",
                            seriesId = seriesId,
                            apiKey = s.apiKey,
                            // Source page images are 0-indexed; the reader speaks 1-based pages.
                            pageUrlFor = { pg -> sourcePageUrl(s.baseUrl, providerId, chapterId, pg - 1) },
                            onPersist = if (incognito) ({ _, _ -> }) else ({ pg, completed ->
                                api.saveSourceProgress(
                                    s.baseUrl, s.apiKey, providerId, chapterId,
                                    page = pg, completed = completed, seriesId = seriesId, chapterName = chapterName,
                                )
                            }),
                        )
                    }
                    ImageReaderScreen(session, api, source, onBack)
                }
            }
        }
    }
}

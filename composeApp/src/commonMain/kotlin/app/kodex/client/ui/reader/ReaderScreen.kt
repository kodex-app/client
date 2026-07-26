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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.catalog.bookPageUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState
    data class Ready(val book: BookDto) : ReaderState
}

/** Reader for a downloaded local book (comic/DIVINA + PDF). EPUB is gated with a message. */
@Composable
fun ReaderScreen(session: SessionManager, api: KodexApi, bookId: String, onBack: () -> Unit, startPage: Int? = null) {
    val server by session.activeServer.collectAsStateSafe()
    var state by remember(bookId) { mutableStateOf<ReaderState>(ReaderState.Loading) }

    LaunchedEffect(bookId, server?.id) {
        val s = server ?: return@LaunchedEffect
        state = ReaderState.Loading
        state = runCatching { api.book(s.baseUrl, s.apiKey, bookId) }
            .fold({ ReaderState.Ready(it) }, { ReaderState.Error(it.friendlyMessage()) })
    }

    when (val st = state) {
        is ReaderState.Loading -> ReaderShell(onBack) { Spinner() }
        is ReaderState.Error -> ReaderShell(onBack) { ReaderMessage(st.message) }
        is ReaderState.Ready -> {
            val s = server
            val book = st.book
            when {
                s == null -> ReaderShell(onBack) { ReaderMessage("Not signed in.") }
                isEpub(book) -> ReaderShell(onBack) { ReaderMessage("EPUB reading isn't supported in the app yet.") }
                book.pageCount <= 0 -> ReaderShell(onBack) { ReaderMessage("This book has no readable pages.") }
                else -> {
                    val source = remember(book.id, s.baseUrl, startPage) {
                        ReaderSource(
                            title = book.title.ifBlank { book.numberDisplay ?: "Reading" },
                            pageCount = book.pageCount,
                            initialPage = startPage?.coerceIn(1, book.pageCount) ?: book.readProgress?.page ?: 1,
                            kind = if (book.mediaType?.contains("pdf", ignoreCase = true) == true) "pdf" else "comic",
                            seriesId = book.seriesId,
                            apiKey = s.apiKey,
                            pageUrlFor = { pg -> bookPageUrl(s.baseUrl, book.id, pg) },
                            onPersist = { pg, completed -> api.saveReadProgress(s.baseUrl, s.apiKey, book.id, pg, completed) },
                        )
                    }
                    ImageReaderScreen(session, api, source, onBack)
                }
            }
        }
    }
}

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

private fun isEpub(book: BookDto): Boolean =
    book.mediaType?.contains("epub", ignoreCase = true) == true

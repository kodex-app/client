package app.kodex.client.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.catalog.bookPageUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState
    data class Ready(val book: BookDto) : ReaderState
}

/**
 * Full-screen paged image reader for comics (DIVINA) and PDF — one page per swipe, resuming from and
 * saving read progress. EPUB isn't an image format, so it's gated with a message (WebView reader TBD).
 */
@Composable
fun ReaderScreen(session: SessionManager, api: KodexApi, bookId: String, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    var state by remember(bookId) { mutableStateOf<ReaderState>(ReaderState.Loading) }

    LaunchedEffect(bookId, server?.id) {
        val s = server ?: return@LaunchedEffect
        state = ReaderState.Loading
        state = runCatching { api.book(s.baseUrl, s.apiKey, bookId) }
            .fold({ ReaderState.Ready(it) }, { ReaderState.Error(it.friendlyMessage()) })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val st = state) {
            is ReaderState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

            is ReaderState.Error ->
                Message(st.message)

            is ReaderState.Ready -> {
                val s = server
                val book = st.book
                when {
                    s == null -> Message("Not signed in.")
                    isEpub(book) -> Message("EPUB reading isn't supported in the app yet.")
                    book.pageCount <= 0 -> Message("This book has no readable pages.")
                    else -> PagerReader(s, api, book)
                }
            }
        }

        // Persistent back affordance (the in-reader chrome also has one, but this covers load/error/gates).
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
private fun PagerReader(server: ServerConnection, api: KodexApi, book: BookDto) {
    val pageCount = book.pageCount
    val start = ((book.readProgress?.page ?: 1) - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = start, pageCount = { pageCount })
    var chromeVisible by remember { mutableStateOf(true) }

    // Save progress whenever the pager settles on a new page.
    LaunchedEffect(pagerState, book.id) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { index ->
                val page = index + 1
                runCatching {
                    api.saveReadProgress(server.baseUrl, server.apiKey, book.id, page, page >= pageCount)
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            ReaderPage(
                url = bookPageUrl(server.baseUrl, book.id, index + 1),
                apiKey = server.apiKey,
                onTap = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(visible = chromeVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${pagerState.currentPage + 1} / $pageCount", color = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderPage(url: String, apiKey: String, onTap: () -> Unit) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", apiKey).build())
        .build()

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, textAlign = TextAlign.Center)
    }
}

private fun isEpub(book: BookDto): Boolean =
    book.mediaType?.contains("epub", ignoreCase = true) == true

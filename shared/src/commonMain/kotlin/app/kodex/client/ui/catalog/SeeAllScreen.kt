package app.kodex.client.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.collectAsStateSafe

/** What a "See all" screen shows — backs Home's per-section View-all. */
enum class SeeAllKind { KEEP_READING, RECENT_BOOKS, RECENT_SERIES, UPDATED_SERIES }

/** Full-screen cover grid for one Home rail's complete list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllScreen(
    session: SessionManager,
    api: KodexApi,
    kind: SeeAllKind,
    onBack: () -> Unit,
    onOpenSeries: (SeriesDto) -> Unit,
    onOpenBook: (BookDto) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val isBooks = kind == SeeAllKind.KEEP_READING || kind == SeeAllKind.RECENT_BOOKS
    val title = when (kind) {
        SeeAllKind.KEEP_READING -> "Continue reading"
        SeeAllKind.RECENT_BOOKS -> "Recently added"
        SeeAllKind.RECENT_SERIES -> "Recent series"
        SeeAllKind.UPDATED_SERIES -> "Recently updated"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = kind to server?.id,
                load = {
                    val s = server!!
                    when (kind) {
                        SeeAllKind.KEEP_READING -> api.keepReading(s.baseUrl, s.apiKey)
                        SeeAllKind.RECENT_BOOKS -> api.booksList(s.baseUrl, s.apiKey, "createdDate,desc")
                        SeeAllKind.RECENT_SERIES -> api.querySeries(s.baseUrl, s.apiKey, sort = "createdDate,desc", size = 300)
                        SeeAllKind.UPDATED_SERIES -> api.querySeries(s.baseUrl, s.apiKey, sort = "lastModifiedDate,desc", size = 300)
                    }
                },
            ) { list ->
                val s = server ?: return@LoadedContent
                if (list.isEmpty()) {
                    EmptyMessage("Nothing here yet.")
                } else LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (isBooks) {
                        @Suppress("UNCHECKED_CAST")
                        val books = list as List<BookDto>
                        items(books, key = { it.id }) { b ->
                            CoverCard(
                                coverUrl = bookCoverUrl(s.baseUrl, b.id),
                                apiKey = s.apiKey,
                                title = b.title.ifBlank { b.numberDisplay ?: "Book" },
                                subtitle = bookSubtitle(b),
                                unread = null,
                                onClick = { onOpenBook(b) },
                                width = null,
                            )
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val series = list as List<SeriesDto>
                        items(series, key = { it.id }) { sd ->
                            CoverCard(
                                coverUrl = seriesCoverUrl(s.baseUrl, sd),
                                apiKey = s.apiKey,
                                title = sd.title,
                                subtitle = seriesSubtitle(sd),
                                unread = seriesUnreadBadge(sd),
                                onClick = { onOpenSeries(sd) },
                                width = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

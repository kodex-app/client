package app.kodex.client.ui.series

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesDetailDto
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A series: cover + metadata header, then its books as a grid (all in one scroll). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    session: SessionManager,
    api: KodexApi,
    seriesId: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = seriesId to server?.id,
                load = {
                    val s = server!!
                    coroutineScope {
                        val detail = async { api.seriesDetail(s.baseUrl, s.apiKey, seriesId) }
                        val books = async { api.seriesBooks(s.baseUrl, s.apiKey, seriesId) }
                        detail.await() to books.await()
                    }
                },
            ) { (detail, books) ->
                val s = server ?: return@LoadedContent
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SeriesHeader(s.baseUrl, s.apiKey, detail)
                    }
                    if (books.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Books  ·  ${books.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(books, key = { it.id }) { book ->
                        CoverCard(
                            coverUrl = bookCoverUrl(s.baseUrl, book.id),
                            apiKey = s.apiKey,
                            title = bookLabel(book),
                            subtitle = book.numberDisplay,
                            unread = null,
                            onClick = { onOpenBook(book.id) },
                            width = null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesHeader(baseUrl: String, apiKey: String, detail: SeriesDetailDto) {
    Column {
        Row {
            Box(
                Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                CoverImage(
                    seriesCoverUrl(baseUrl, detail.id, detail.coverUrl),
                    apiKey,
                    Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    seriesCountLabel(detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (detail.unreadCount > 0) {
                    Text(
                        "${detail.unreadCount} unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (detail.publisher.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(detail.publisher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detail.language.isNotBlank()) {
                    Text(detail.language.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(detail.summary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun seriesCountLabel(d: SeriesDetailDto): String =
    if (d.totalChapters != null) {
        "${d.totalChapters} chapters · ${d.bookCount} downloaded"
    } else {
        "${d.bookCount} ${if (d.bookCount == 1) "book" else "books"}"
    }

private fun bookLabel(book: BookDto): String =
    book.title.ifBlank { book.numberDisplay ?: "Book" }

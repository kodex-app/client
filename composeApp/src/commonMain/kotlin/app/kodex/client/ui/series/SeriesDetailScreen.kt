package app.kodex.client.ui.series

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SeriesChapterDto
import app.kodex.client.network.SeriesDetailDto
import app.kodex.client.ui.main.OpenSourceReader
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.catalog.CoverCard
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.catalog.seriesCoverUrl
import app.kodex.client.ui.collectAsStateSafe

private data class SeriesContent(
    val detail: SeriesDetailDto,
    val books: List<BookDto>,
    val chapters: List<SeriesChapterDto>,
)

/** Resume affordance for the header's Read button. */
private data class Resume(val label: String, val open: () -> Unit)

/**
 * A series: cover + metadata header with a Read/Continue button, then its content — the books grid
 * for LOCAL series, or the source chapter list for WEB (followed) series.
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
                    val detail = api.seriesDetail(s.baseUrl, s.apiKey, seriesId)
                    if (detail.isWeb) {
                        SeriesContent(detail, emptyList(), api.seriesChapters(s.baseUrl, s.apiKey, seriesId))
                    } else {
                        SeriesContent(detail, api.seriesBooks(s.baseUrl, s.apiKey, seriesId), emptyList())
                    }
                },
            ) { content ->
                val s = server ?: return@LoadedContent
                if (content.detail.isWeb) {
                    ChaptersLayout(s.baseUrl, s.apiKey, content, onOpenReader, onOpenSourceReader)
                } else {
                    BooksLayout(s.baseUrl, s.apiKey, content, onOpenBook, onOpenReader)
                }
            }
        }
    }
}

@Composable
private fun BooksLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String) -> Unit,
) {
    val resume = localResume(content.books, onOpenReader)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SeriesHeader(
                    baseUrl, apiKey, content.detail,
                    countLabel = "${content.books.size} ${if (content.books.size == 1) "book" else "books"}",
                    unread = content.books.count { it.readProgress?.completed != true },
                )
                ReadButton(resume)
            }
        }
        if (content.books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Books · ${content.books.size}") }
            items(content.books, key = { it.id }) { book ->
                CoverCard(
                    coverUrl = bookCoverUrl(baseUrl, book.id),
                    apiKey = apiKey,
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

@Composable
private fun ChaptersLayout(
    baseUrl: String,
    apiKey: String,
    content: SeriesContent,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    val providerId = content.detail.sourceProviderId.orEmpty()
    val seriesId = content.detail.id
    // Reading order is ascending by number; display newest-first (matches the web default).
    val ascending = content.chapters.sortedWith(compareBy(nullsLast()) { it.number })
    val display = ascending.asReversed()
    val downloaded = ascending.count { it.downloaded }
    val resume = webResume(ascending, providerId, seriesId, onOpenReader, onOpenSourceReader)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            SeriesHeader(
                baseUrl, apiKey, content.detail,
                countLabel = "${ascending.size} chapters · $downloaded downloaded",
                unread = ascending.count { !it.read },
            )
            ReadButton(resume)
            Spacer(Modifier.height(20.dp))
            SectionLabel("Chapters · ${ascending.size}")
        }
        items(display, key = { it.chapterId }) { chapter ->
            ChapterRow(chapter, providerId, seriesId, onOpenReader, onOpenSourceReader)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: SeriesChapterDto,
    providerId: String,
    seriesId: String,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                val bookId = chapter.bookId
                if (bookId != null) onOpenReader(bookId)
                else onOpenSourceReader(providerId, chapter.chapterId, seriesId, chapter.name)
            }
            .alpha(if (chapter.read) 0.55f else 1f)
            .padding(vertical = 12.dp),
    ) {
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

@Composable
private fun ReadButton(resume: Resume?) {
    if (resume == null) return
    Spacer(Modifier.height(16.dp))
    Button(onClick = resume.open, modifier = Modifier.fillMaxWidth()) { Text(resume.label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesHeader(baseUrl: String, apiKey: String, detail: SeriesDetailDto, countLabel: String, unread: Int) {
    Column {
        Row {
            Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) {
                CoverImage(seriesCoverUrl(baseUrl, detail.id, detail.coverUrl), apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    countLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread > 0) {
                    Text("$unread unread", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** LOCAL: resume the in-progress book, else the first unread, else re-read from the top. */
private fun localResume(books: List<BookDto>, onOpenReader: (String) -> Unit): Resume? {
    if (books.isEmpty()) return null
    val inProgress = books.firstOrNull { it.readProgress?.completed == false }
    val firstUnread = books.firstOrNull { it.readProgress == null }
    val target = inProgress ?: firstUnread ?: books.first()
    val label = when {
        inProgress != null -> "Continue"
        firstUnread != null -> "Read"
        else -> "Read again"
    }
    return Resume(label) { onOpenReader(target.id) }
}

/** WEB: resume the first unread chapter — opens the offline book if downloaded, else streams it. */
private fun webResume(
    chapters: List<SeriesChapterDto>,
    providerId: String,
    seriesId: String,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
): Resume? {
    if (chapters.isEmpty()) return null
    val firstUnread = chapters.firstOrNull { !it.read }
    val target = firstUnread ?: chapters.first()
    val label = when {
        firstUnread == null -> "Read again"
        target.page != null -> "Continue"
        else -> "Read"
    }
    return Resume(label) {
        val bookId = target.bookId
        if (bookId != null) onOpenReader(bookId)
        else onOpenSourceReader(providerId, target.chapterId, seriesId, target.name)
    }
}

private fun bookLabel(book: BookDto): String = book.title.ifBlank { book.numberDisplay ?: "Book" }

private fun chapterNumberLabel(c: SeriesChapterDto): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

package app.kodex.client.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import kotlinx.coroutines.launch

/** A single book: cover + metadata + summary, with a functional mark read/unread toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    session: SessionManager,
    api: KodexApi,
    bookId: String,
    onBack: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book", fontWeight = FontWeight.SemiBold) },
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
                key = Triple(bookId, reload, server?.id),
                load = { val s = server!!; api.book(s.baseUrl, s.apiKey, bookId) },
            ) { book ->
                val s = server ?: return@LoadedContent
                BookDetailContent(
                    server = s,
                    book = book,
                    busy = busy,
                    onToggleRead = {
                        busy = true
                        scope.launch {
                            runCatching {
                                if (book.readProgress?.completed == true) {
                                    api.markBookUnread(s.baseUrl, s.apiKey, book.id)
                                } else {
                                    api.markBookRead(s.baseUrl, s.apiKey, book)
                                }
                            }
                            busy = false
                            reload++
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailContent(
    server: ServerConnection,
    book: BookDto,
    busy: Boolean,
    onToggleRead: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row {
            Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) {
                CoverImage(bookCoverUrl(server.baseUrl, book.id), server.apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title.ifBlank { book.numberDisplay ?: "Book" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                book.numberDisplay?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(readStatus(book), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onToggleRead, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (book.readProgress?.completed == true) "Mark as unread" else "Mark as read")
            }
        }

        val authorLine = book.authors.filter { it.name.isNotBlank() }.joinToString(", ") { it.name }
        MetaSection("Authors", authorLine)
        MetaSection("Released", book.releaseDate)
        MetaSection("Pages", if (book.pageCount > 0) book.pageCount.toString() else null)
        MetaSection("Format", book.mediaType)
        MetaSection("Size", humanSize(book.fileSize))

        if (book.tags.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                book.tags.take(20).forEach { MetaChip(it) }
            }
        }

        if (book.summary.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(book.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MetaSection(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun readStatus(book: BookDto): String = when {
    book.readProgress?.completed == true -> "Completed"
    book.readProgress != null -> "In progress · page ${book.readProgress.page}"
    else -> "Not started"
}

private fun humanSize(bytes: Long): String? {
    if (bytes <= 0) return null
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[unit]}"
}

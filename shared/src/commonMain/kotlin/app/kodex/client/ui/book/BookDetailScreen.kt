package app.kodex.client.ui.book

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.network.AuthorDto
import app.kodex.client.network.BookDto
import app.kodex.client.network.KodexApi
import app.kodex.client.network.UpdateBookMetadataRequest
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.bookCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.nav.retain
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import app.kodex.client.ui.sheetMinHeight
import kotlinx.coroutines.launch

/** A single book: cover + metadata + summary, mark read/unread, and edit / re-analyze / delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    session: SessionManager,
    api: KodexApi,
    bookId: String,
    onBack: () -> Unit,
    onRead: (String) -> Unit = {},
    onOpenReaderAt: (bookId: String, page: Int) -> Unit = { _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    // Retained: opening the reader unmounts this screen, and the book would otherwise be re-fetched
    // from a spinner on the way back. Held state means the refresh happens underneath what's shown.
    var book by retain("book") { mutableStateOf<BookDto?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, server?.id, reloadTick) {
        val s = server ?: return@LaunchedEffect
        errorMsg = null
        runCatching { api.book(s.baseUrl, s.apiKey, bookId) }
            .fold({ book = it }, { errorMsg = it.friendlyMessage() })
    }

    fun action(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { snackbar?.show(message); reloadTick++ },
                onFailure = { snackbar?.show("Action failed. Please try again.") },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Book actions") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Edit metadata") }, onClick = { menuOpen = false; editOpen = true })
                            DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { menuOpen = false; bookmarksOpen = true })
                            DropdownMenuItem(text = { Text("Re-analyze") }, onClick = {
                                menuOpen = false
                                val s = server ?: return@DropdownMenuItem
                                action("Re-analyzing…") { api.analyzeBook(s.baseUrl, s.apiKey, bookId) }
                            })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val s = server
            val current = book
            when {
                errorMsg != null && current == null ->
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { reloadTick++ }, Modifier.padding(top = 16.dp)) { Text("Retry") }
                        }
                    }
                current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s != null -> BookDetailContent(
                    server = s,
                    book = current,
                    busy = busy,
                    onRead = { onRead(current.id) },
                    onToggleRead = {
                        busy = true
                        scope.launch {
                            runCatching {
                                if (current.readProgress?.completed == true) api.markBookUnread(s.baseUrl, s.apiKey, current.id)
                                else api.markBookRead(s.baseUrl, s.apiKey, current)
                            }
                            busy = false
                            reloadTick++
                        }
                    },
                )
            }
        }
    }

    val current = book
    val s = server
    if (editOpen && current != null && s != null) {
        EditMetadataSheet(
            book = current,
            onDismiss = { editOpen = false },
            onSave = { patch ->
                editOpen = false
                action("Metadata updated") { api.updateBookMetadata(s.baseUrl, s.apiKey, bookId, patch) }
            },
        )
    }

    if (bookmarksOpen && s != null) {
        app.kodex.client.ui.bookmark.BookmarksSheet(
            onDismiss = { bookmarksOpen = false },
            load = {
                api.bookBookmarks(s.baseUrl, s.apiKey, bookId).map { bm ->
                    app.kodex.client.ui.bookmark.BookmarkRow(
                        id = bm.id,
                        title = bm.label?.takeIf { it.isNotBlank() } ?: (bm.page?.let { "Page $it" } ?: "Bookmark"),
                        subtitle = bm.page?.let { "Page $it" }.takeIf { bm.label != null && bm.label.isNotBlank() },
                        onOpen = { bookmarksOpen = false; bm.page?.let { onOpenReaderAt(bookId, it) } ?: onRead(bookId) },
                        onDelete = { api.deleteBookmark(s.baseUrl, s.apiKey, bookId, bm.id) },
                    )
                }
            },
        )
    }

    if (confirmDelete && s != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete book?") },
            text = { Text("This removes the book and its file from the server. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.deleteBook(s.baseUrl, s.apiKey, bookId) }.fold(
                            onSuccess = { snackbar?.show("Book deleted"); onBack() },
                            onFailure = { snackbar?.show("Couldn't delete (need manage permission).") },
                        )
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Book fields that can be pinned against metadata providers; keys are the server's field names. */
private val BOOK_LOCKABLE = listOf(
    "title" to "Title",
    "number" to "Number",
    "summary" to "Summary",
    "tags" to "Tags",
    "authors" to "Authors",
    "releaseDate" to "Release date",
    "isbn" to "ISBN",
)

@Composable
private fun EditSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMetadataSheet(
    book: BookDto,
    onDismiss: () -> Unit,
    onSave: (UpdateBookMetadataRequest) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(book.title) }
    var number by remember { mutableStateOf(book.numberDisplay ?: "") }
    var summary by remember { mutableStateOf(book.summary) }
    var tags by remember { mutableStateOf(book.tags.joinToString(", ")) }
    // Authors are an ordered list of name+role pairs, so they get rows rather than a comma field.
    val authors = remember { book.authors.toMutableStateList() }
    var locked by remember { mutableStateOf(book.lockedFields) }

    ModalBottomSheet(modifier = Modifier.heightIn(min = sheetMinHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Text("Edit metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(number, { number = it }, label = { Text("Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            EditSectionTitle("Authors")
            authors.forEachIndexed { i, author ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = author.name,
                        onValueChange = { authors[i] = author.copy(name = it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = author.role,
                        onValueChange = { authors[i] = author.copy(role = it) },
                        label = { Text("Role") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { authors.removeAt(i) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove author")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            TextButton(onClick = { authors.add(AuthorDto(name = "", role = "writer")) }) { Text("Add author") }

            Spacer(Modifier.height(12.dp))
            EditSectionTitle("Locked fields")
            Text(
                "A locked field is left alone when metadata is refreshed from a provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BOOK_LOCKABLE.forEach { (key, label) ->
                    FilterChip(
                        selected = key in locked,
                        onClick = { locked = if (key in locked) locked - key else locked + key },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            UpdateBookMetadataRequest(
                                title = title,
                                number = number.ifBlank { null },
                                summary = summary,
                                tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                // Blank rows are the ones the user added and left empty; drop them.
                                authors = authors.filter { it.name.isNotBlank() },
                                lockedFields = locked.toList(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailContent(
    server: ServerConnection,
    book: BookDto,
    busy: Boolean,
    onRead: () -> Unit,
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
        Button(onClick = onRead, enabled = book.isReady, modifier = Modifier.fillMaxWidth()) {
            Text(readActionLabel(book))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onToggleRead, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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

        val ids = buildMap {
            book.isbn?.takeIf { it.isNotBlank() }?.let { put("ISBN", it) }
            book.identifiers.forEach { (k, v) -> if (k != "isbn" && v.isNotBlank()) put(k.uppercase(), v) }
        }
        if (ids.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Identifiers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ids.forEach { (k, v) -> MetaSection(k, v) }
        }

        if (book.externalLinks.isNotEmpty()) {
            val openUrl = app.kodex.client.platform.rememberUrlOpener()
            Spacer(Modifier.height(16.dp))
            Text("Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            book.externalLinks.forEach { link ->
                Text(
                    link.label.ifBlank { link.url },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { openUrl(link.url) }
                        .padding(vertical = 8.dp),
                )
            }
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

private fun readActionLabel(book: BookDto): String = when {
    book.readProgress?.completed == true -> "Read again"
    book.readProgress != null -> "Continue"
    else -> "Read"
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

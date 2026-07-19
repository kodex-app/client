package app.kodex.client.ui.browse

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.kodex.client.network.FollowedSeriesRef
import app.kodex.client.network.KodexApi
import app.kodex.client.network.SourceChapter
import app.kodex.client.network.SourceDescriptor
import app.kodex.client.network.SourceSearchResult
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.MetaChip
import app.kodex.client.ui.catalog.CoverImage
import app.kodex.client.ui.catalog.sourceCoverUrl
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class SourceSeriesData(
    val info: SourceSearchResult,
    val chapters: List<SourceChapter>,
    val followed: FollowedSeriesRef?,
)

/**
 * A content source's series page (Browse drill-down): source metadata + chapter list, with follow
 * (add to the WEB library), download-all, and unfollow actions. Reading a chapter awaits the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSeriesScreen(
    session: SessionManager,
    api: KodexApi,
    source: SourceDescriptor,
    seed: SourceSearchResult,
    onBack: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun act(block: suspend (ServerConnection) -> String) {
        val s = server ?: return
        busy = true
        message = null
        scope.launch {
            message = runCatching { block(s) }.fold({ it }, { it.friendlyMessage() })
            busy = false
            reload++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seed.title.ifBlank { "Series" }, fontWeight = FontWeight.SemiBold) },
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
                key = listOf(source.id, seed.externalId, reload, server?.id),
                load = {
                    val s = server!!
                    coroutineScope {
                        val info = async { api.sourceSeries(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        val chapters = async { api.sourceChapters(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        val followed = async { api.followedSeriesRef(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        SourceSeriesData(info.await(), chapters.await(), followed.await())
                    }
                },
            ) { data ->
                val s = server ?: return@LoadedContent
                val followed = data.followed

                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    item {
                        Header(s, source.id, data.info)
                        Spacer(Modifier.height(16.dp))
                        Actions(
                            followed = followed,
                            busy = busy,
                            message = message,
                            onFollow = {
                                act { srv ->
                                    val lib = api.webLibrary(srv.baseUrl, srv.apiKey)
                                    api.followWebSeries(srv.baseUrl, srv.apiKey, lib.id, source.id, seed.externalId)
                                    "Added to your library"
                                }
                            },
                            onDownload = {
                                act { srv ->
                                    api.downloadWebSeries(srv.baseUrl, srv.apiKey, followed!!.libraryId, followed.seriesId, null)
                                    "Download queued"
                                }
                            },
                            onUnfollow = {
                                act { srv ->
                                    api.unfollowWebSeries(srv.baseUrl, srv.apiKey, followed!!.libraryId, followed.seriesId, deleteFiles = false)
                                    "Removed from your library"
                                }
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Chapters  ·  ${data.chapters.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(data.chapters, key = { it.externalId }) { chapter ->
                        ChapterRow(chapter)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(server: ServerConnection, providerId: String, info: SourceSearchResult) {
    Column {
        Row {
            Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) {
                CoverImage(sourceCoverUrl(server.baseUrl, providerId, info.coverUrl), server.apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val by = listOfNotNull(info.author, info.artist).filter { it.isNotBlank() }.distinct().joinToString(", ")
                if (by.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(prettyStatus(info.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (info.genres.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info.genres.take(15).forEach { MetaChip(it) }
            }
        }
        info.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Actions(
    followed: FollowedSeriesRef?,
    busy: Boolean,
    message: String?,
    onFollow: () -> Unit,
    onDownload: () -> Unit,
    onUnfollow: () -> Unit,
) {
    if (followed == null) {
        Button(onClick = onFollow, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) BtnSpinner() else Text("Add to library")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDownload, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) BtnSpinner() else Text("Download all")
            }
            OutlinedButton(onClick = onUnfollow, enabled = !busy) { Text("Unfollow") }
        }
    }
    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChapterRow(chapter: SourceChapter) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(chapter.name.ifBlank { chapterNumber(chapter) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        val meta = listOfNotNull(
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
private fun BtnSpinner() =
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)

private fun chapterNumber(c: SourceChapter): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

private fun prettyStatus(status: String): String =
    status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

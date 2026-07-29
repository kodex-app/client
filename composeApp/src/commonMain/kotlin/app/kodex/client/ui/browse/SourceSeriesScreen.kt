@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.kodex.client.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * (add to the WEB library), download-all, and unfollow actions. Styled to match the library series
 * detail — collapsing transparent toolbar + blurred backdrop, full-bleed chapter rows, sort/refresh.
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
    var sortDesc by remember { mutableStateOf(true) }
    var sortByDate by remember { mutableStateOf(false) }
    var translator by remember { mutableStateOf<String?>(null) }

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

    // Collapsing toolbar: the title fades in and the bar turns opaque once the header scrolls past it.
    val listState = rememberLazyListState()
    val titleVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 280 }
    }
    val barColor by animateColorAsState(
        if (titleVisible) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "sourceBarColor",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor,
                    scrolledContainerColor = barColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    AnimatedVisibility(titleVisible, enter = fadeIn(), exit = fadeOut()) {
                        Text(seed.title.ifBlank { "Series" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val topInset = padding.calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomInset = maxOf(padding.calculateBottomPadding(), navBottom)
        Box(Modifier.fillMaxSize()) {
            server?.let { srv -> SourceBackdrop(srv.baseUrl, srv.apiKey, source.id, seed.coverUrl, topInset + 210.dp) }
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
                val scanlators = data.chapters.mapNotNull { it.scanlator?.takeIf { sc -> sc.isNotBlank() } }.distinct().sorted()
                val visible = if (translator == null) data.chapters else data.chapters.filter { it.scanlator == translator }
                val ascending = if (sortByDate) visible.sortedWith(compareBy(nullsLast()) { it.releaseDate })
                else visible.sortedWith(compareBy(nullsLast()) { it.number })
                val display = if (sortDesc) ascending.asReversed() else ascending

                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 24.dp + bottomInset),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
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
                            ChapterControls(
                                total = visible.size,
                                sortByDate = sortByDate, sortDesc = sortDesc,
                                onToggleDir = { sortDesc = !sortDesc }, onSetSortByDate = { sortByDate = it },
                                scanlators = scanlators, translator = translator, onSetTranslator = { translator = it },
                                onRefresh = { reload++ },
                            )
                        }
                    }
                    items(display, key = { it.externalId }) { chapter ->
                        ChapterRow(chapter)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

/** Blurred cover backdrop from the top (behind the toolbar) fading into the page background. */
@Composable
private fun SourceBackdrop(baseUrl: String, apiKey: String, providerId: String, coverUrl: String?, height: Dp) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(height)) {
        CoverImage(sourceCoverUrl(baseUrl, providerId, coverUrl), apiKey, Modifier.fillMaxSize().blur(20.dp).alpha(0.55f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.30f),
                    0.65f to surface.copy(alpha = 0.75f),
                    1f to surface,
                ),
            ),
        )
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
            ExpandableSummary(it)
        }
    }
}

/** Series summary capped at 3 lines with a "Read more" toggle when it overflows; tapping toggles too. */
@Composable
private fun ExpandableSummary(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = overflows || expanded) { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
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

/** Sort (chapter/date + direction), translator filter, and refresh controls above the chapter list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterControls(
    total: Int,
    sortByDate: Boolean,
    sortDesc: Boolean,
    onToggleDir: () -> Unit,
    onSetSortByDate: (Boolean) -> Unit,
    scanlators: List<String>,
    translator: String?,
    onSetTranslator: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var transMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Chapters · $total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            ControlChip(if (sortByDate) "Release date" else "Chapter number") { sortMenu = true }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMenuItem("Chapter number", selected = !sortByDate, sortDesc = sortDesc) { if (sortByDate) onSetSortByDate(false) else onToggleDir() }
                SortMenuItem("Release date", selected = sortByDate, sortDesc = sortDesc) { if (!sortByDate) onSetSortByDate(true) else onToggleDir() }
            }
        }
        if (scanlators.size > 1) {
            Spacer(Modifier.width(8.dp))
            Box {
                ControlChip(translator ?: "All translators") { transMenu = true }
                DropdownMenu(expanded = transMenu, onDismissRequest = { transMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All translators") },
                        onClick = { transMenu = false; onSetTranslator(null) },
                        leadingIcon = { if (translator == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                    scanlators.forEach { sc ->
                        DropdownMenuItem(
                            text = { Text(sc) },
                            onClick = { transMenu = false; onSetTranslator(sc) },
                            leadingIcon = { if (translator == sc) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuItem(label: String, selected: Boolean, sortDesc: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (selected) Icon(
                if (sortDesc) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (sortDesc) "Descending" else "Ascending",
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun ChapterRow(chapter: SourceChapter) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(chapter.name.ifBlank { chapterNumber(chapter) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        val meta = listOfNotNull(
            chapter.number?.let { n -> "#" + (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) },
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

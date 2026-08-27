@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.icedtea.kodex.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.model.ServerConnection
import dev.icedtea.kodex.network.FollowedSeriesRef
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.ReadProgressDto
import dev.icedtea.kodex.network.SourceChapter
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.SourceSearchResult
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.catalog.SeriesBackdrop
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.catalog.SeriesDetailList
import dev.icedtea.kodex.ui.catalog.SeriesEntryRow
import dev.icedtea.kodex.ui.catalog.SeriesHeader
import dev.icedtea.kodex.ui.catalog.SeriesListControls
import dev.icedtea.kodex.ui.catalog.SeriesSort
import dev.icedtea.kodex.ui.catalog.sourceCoverUrl
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.main.OpenBrowseReader
import dev.icedtea.kodex.ui.main.SourceSeriesContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class SourceSeriesData(
    val info: SourceSearchResult,
    val chapters: List<SourceChapter>,
    val followed: FollowedSeriesRef?,
    /** Saved progress per chapter external id — read marks + the resume action, without following. */
    val progress: Map<String, ReadProgressDto>,
)

/**
 * A content source's series page (Browse drill-down): source metadata + chapter list, with read-from-
 * source per chapter (no library needed), follow (add to the WEB library), download-all, and unfollow
 * actions. Styled to match the library series detail — collapsing transparent toolbar + blurred
 * backdrop, full-bleed chapter rows, sort/refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSeriesScreen(
    session: SessionManager,
    api: KodexApi,
    source: SourceDescriptor,
    seed: SourceSearchResult,
    onBack: () -> Unit,
    onOpenReader: OpenBrowseReader = { _, _, _ -> },
    onOpenReaderIncognito: OpenBrowseReader = { _, _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Retained: opening a chapter in the reader unmounts this screen, and losing the sort/filter and
    // scroll position here meant coming back landed at the top of a freshly loaded list.
    val st = retain("sourceSeries") { SourceSeriesState() }
    var sortDesc by st.sortDesc
    var sortKey by st.sortKey
    var translator by st.translator
    // The read actions live in the Scaffold's FAB slot, but only the loaded chapter list knows which
    // chapter to resume — so the content publishes it up here (and clears it while reloading).
    var readFab by remember { mutableStateOf<ReadFab?>(null) }

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
    val listState = st.list
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
        floatingActionButton = {
            readFab?.let { fab ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = fab.openIncognito) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = "Read incognito")
                    }
                    ExtendedFloatingActionButton(
                        onClick = fab.open,
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        text = {
                            Column {
                                Text(fab.label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    fab.target,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalContentColor.current.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val topInset = padding.calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomInset = maxOf(padding.calculateBottomPadding(), navBottom)
        Box(Modifier.fillMaxSize()) {
            server?.let { srv -> SourceBackdrop(srv.baseUrl, srv.apiKey, source.id, seed.coverUrl, topInset + 210.dp) }
            LoadedContent(
                retainKey = "chapters",
                key = listOf(source.id, seed.externalId, reload, server?.id),
                load = {
                    val s = server!!
                    coroutineScope {
                        val info = async { api.sourceSeries(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        val chapters = async { api.sourceChapters(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        val followed = async { api.followedSeriesRef(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                        val progress = async {
                            runCatching { api.sourceSeriesProgress(s.baseUrl, s.apiKey, source.id, seed.externalId) }
                                .getOrDefault(emptyMap())
                        }
                        SourceSeriesData(info.await(), chapters.await(), followed.await(), progress.await())
                    }
                },
            ) { data ->
                val s = server ?: return@LoadedContent
                val followed = data.followed
                val scanlators = data.chapters.mapNotNull { it.scanlator?.takeIf { sc -> sc.isNotBlank() } }.distinct().sorted()
                val visible = if (translator == null) data.chapters else data.chapters.filter { it.scanlator == translator }
                // SOURCE is the order the source itself listed them in, left untouched.
                val ascending = when (sortKey) {
                    SeriesSort.DATE -> visible.sortedWith(compareBy(nullsLast()) { it.releaseDate })
                    SeriesSort.NUMBER -> visible.sortedWith(compareBy(nullsLast()) { it.number })
                    SeriesSort.SOURCE -> visible
                }
                val display = if (sortDesc) ascending.asReversed() else ascending
                // Identity of this source series, carried into the reader (nav + progress attribution).
                val context = SourceSeriesContext(
                    providerId = source.id,
                    externalId = seed.externalId,
                    title = data.info.title.ifBlank { seed.title },
                    coverUrl = data.info.coverUrl ?: seed.coverUrl,
                    isNovel = source.kind == KIND_BOOK,
                )
                val resume = resumeChapter(data.chapters, data.progress)

                val fab = remember(resume, context) {
                    resume?.let { r ->
                        ReadFab(
                            label = r.label,
                            target = r.target,
                            open = { onOpenReader(context, r.chapter.externalId, r.chapter.name) },
                            openIncognito = { onOpenReaderIncognito(context, r.chapter.externalId, r.chapter.name) },
                        )
                    }
                }
                LaunchedEffect(fab) { readFab = fab }
                DisposableEffect(Unit) { onDispose { readFab = null } }

                SeriesDetailList(listState, topInset, bottomInset, header = {
                    Header(s, source.id, data.info, seed)
                    Spacer(Modifier.height(16.dp))
                    Actions(
                        followed = followed,
                        busy = busy,
                        message = message,
                        onFollow = {
                            act { srv ->
                                // A WEB library holds one media kind, so the target has to match this
                                // source's — the implicit /libraries/web shelf is always COMIC.
                                val lib = api.webLibraryTargets(srv.baseUrl, srv.apiKey, source.mediaKind).firstOrNull()
                                    ?: error("No ${mediaKindLabel(source.mediaKind)} library to add to.")
                                api.followWebSeries(srv.baseUrl, srv.apiKey, lib.id, source.id, seed.externalId)
                                "Added to ${lib.name}"
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
                    SeriesListControls(
                        countLabel = "Books · ${visible.size}",
                        numberLabel = "Book number",
                        sortKey = sortKey, sortDesc = sortDesc,
                        onToggleDir = { sortDesc = !sortDesc }, onSetSortKey = { sortKey = it },
                        scanlators = scanlators, translator = translator, onSetTranslator = { translator = it },
                        onRefresh = { reload++ },
                    )
                }) {
                    if (display.isEmpty()) {
                        item {
                            Text(
                                "This source listed no chapters for this series.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    }
                    // Sources that report volumes (e.g. MangaDex) get a header per volume; the rest fall
                    // out as one unlabelled group = the flat list.
                    display.groupBy { it.volume }.forEach { (volume, chapters) ->
                        if (volume != null) {
                            item(key = "volume:$volume") { VolumeHeader(volume) }
                        }
                        items(chapters, key = { it.externalId }) { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                progress = data.progress[chapter.externalId],
                                onClick = { onOpenReader(context, chapter.externalId, chapter.name) },
                                onLongClick = { onOpenReaderIncognito(context, chapter.externalId, chapter.name) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Blurred cover backdrop from the top (behind the toolbar) fading into the page background. */
@Composable
private fun SourceBackdrop(baseUrl: String, apiKey: String, providerId: String, coverUrl: String?, height: Dp) {
    SeriesBackdrop(sourceCoverUrl(baseUrl, providerId, coverUrl), apiKey, height)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    server: ServerConnection,
    providerId: String,
    info: SourceSearchResult,
    /**
     * The row this screen was opened from. Several sources fill in author/artist on their listing
     * pages but return null for them from `seriesDetails`, which made the credit visible on the card
     * and then vanish on open — so the listing's values stand in when the detail omits them.
     */
    seed: SourceSearchResult,
) {
    SeriesHeader(
        coverUrl = sourceCoverUrl(server.baseUrl, providerId, info.coverUrl),
        apiKey = server.apiKey,
        title = info.title,
        chips = info.genres,
        summary = info.description,
    ) {
        val author = info.author?.takeIf { it.isNotBlank() } ?: seed.author
        val artist = info.artist?.takeIf { it.isNotBlank() } ?: seed.artist
        val by = listOfNotNull(author, artist).filter { it.isNotBlank() }.distinct().joinToString(", ")
        if (by.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(prettyStatus(info.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}


/**
 * The library actions (follow / download / unfollow). Reading is handled by the FABs — see [ReadFab].
 */
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
        OutlinedButton(onClick = onFollow, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) BtnSpinner() else Text("Add to library")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDownload, enabled = !busy, modifier = Modifier.weight(1f)) {
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




/** The source's volume/section label, separating grouped chapters. */
@Composable
private fun VolumeHeader(volume: String) {
    Text(
        volume,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * A streamable chapter: tap reads it straight from the source, long-press reads it incognito. Read
 * chapters are dimmed (with the unread dot cleared) and an in-progress one shows where it left off.
 */
@Composable
private fun ChapterRow(
    chapter: SourceChapter,
    progress: ReadProgressDto?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val read = progress?.completed == true
    SeriesEntryRow(
        title = chapter.name.ifBlank { chapterNumber(chapter) },
        meta = listOfNotNull(
            chapter.number?.let { n -> "#" + (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) },
            chapter.scanlator?.takeIf { it.isNotBlank() },
            chapter.releaseDate?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = onClick,
        onLongClick = onLongClick,
        dimmed = read,
        leading = {
            // Unread dot; a read chapter keeps the space so titles stay aligned down the list.
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (read) Color.Transparent else MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
        },
        trailing = progress?.takeIf { !it.completed }?.let { p ->
            {
                Text(
                    "Page ${p.page}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun BtnSpinner() =
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)

/** The chapter the read FAB opens, how to label the action, and how to name the chapter. */
private data class Resume(val chapter: SourceChapter, val label: String, val target: String)

/** [Resume] bound to its callbacks, lifted out of the loaded content so the Scaffold can host it. */
private data class ReadFab(
    val label: String,
    /** Which chapter it opens, and where in it — see the series-detail FAB for why it's named. */
    val target: String,
    val open: () -> Unit,
    val openIncognito: () -> Unit,
)

/**
 * Which chapter to continue or start with, in the web's order: the lowest-numbered chapter left
 * in progress, else the lowest never finished, else (everything read) the first one again.
 */
private fun resumeChapter(chapters: List<SourceChapter>, progress: Map<String, ReadProgressDto>): Resume? {
    if (chapters.isEmpty()) return null
    val byNumber = chapters.sortedWith(compareBy(nullsLast()) { it.number })
    // Its own title if it has one, else its number; plus the page, but only when resuming part-way.
    fun name(c: SourceChapter, withPage: Boolean): String {
        val label = c.name.takeIf { it.isNotBlank() } ?: chapterNumber(c)
        val page = progress[c.externalId]?.page?.takeIf { withPage && it > 0 } ?: return label
        return "$label · page $page"
    }
    byNumber.firstOrNull { progress[it.externalId]?.completed == false }
        ?.let { return Resume(it, "Continue", name(it, withPage = true)) }
    byNumber.firstOrNull { progress[it.externalId]?.completed != true }
        ?.let { return Resume(it, "Start reading", name(it, withPage = false)) }
    return Resume(byNumber.first(), "Read again", name(byNumber.first(), withPage = false))
}

private fun chapterNumber(c: SourceChapter): String =
    c.number?.let { n -> if (n % 1.0 == 0.0) "Chapter ${n.toInt()}" else "Chapter $n" } ?: "Chapter"

private fun prettyStatus(status: String): String =
    status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** [SourceDescriptor.kind] of a novel source — its chapters are text, not page images. */
private const val KIND_BOOK = "BOOK"

/** Sort, filter and scroll position that must survive the reader being opened on top of this screen. */
private class SourceSeriesState {
    val sortDesc = mutableStateOf(true)
    val sortKey = mutableStateOf(SeriesSort.NUMBER)
    val translator = mutableStateOf<String?>(null)
    val list = LazyListState()
}

package dev.icedtea.kodex.ui.catalog

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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.BookDto
import dev.icedtea.kodex.network.KeepReadingDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.SeriesDto
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.LoadedContent
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.main.OpenBrowseReader
import dev.icedtea.kodex.ui.main.OpenSourceReader

/** What a "See all" screen shows — backs Home's per-section View-all. */
enum class SeeAllKind { KEEP_READING, RECENT_BOOKS, RECENT_SERIES, UPDATED_SERIES }

/**
 * Full-screen cover grid for one Home rail's complete list.
 *
 * Each rail expands through the home resource's own row endpoint under `/v1/home`, so it is scoped by the
 * server exactly like the rail it came from — the expanded list can't show libraries or sources the rail
 * hid, and no filtering happens here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllScreen(
    session: SessionManager,
    api: KodexApi,
    kind: SeeAllKind,
    onBack: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    /** Long-press: the book's details, since a tap now reads it. */
    onShowBookDetails: (String) -> Unit = {},
    onOpenSourceReader: OpenSourceReader = { _, _, _, _ -> },
    onOpenBrowseReader: OpenBrowseReader = { _, _, _ -> },
) {
    val server by session.activeServer.collectAsStateSafe()
    val sourceNames = rememberSourceNames(session, api)
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
                retainKey = "items",
                key = kind to server?.id,
                load = {
                    val s = server!!
                    when (kind) {
                        SeeAllKind.KEEP_READING -> api.homeKeepReading(s.baseUrl, s.apiKey)
                        SeeAllKind.RECENT_BOOKS -> api.homeBooks(s.baseUrl, s.apiKey)
                        SeeAllKind.RECENT_SERIES -> api.homeSeries(s.baseUrl, s.apiKey, row = "RECENT")
                        SeeAllKind.UPDATED_SERIES -> api.homeSeries(s.baseUrl, s.apiKey, row = "UPDATED")
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
                    when (kind) {
                        SeeAllKind.KEEP_READING -> {
                            @Suppress("UNCHECKED_CAST")
                            val entries = list as List<KeepReadingDto>
                            items(entries, key = { "${it.kind}-${it.bookId ?: it.chapterId}" }) { k ->
                                CoverCard(
                                    coverUrl = keepReadingCover(s.baseUrl, k),
                                    apiKey = s.apiKey,
                                    title = k.seriesName.ifBlank { k.title.orEmpty() },
                                    subtitle = k.title,
                                    unread = null,
                                    onClick = { openKeepReading(k, onOpenBook, onOpenSeries, onOpenSourceReader, onOpenBrowseReader) },
                                    width = null,
                                    onLongClick = k.bookId.takeIf { k.isBook }?.let { id -> { onShowBookDetails(id) } },
                                )
                            }
                        }

                        SeeAllKind.RECENT_BOOKS -> {
                            @Suppress("UNCHECKED_CAST")
                            val books = list as List<BookDto>
                            items(books, key = { it.id }) { b ->
                                CoverCard(
                                    coverUrl = bookCoverUrl(s.baseUrl, b.id),
                                    apiKey = s.apiKey,
                                    title = b.title.ifBlank { b.numberDisplay ?: "Book" },
                                    subtitle = bookSubtitle(b),
                                    unread = null,
                                    onClick = { onOpenBook(b.id) },
                                    width = null,
                                    onLongClick = { onShowBookDetails(b.id) },
                                )
                            }
                        }

                        SeeAllKind.RECENT_SERIES, SeeAllKind.UPDATED_SERIES -> {
                            @Suppress("UNCHECKED_CAST")
                            val series = list as List<SeriesDto>
                            items(series, key = { it.id }) { sd ->
                                CoverCard(
                                    coverUrl = seriesCoverUrl(s.baseUrl, sd),
                                    apiKey = s.apiKey,
                                    title = sd.title,
                                    subtitle = seriesSubtitle(sd),
                                    unread = seriesUnreadBadge(sd),
                                    onClick = { onOpenSeries(sd.id) },
                                    width = null,
                                    source = sourceLabel(sd, sourceNames),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package app.kodex.client.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade

private val CardWidth = 132.dp

/** A cover image fetched from Kodex with the per-server `X-API-Key` header attached. */
@Composable
fun CoverImage(url: String, apiKey: String, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().set("X-API-Key", apiKey).build())
        .crossfade(true)
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * Poster-style card: cover (2:3), optional unread badge, title (2 lines), subtitle. Pass a non-null
 * [width] for horizontal rails; pass null to fill the width the parent gives (e.g. a grid cell).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CoverCard(
    coverUrl: String,
    apiKey: String,
    title: String,
    subtitle: String?,
    unread: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = CardWidth,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    inLibrary: Boolean = false,
) {
    val sizing = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Column(modifier.then(sizing).then(clickModifier)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(coverUrl, apiKey, Modifier.fillMaxSize())
            if (unread != null && unread > 0) {
                UnreadBadge(unread, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            } else if (inLibrary) {
                InLibraryShading()
                // "In library" mark for Browse — a filled check badge in the corner.
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "In library", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Already in your library" treatment for Browse covers: a coloured ring plus shading pulled in from
 * all four edges, matching the web UI's `inset 0 0 0 2px` + `inset 0 0 48px 14px` box-shadow. Compose
 * has no inset shadow, so the vignette is four edge gradients — they overlap at the corners, which is
 * what an inset shadow does there anyway.
 */
@Composable
private fun BoxScope.InLibraryShading() {
    val scrim = Color.Black.copy(alpha = 0.55f)
    val depth = 34.dp
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(depth).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(scrim, Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(depth).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim))),
        )
        Box(
            Modifier.fillMaxHeight().width(depth).align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(scrim, Color.Transparent))),
        )
        Box(
            Modifier.fillMaxHeight().width(depth).align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, scrim))),
        )
    }
    Box(Modifier.fillMaxSize().border(2.dp, InLibraryRing, RoundedCornerShape(12.dp)))
}

/** The web UI's in-library ring colour, kept identical so both clients mark it the same way. */
private val InLibraryRing = Color(0xFF20C997)

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A titled horizontal rail of cover cards — the Home screen's building block. */
@Composable
fun <T> CoverSection(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    onSeeAll: (() -> Unit)? = null,
    card: @Composable (T) -> Unit,
) {
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                androidx.compose.material3.TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            items(items = items, key = key) { card(it) }
        }
    }
}

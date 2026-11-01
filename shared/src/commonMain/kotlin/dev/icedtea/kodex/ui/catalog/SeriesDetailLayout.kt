package dev.icedtea.kodex.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icedtea.kodex.ui.MetaChip

// The chrome behind both series-detail screens — the one in a library and the one being browsed on a
// source. They render the same thing (blurred backdrop, cover + facts, chips, summary, then a list of
// entries) and only differ in which facts they know and which buttons they offer, so the shape lives
// here once and each screen fills in the slots.

/** Blurred cover behind the toolbar, fading into the surface so toolbar icons stay legible. */
@Composable
fun SeriesBackdrop(coverUrl: String, apiKey: String, height: Dp) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(height)) {
        CoverImage(coverUrl, apiKey, Modifier.fillMaxSize().blur(20.dp).alpha(0.55f))
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

/**
 * Cover, title and metadata at the top of a series.
 *
 * [facts] is the block under the title — credits, provenance pills — which is the part the two
 * screens genuinely disagree on: a library series knows its shelf, a source series knows its
 * publication status.
 *
 * [primary] sits right under the cover row, full width: the read/continue call to action and its
 * progress. It's the thing most visits to this screen are for, so it gets the most prominent spot
 * rather than a floating button covering the list.
 *
 * [actions] follows it (see [SeriesActionRow]); then the summary, then the genres/tags as one
 * horizontally scrolling strip — a wrapped flow of fifteen chips used to push the list off-screen.
 */
@Composable
fun SeriesHeader(
    coverUrl: String,
    apiKey: String,
    title: String,
    chips: List<String>,
    summary: String?,
    actions: (@Composable () -> Unit)? = null,
    primary: (@Composable () -> Unit)? = null,
    facts: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row {
            Box(
                Modifier.width(124.dp).height(186.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                CoverImage(coverUrl, apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                facts()
            }
        }
        if (primary != null) {
            Spacer(Modifier.height(16.dp))
            primary()
        }
        if (actions != null) {
            Spacer(Modifier.height(12.dp))
            actions()
        }
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            ExpandableSummary(summary)
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { MetaChip(it) }
            }
        }
    }
}

/**
 * A small fact about the series in the header — its source, language, shelf, status. Pills rather
 * than a stack of grey text lines, so each fact reads as its own thing at a glance.
 */
@Composable
fun InfoPill(
    text: String,
    icon: ImageVector? = null,
    accent: Boolean = false,
) {
    val container = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val onContainer = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(container).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The header's quick-action strip: evenly divided icon+label buttons across the full width.
 *
 * These are the series-wide maintenance actions (refresh, re-analyze, bookmarks, open on the source
 * site) that were buried in the toolbar's overflow menu — one tap from the header instead of two,
 * and visible enough that it's clear they exist at all. Everything rarer stays in the menu.
 */
@Composable
fun SeriesActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * One [SeriesActionRow] entry: a tonal icon tile over its label. Labels wrap to two lines rather
 * than shrinking, so "Refresh metadata" stays readable at four-across on a phone.
 */
@Composable
fun RowScope.SeriesAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
        )
    }
}

/**
 * The scrolling body: one padded header block followed by full-bleed entry rows.
 *
 * Rows span the full width (their selection highlight has to), so the side padding belongs to the
 * header rather than the list. The insets are the reason this is shared at all — the top one clears
 * the transparent toolbar and the bottom one clears the nav bar plus the resume button.
 */
@Composable
fun SeriesDetailList(
    listState: LazyListState,
    topInset: Dp,
    bottomInset: Dp,
    header: @Composable ColumnScope.() -> Unit,
    body: LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 96.dp + bottomInset),
    ) {
        item { Column(Modifier.padding(horizontal = 16.dp)) { header() } }
        body()
    }
}

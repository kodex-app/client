package dev.icedtea.kodex.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
 * [facts] is the block under the title — credits, counts, provenance — which is the part the two
 * screens genuinely disagree on: a library series knows its shelf and read counts, a source series
 * knows its publication status.
 *
 * [actions] sits between the chips and the summary: the handful of maintenance actions worth a tap
 * rather than a trip through the overflow menu (see [SeriesActionRow]).
 */
@Composable
fun SeriesHeader(
    coverUrl: String,
    apiKey: String,
    title: String,
    chips: List<String>,
    summary: String?,
    actions: (@Composable () -> Unit)? = null,
    facts: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row {
            Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) {
                CoverImage(coverUrl, apiKey, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                facts()
            }
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(15).forEach { MetaChip(it) }
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(14.dp))
            actions()
        }
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            ExpandableSummary(summary)
        }
    }
}

/**
 * The header's quick-action strip: evenly divided icon+label buttons across the full width.
 *
 * These are the series-wide maintenance actions (refresh, re-analyze, open on the source site,
 * remove) that were buried in the toolbar's overflow menu — one tap from the header instead of two,
 * and visible enough that it's clear they exist at all. Everything rarer stays in the menu.
 */
@Composable
fun SeriesActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * One [SeriesActionRow] entry. Labels wrap to two lines rather than shrinking, so "Refresh metadata"
 * stays readable at four-across on a phone.
 */
@Composable
fun RowScope.SeriesAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
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

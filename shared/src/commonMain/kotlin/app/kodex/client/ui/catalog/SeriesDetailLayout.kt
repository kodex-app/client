package app.kodex.client.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kodex.client.ui.MetaChip

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
 */
@Composable
fun SeriesHeader(
    coverUrl: String,
    apiKey: String,
    title: String,
    chips: List<String>,
    summary: String?,
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
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            ExpandableSummary(summary)
        }
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

package app.kodex.client.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kodex.client.network.SeriesDto

/** Adaptive grid of series covers — used by a library's drill-down (and reusable elsewhere). */
@Composable
fun SeriesGrid(
    baseUrl: String,
    apiKey: String,
    series: List<SeriesDto>,
    onOpen: (SeriesDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(series, key = { it.id }) { s ->
            CoverCard(
                coverUrl = seriesCoverUrl(baseUrl, s),
                apiKey = apiKey,
                title = s.title,
                subtitle = seriesSubtitle(s),
                unread = seriesUnreadBadge(s),
                onClick = { onOpen(s) },
                width = null,
            )
        }
    }
}

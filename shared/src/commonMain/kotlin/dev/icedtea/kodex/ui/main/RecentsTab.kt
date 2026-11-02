package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.recents.HistoryList
import dev.icedtea.kodex.ui.recents.UpdatesList
import kotlinx.coroutines.launch

/** "Recents" shows the Updates feed or reading History; a pill switch at the top (or a swipe) changes lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsTab(
    session: SessionManager,
    api: KodexApi,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
    onOpenBrowseReader: OpenBrowseReader,
    onOpenSeries: (String) -> Unit,
) {
    // Which of the two lists you were on has to outlive opening something from it: the tab area is
    // dropped while a detail screen is open, so a plain `remember` sent you back to Updates.
    val selected = retain("recents:tab") { mutableStateOf(0) }
    // The segmented button and the pager share one source of truth, so tapping and swiping agree.
    val pagerState = rememberPagerState(selected.value) { 2 }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selected.value = it }
    }
    val scope = rememberCoroutineScope()
    val showHistory = pagerState.currentPage == 1

    Column(Modifier.fillMaxSize()) {
        RecentsSwitch(
            showHistory = showHistory,
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 1) {
                HistoryList(session, api, onOpenReader, onOpenSourceReader, onOpenBrowseReader, onOpenSeries)
            } else {
                UpdatesList(session, api, onOpenReader, onOpenSourceReader, onOpenSeries)
            }
        }
    }
}

/**
 * Updates / History as a pill switch: a soft track with the active option raised, each with its icon —
 * the web UI's Recents switch.
 */
@Composable
private fun RecentsSwitch(showHistory: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            Triple(0, "Updates", Icons.Outlined.AutoAwesome),
            Triple(1, "History", Icons.Outlined.History),
        ).forEach { (page, label, icon) ->
            val active = (page == 1) == showHistory
            val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.weight(1f).height(38.dp)
                    .shadow(if (active) 2.dp else 0.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(page) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = fg)
            }
        }
    }
}

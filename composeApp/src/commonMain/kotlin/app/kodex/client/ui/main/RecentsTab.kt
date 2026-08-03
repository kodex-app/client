package app.kodex.client.ui.main

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.recents.HistoryList
import app.kodex.client.ui.recents.UpdatesList
import kotlinx.coroutines.launch

/** "Recents" shows the Updates feed or reading History; a segmented button at the top switches modes. */
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
    // The segmented button and the pager share one source of truth, so tapping and swiping agree.
    val pagerState = rememberPagerState(0) { 2 }
    val scope = rememberCoroutineScope()
    val showHistory = pagerState.currentPage == 1

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(
                selected = !showHistory,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Updates") }
            SegmentedButton(
                selected = showHistory,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("History") }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 1) {
                HistoryList(session, api, onOpenReader, onOpenSourceReader, onOpenBrowseReader, onOpenSeries)
            } else {
                UpdatesList(session, api, onOpenReader, onOpenSourceReader, onOpenSeries)
            }
        }
    }
}

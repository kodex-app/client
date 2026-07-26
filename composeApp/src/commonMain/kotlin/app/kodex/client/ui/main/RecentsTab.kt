package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.recents.HistoryList
import app.kodex.client.ui.recents.UpdatesList

/**
 * "Recents" shows either the Updates feed or the reading History; a floating button at the bottom
 * switches between the two (replacing the old top sub-tabs).
 */
@Composable
fun RecentsTab(
    session: SessionManager,
    api: KodexApi,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    var showHistory by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (showHistory) {
            HistoryList(session, api, onOpenReader, onOpenSourceReader)
        } else {
            UpdatesList(session, api, onOpenReader, onOpenSourceReader)
        }

        // Tapping opens the other section; the label/icon show where you'll go.
        ExtendedFloatingActionButton(
            onClick = { showHistory = !showHistory },
            icon = { Icon(if (showHistory) Icons.Filled.Refresh else Icons.Filled.DateRange, contentDescription = null) },
            text = { Text(if (showHistory) "Updates" else "History") },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        )
    }
}

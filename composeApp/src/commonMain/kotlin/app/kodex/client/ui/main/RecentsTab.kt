package app.kodex.client.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.recents.HistoryList
import app.kodex.client.ui.recents.UpdatesList

/** "Recents" groups the server's Updates feed and the user's reading History behind two sub-tabs. */
@Composable
fun RecentsTab(
    session: SessionManager,
    api: KodexApi,
    onOpenReader: (String) -> Unit,
    onOpenSourceReader: OpenSourceReader,
) {
    val sections = listOf("Updates", "History")
    var selected by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            sections.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        when (selected) {
            0 -> UpdatesList(session, api, onOpenReader, onOpenSourceReader)
            else -> HistoryList(session, api, onOpenReader, onOpenSourceReader)
        }
    }
}

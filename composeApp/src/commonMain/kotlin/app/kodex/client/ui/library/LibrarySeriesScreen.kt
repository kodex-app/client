package app.kodex.client.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.LibraryDto
import app.kodex.client.network.SeriesDto
import app.kodex.client.ui.EmptyMessage
import app.kodex.client.ui.LoadedContent
import app.kodex.client.ui.catalog.SeriesGrid
import app.kodex.client.ui.collectAsStateSafe

/** A single library's series, as an adaptive cover grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySeriesScreen(
    session: SessionManager,
    api: KodexApi,
    library: LibraryDto,
    onBack: () -> Unit,
    onOpenSeries: (SeriesDto) -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(library.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadedContent(
                key = library.id to server?.id,
                load = { val s = server!!; api.seriesInLibrary(s.baseUrl, s.apiKey, library.id) },
            ) { series ->
                if (series.isEmpty()) {
                    EmptyMessage("No series in this library yet.")
                } else {
                    val s = server
                    if (s != null) SeriesGrid(s.baseUrl, s.apiKey, series, onOpenSeries)
                }
            }
        }
    }
}

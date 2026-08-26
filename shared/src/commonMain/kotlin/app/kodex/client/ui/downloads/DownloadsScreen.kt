package app.kodex.client.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.DownloadJobDto
import app.kodex.client.network.KodexApi
import app.kodex.client.ui.PagedList
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.rememberPagedList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Downloads queue — content-source download jobs, newest first, with per-job pause/resume/retry/
 * cancel and global cancel-all / clear-finished / retry-failed. Polls every 2s while any job is
 * active so progress advances live (a lightweight stand-in until the SSE enabler lands).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val current = server ?: return
    val baseUrl = current.baseUrl
    val apiKey = current.apiKey
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    val snackbar = app.kodex.client.ui.rememberSnackbar()
    val paged = rememberPagedList(current.id, keyOf = { it.id }) { page -> api.downloads(baseUrl, apiKey, page) }

    // Live: refresh on any download state change (new job, completed, failed, paused…).
    app.kodex.client.ui.OnServerEvent(app.kodex.client.network.ServerEvent.DOWNLOAD_STATUS_CHANGED) {
        paged.silentRefresh()
    }
    // Poll while jobs run so the progress rings advance smoothly between state-change events.
    val anyActive = paged.items.any { it.isActive }
    LaunchedEffect(anyActive) {
        while (anyActive) {
            delay(2000)
            paged.silentRefresh()
        }
    }

    fun act(success: String? = null, block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.fold(
            onSuccess = { paged.silentRefresh(); success?.let { snackbar?.show(it) } },
            onFailure = { snackbar?.show("Action failed. Please try again.") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Download options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Retry failed") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                runCatching { api.retryFailedDownloads(baseUrl, apiKey) }.fold(
                                    onSuccess = { n -> paged.silentRefresh(); snackbar?.show(if (n > 0) "Retrying $n download(s)" else "No failed downloads") },
                                    onFailure = { snackbar?.show("Action failed. Please try again.") },
                                )
                            }
                        })
                        DropdownMenuItem(text = { Text("Clear finished") }, onClick = {
                            menuOpen = false
                            scope.launch {
                                runCatching { api.clearFinishedDownloads(baseUrl, apiKey) }.fold(
                                    onSuccess = { n -> paged.silentRefresh(); snackbar?.show(if (n > 0) "Cleared $n download(s)" else "Nothing to clear") },
                                    onFailure = { snackbar?.show("Action failed. Please try again.") },
                                )
                            }
                        })
                        DropdownMenuItem(text = { Text("Cancel all") }, onClick = {
                            menuOpen = false; act("All downloads cancelled") { api.cancelAllDownloads(baseUrl, apiKey) }
                        })
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PagedList(paged, emptyText = "No downloads.\nFollow a WEB series and download chapters to see them here.") { items ->
                items.forEachIndexed { index, job ->
                    item(key = job.id) {
                        DownloadRow(
                            job = job,
                            onCancel = { act { api.downloadAction(baseUrl, apiKey, job.id, "cancel") } },
                            onPause = { act { api.downloadAction(baseUrl, apiKey, job.id, "pause") } },
                            onResume = { act { api.downloadAction(baseUrl, apiKey, job.id, "resume") } },
                            onRetry = { act { api.downloadAction(baseUrl, apiKey, job.id, "retry") } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    job: DownloadJobDto,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), Alignment.Center) {
            when (job.state) {
                "RUNNING" -> {
                    CircularProgressIndicator(progress = { job.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                    Text("${(job.progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall)
                }
                "QUEUED" -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> StatusDot(job.state)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                job.seriesName ?: "Series",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                job.chapterName ?: job.message ?: statusLabel(job.state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                statusLabel(job.state) + (job.message?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (job.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (job.state == "RUNNING" || job.state == "QUEUED") {
                    DropdownMenuItem(text = { Text("Pause") }, onClick = { menu = false; onPause() })
                }
                if (job.isPaused) {
                    DropdownMenuItem(text = { Text("Resume") }, onClick = { menu = false; onResume() })
                }
                if (job.isFailed) {
                    DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; onRetry() })
                }
                if (job.isActive) {
                    DropdownMenuItem(text = { Text("Cancel") }, onClick = { menu = false; onCancel() })
                }
            }
        }
    }
}

@Composable
private fun StatusDot(state: String) {
    val color = when (state) {
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        "FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(12.dp).padding(0.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
    }
}

private fun statusLabel(state: String): String = when (state) {
    "QUEUED" -> "Queued"
    "RUNNING" -> "Downloading"
    "PAUSED" -> "Paused"
    "COMPLETED" -> "Completed"
    "FAILED" -> "Failed"
    "CANCELLED" -> "Cancelled"
    else -> state
}

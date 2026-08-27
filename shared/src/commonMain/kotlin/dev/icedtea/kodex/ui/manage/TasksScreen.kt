package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.ServerEvent
import dev.icedtea.kodex.network.TaskDto
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.OnServerEvent
import dev.icedtea.kodex.ui.TooltipIconButton
import dev.icedtea.kodex.ui.catalog.ColorBadge
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.relativeTime
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * The background task queue, read-only apart from "cancel all".
 *
 * The server has no per-task cancel — only the bulk one on `/admin/tasks/cancel-all` — so the screen
 * offers exactly that rather than a per-row action that would have to fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<TaskDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var confirmCancelAll by remember { mutableStateOf(false) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.tasks(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { tasks = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
    }

    // The queue is exactly what SSE reports on, so it refreshes itself instead of polling.
    OnServerEvent(ServerEvent.TASK_STATUS_CHANGED) { reload++ }

    val running = tasks.orEmpty().count { it.status.equals("RUNNING", ignoreCase = true) }
    val queued = tasks.orEmpty().count { it.status.equals("PENDING", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TooltipIconButton("Refresh", { reload++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    TooltipIconButton("Cancel all", { confirmCancelAll = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel all")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tasks != null) {
                Text(
                    "$running running · $queued queued · ${tasks.orEmpty().size} shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when (val list = tasks) {
                null -> if (loadError != null) ErrorState(loadError!!) { reload++ }
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> if (list.isEmpty()) {
                    EmptyMessage("The queue is empty.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { task ->
                            TaskRow(task)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (confirmCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmCancelAll = false },
            title = { Text("Cancel all tasks?") },
            text = { Text("Queued and running tasks are stopped. Scans already part-way through leave what they imported.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancelAll = false
                    val s = server ?: return@TextButton
                    scope.launch {
                        runCatching { api.cancelAllTasks(s.baseUrl, s.apiKey) }.fold(
                            onSuccess = { snackbar?.show(if (it == 1) "1 task cancelled" else "$it tasks cancelled"); reload++ },
                            onFailure = { snackbar?.show(it.friendlyMessage()) },
                        )
                    }
                }) { Text("Cancel all") }
            },
            dismissButton = { TextButton(onClick = { confirmCancelAll = false }) { Text("Keep running") } },
        )
    }
}

@Composable
private fun TaskRow(task: TaskDto) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(prettyTaskType(task.type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            task.message?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            task.lastModifiedDate?.let {
                Spacer(Modifier.height(2.dp))
                Text(relativeTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ColorBadge(task.status) }
    }
}

/** "LIBRARY_SCAN" → "Library scan" — task types are enum names, which read badly as-is. */
private fun prettyTaskType(type: String): String =
    type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

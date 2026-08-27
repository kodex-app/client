package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** One server-wide action, with the confirmation copy it needs before running. */
private data class ServerAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val confirmTitle: String,
    val confirmBody: String,
    val confirmLabel: String,
    /** Shutdown is the one that cannot be undone from the app — it can't start the server again. */
    val destructive: Boolean = false,
    val run: suspend (KodexApi, String, String) -> String,
)

/**
 * Server-wide maintenance: rescan everything, stop the queue, shut the server down.
 *
 * Each one is confirmed, because none is scoped to a single library and two of them interrupt work in
 * progress. Shutdown is last and marked destructive: the app has no way to start the server again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerActionsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf<ServerAction?>(null) }

    val actions = listOf(
        ServerAction(
            title = "Refresh all libraries",
            subtitle = "Scan every library for new files and content",
            icon = Icons.Filled.Refresh,
            confirmTitle = "Refresh all libraries?",
            confirmBody = "Every library is queued for a scan. New files are picked up; existing ones are left alone.",
            confirmLabel = "Refresh all",
            run = { a, base, key -> "${a.refreshAllLibraries(base, key, deep = false)} libraries queued" },
        ),
        ServerAction(
            title = "Deep scan all libraries",
            subtitle = "Re-read files already known — slow, use after moving or repairing files",
            icon = Icons.Filled.Search,
            confirmTitle = "Deep scan everything?",
            confirmBody = "Every file is re-read, not just new ones. On a large collection this can take hours and will keep the queue busy.",
            confirmLabel = "Deep scan",
            run = { a, base, key -> "${a.refreshAllLibraries(base, key, deep = true)} libraries queued" },
        ),
        ServerAction(
            title = "Cancel all tasks",
            subtitle = "Stop everything queued or running",
            icon = Icons.Filled.Close,
            confirmTitle = "Cancel all tasks?",
            confirmBody = "Queued and running tasks are stopped. Scans part-way through keep whatever they already imported.",
            confirmLabel = "Cancel all",
            run = { a, base, key -> a.cancelAllTasks(base, key).let { if (it == 1) "1 task cancelled" else "$it tasks cancelled" } },
        ),
        ServerAction(
            title = "Shut down server",
            subtitle = "Stop the Kodex process",
            icon = Icons.Filled.PowerSettingsNew,
            confirmTitle = "Shut down the server?",
            confirmBody = "Everyone is disconnected and this app cannot start it again — you will need access to the machine running Kodex.",
            confirmLabel = "Shut down",
            destructive = true,
            run = { a, base, key -> a.shutdownServer(base, key); "Shutting down…" },
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server actions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    actions.forEachIndexed { i, action ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                        ActionRow(action) { confirming = action }
                    }
                }
            }
        }
    }

    confirming?.let { action ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(action.confirmTitle) },
            text = { Text(action.confirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    val s = server ?: return@TextButton
                    scope.launch {
                        runCatching { action.run(api, s.baseUrl, s.apiKey) }.fold(
                            onSuccess = { snackbar?.show(it) },
                            onFailure = { snackbar?.show(it.friendlyMessage()) },
                        )
                    }
                }) { Text(action.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(action: ServerAction, onClick: () -> Unit) {
    val tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(action.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
            Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

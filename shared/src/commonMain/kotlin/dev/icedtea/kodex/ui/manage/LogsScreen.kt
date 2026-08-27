package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LogEntryDto
import dev.icedtea.kodex.ui.EmptyMessage
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.TooltipIconButton
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Levels in severity order; the filter keeps this level and everything above it. */
private val LEVELS = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")

/**
 * The server's recent log buffer, with a level filter and the debug-logging toggle.
 *
 * Reads the buffered `/server/logs` endpoint on demand rather than holding the `/stream` SSE open: a
 * second always-on stream alongside the app's event bus would keep the radio busy for a screen you
 * look at occasionally. Pull the refresh button for the current tail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf<List<LogEntryDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var minLevel by remember { mutableStateOf("INFO") }
    var debug by remember { mutableStateOf<Boolean?>(null) }

    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.recentLogs(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { logs = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
        if (debug == null) debug = runCatching { api.debugMode(s.baseUrl, s.apiKey) }.getOrNull()
    }

    val threshold = LEVELS.indexOf(minLevel)
    val visible = logs.orEmpty().filter { LEVELS.indexOf(it.level.uppercase()).let { i -> i < 0 || i >= threshold } }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TooltipIconButton("Refresh", { reload++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LEVELS.forEach { level ->
                    FilterChip(selected = minLevel == level, onClick = { minLevel = level }, label = { Text(level) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Debug logging", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Turns on DEBUG for dev.kodex on the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = debug == true,
                    enabled = debug != null,
                    onCheckedChange = { want ->
                        val s = server ?: return@Switch
                        scope.launch {
                            runCatching { api.setDebugMode(s.baseUrl, s.apiKey, want) }.fold(
                                onSuccess = { debug = it; reload++ },
                                onFailure = { snackbar?.show(it.friendlyMessage()) },
                            )
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            when {
                logs == null && loadError != null -> ErrorState(loadError!!) { reload++ }
                logs == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                visible.isEmpty() -> EmptyMessage("Nothing logged at $minLevel or above.")
                else -> SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(visible) { i, entry ->
                            LogRow(entry)
                            if (i < visible.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntryDto) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelTag(entry.level)
            Spacer(Modifier.height(0.dp))
            Text(
                entry.logger.substringAfterLast('.'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        entry.throwable?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 12,
            )
        }
    }
}

@Composable
private fun LevelTag(level: String) {
    val color = when (level.uppercase()) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARN" -> MaterialTheme.colorScheme.tertiary
        "DEBUG", "TRACE" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.background(color.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(level.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

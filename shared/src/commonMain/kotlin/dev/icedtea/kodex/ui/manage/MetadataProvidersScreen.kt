package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.MetadataProviderDto
import dev.icedtea.kodex.ui.ErrorState
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar

/**
 * The metadata providers built into the server (AniList, ComicVine, MangaUpdates, …): each has its
 * own settings (API keys, options) edited through [SourceConfigSheet]. Enabling them per library is
 * done on the library form. Mirrors the web UI's Extensions › Metadata tab. Admin-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataProvidersScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    var providers by remember { mutableStateOf<List<MetadataProviderDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var configuring by remember { mutableStateOf<MetadataProviderDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.metadataProviders(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { providers = it.sortedBy { p -> p.displayName.lowercase() }; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metadata providers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (val list = providers) {
            null -> if (loadError != null) ErrorState(loadError!!, onRetry = { reload++ })
                else Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        "Built into the server. Enable them per library on the library form; set their API keys here.",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName.ifBlank { p.id }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(p.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.configSchema.isNotEmpty()) TextButton(onClick = { configuring = p }) { Text("Settings") }
                        else Text("Built-in", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    val s = server
    val cfg = configuring
    if (cfg != null && s != null) {
        SourceConfigSheet(
            api = api,
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            providerId = cfg.id,
            onDismiss = { configuring = null },
            onSaved = { message -> configuring = null; snackbar?.show(message) },
            metadata = true,
        )
    }
}

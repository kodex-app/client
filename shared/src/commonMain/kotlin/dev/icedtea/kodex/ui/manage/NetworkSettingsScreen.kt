package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.NetworkSettingsDto
import dev.icedtea.kodex.network.NetworkSettingsRequest
import dev.icedtea.kodex.ui.InlineLoadError
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

private val PROXY_TYPES = listOf("HTTP", "SOCKS4", "SOCKS5")

/**
 * How the *server* reaches the internet: proxy, DNS-over-HTTPS, and the Cloudflare solver that content
 * sources fall back to. Nothing here affects this app's own connection to the server.
 *
 * Saved as one whole object — the API replaces the record rather than patching fields — so the screen
 * edits a local copy and sends it on Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<NetworkSettingsDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.networkSettings(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { loaded = it; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val current = loaded
            if (current == null) {
                if (loadError != null) InlineLoadError(loadError!!) { reload++ }
                else CircularProgressIndicator(Modifier.padding(16.dp))
                return@Column
            }

            var proxyEnabled by remember(current) { mutableStateOf(current.proxyEnabled) }
            var proxyType by remember(current) { mutableStateOf(current.proxyType) }
            var host by remember(current) { mutableStateOf(current.proxyHost) }
            var port by remember(current) { mutableStateOf(current.proxyPort.takeIf { it > 0 }?.toString().orEmpty()) }
            var username by remember(current) { mutableStateOf(current.proxyUsername) }
            // Blank keeps the stored password; the server treats null as "unchanged".
            var password by remember(current) { mutableStateOf("") }
            var dohEnabled by remember(current) { mutableStateOf(current.dohEnabled) }
            var dohUrl by remember(current) { mutableStateOf(current.dohUrl) }
            var solverEnabled by remember(current) { mutableStateOf(current.cloudflareSolverEnabled) }
            var solverUrl by remember(current) { mutableStateOf(current.cloudflareSolverUrl) }
            var solverTimeout by remember(current) { mutableStateOf(current.cloudflareSolverTimeoutSeconds.toString()) }

            SettingsSectionHeader("Proxy")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow("Route traffic through a proxy", proxyEnabled) { proxyEnabled = it }
                    if (proxyEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            PROXY_TYPES.forEach { type ->
                                FilterChip(
                                    selected = proxyType == type,
                                    onClick = { proxyType = type },
                                    label = { Text(type) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(host, { host = it }, singleLine = true, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(username, { username = it }, singleLine = true, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            label = { Text(if (current.proxyPasswordSet) "Change password" else "Password (optional)") },
                            supportingText = { if (current.proxyPasswordSet) Text("Leave blank to keep the current one") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("DNS over HTTPS")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow("Resolve names over HTTPS", dohEnabled) { dohEnabled = it }
                    if (dohEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dohUrl,
                            onValueChange = { dohUrl = it },
                            singleLine = true,
                            label = { Text("Resolver URL") },
                            supportingText = { Text("e.g. https://cloudflare-dns.com/dns-query") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("Cloudflare solver")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "An external service that answers Cloudflare challenges for content sources that sit behind them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Use a solver", solverEnabled) { solverEnabled = it }
                    if (solverEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(solverUrl, { solverUrl = it }, singleLine = true, label = { Text("Solver URL") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = solverTimeout,
                            onValueChange = { solverTimeout = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            label = { Text("Timeout (seconds)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy,
                onClick = {
                    val s = server ?: return@Button
                    busy = true
                    scope.launch {
                        val request = NetworkSettingsRequest(
                            proxyEnabled = proxyEnabled,
                            proxyType = proxyType,
                            proxyHost = host.trim(),
                            proxyPort = port.toIntOrNull() ?: 0,
                            proxyUsername = username.trim(),
                            proxyPassword = password.ifBlank { null },
                            dohEnabled = dohEnabled,
                            dohUrl = dohUrl.trim(),
                            cloudflareSolverEnabled = solverEnabled,
                            cloudflareSolverUrl = solverUrl.trim(),
                            cloudflareSolverTimeoutSeconds = solverTimeout.toIntOrNull() ?: 60,
                        )
                        runCatching { api.saveNetworkSettings(s.baseUrl, s.apiKey, request) }.fold(
                            onSuccess = { loaded = it; snackbar?.show("Network settings saved") },
                            onFailure = { snackbar?.show(it.friendlyMessage()) },
                        )
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

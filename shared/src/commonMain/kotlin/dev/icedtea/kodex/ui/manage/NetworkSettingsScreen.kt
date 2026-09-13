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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import dev.icedtea.kodex.network.BrowserStatusDto
import dev.icedtea.kodex.network.NetworkSettingsDto
import dev.icedtea.kodex.network.NetworkSettingsRequest
import dev.icedtea.kodex.ui.InlineLoadError
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PROXY_TYPES = listOf("HTTP", "SOCKS4", "SOCKS5")

/**
 * How the *server* reaches the internet: proxy, DNS-over-HTTPS, the Cloudflare solver that content
 * sources fall back to, and the browser Mihon sources use as their WebView (a remote browserless, or
 * the Chromium on the server). Nothing here affects this app's own connection to the server.
 *
 * Saved as one whole object — the API replaces the record rather than patching fields — so the screen
 * edits a local copy and sends it on Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit, onOpenPage: (String) -> Unit = {}) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<NetworkSettingsDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<BrowserStatusDto?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var openUrl by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.networkSettings(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { loaded = it; browser = it.browser; loadError = null },
            onFailure = { loadError = it.friendlyMessage() },
        )
        // The browser starts and stops on its own; keep its line current while the screen is open.
        while (true) {
            delay(5_000)
            runCatching { api.browserStatus(s.baseUrl, s.apiKey) }.onSuccess { browser = it }
        }
    }

    openUrl?.let { draft ->
        var value by remember(draft) { mutableStateOf(draft) }
        AlertDialog(
            onDismissRequest = { openUrl = null },
            title = { Text("Open a page") },
            text = {
                Column {
                    Text("Opens a URL in the server's browser so you can log in or pass an anti-bot check by hand; cookies go to the Mihon extensions.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { openUrl = null; onOpenPage(value.trim()) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { openUrl = null }) { Text("Cancel") } },
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
            var remoteBrowser by remember(current) { mutableStateOf(current.remoteBrowserEnabled) }
            var remoteBrowserUrl by remember(current) { mutableStateOf(current.remoteBrowserUrl) }

            // Only sections that are switched on are checked; the server silently treats an unusable value as
            // "off" (NetworkSettings.*Active), so Save stays disabled until every enabled section is complete.
            val hostError = if (proxyEnabled && host.isBlank()) "Enter the proxy host" else null
            val portError = if (proxyEnabled && port.toIntOrNull()?.let { it in 1..65535 } != true) "Port must be between 1 and 65535" else null
            val dohError = if (dohEnabled && !isUrl(dohUrl, "https")) "Enter an https:// URL" else null
            val solverUrlError = if (solverEnabled && !isUrl(solverUrl, "http", "https")) "Enter an http:// or https:// URL" else null
            val solverTimeoutError = if (solverEnabled && solverTimeout.toIntOrNull()?.let { it in 5..300 } != true) "Timeout must be between 5 and 300 seconds" else null
            val remoteBrowserError = if (remoteBrowser && !isUrl(remoteBrowserUrl, "ws", "wss")) "Enter a ws:// or wss:// URL" else null
            val valid = listOf(hostError, portError, dohError, solverUrlError, solverTimeoutError, remoteBrowserError).all { it == null }

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
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            singleLine = true,
                            label = { Text("Host") },
                            isError = hostError != null,
                            supportingText = hostError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            label = { Text("Port") },
                            isError = portError != null,
                            supportingText = portError?.let { { Text(it) } },
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
                            isError = dohError != null,
                            supportingText = { Text(dohError ?: "e.g. https://cloudflare-dns.com/dns-query") },
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
                        OutlinedTextField(
                            value = solverUrl,
                            onValueChange = { solverUrl = it },
                            singleLine = true,
                            label = { Text("Solver URL") },
                            isError = solverUrlError != null,
                            supportingText = { Text(solverUrlError ?: "e.g. http://localhost:8191") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = solverTimeout,
                            onValueChange = { solverTimeout = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            label = { Text("Timeout (seconds)") },
                            isError = solverTimeoutError != null,
                            supportingText = solverTimeoutError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("Browser (Mihon WebView)")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Some Mihon sources need a real browser: page scripts that compute image URLs, or anti-bot interstitials. Off: the Chromium/Chrome/Edge installed on the server runs headless. On: a remote headless browser (browserless) is used instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Use a remote browser (browserless)", remoteBrowser) { remoteBrowser = it }
                    if (remoteBrowser) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = remoteBrowserUrl,
                            onValueChange = { remoteBrowserUrl = it },
                            singleLine = true,
                            label = { Text("DevTools WebSocket URL") },
                            isError = remoteBrowserError != null,
                            supportingText = { Text(remoteBrowserError ?: "e.g. ws://browserless:3000?token=…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.embeddedBrowser?.let { "Using the browser on the server: $it" }
                                ?: "No Chromium/Chrome/Edge found on the server — sources that need a WebView will report it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (current.embeddedBrowser != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    browser?.let { b ->
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val state = when {
                                !b.running -> "Not running"
                                b.pagesOpen > 0 -> "Running · ${b.pagesOpen}/${b.maxPages} pages"
                                else -> "Idle" + (b.idleSeconds?.let { " for ${formatSeconds(it)}" } ?: "")
                            }
                            Text(state, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = b.mode != "none", onClick = { openUrl = "" }) { Text("Open a page…") }
                            if (b.running) TextButton(enabled = !stopping, onClick = {
                                val s = server ?: return@TextButton
                                stopping = true
                                scope.launch {
                                    runCatching { api.stopBrowser(s.baseUrl, s.apiKey) }.fold(
                                        onSuccess = { browser = it; snackbar?.show("Browser stopped; it starts again on the next source that needs it") },
                                        onFailure = { snackbar?.show(it.friendlyMessage()) },
                                    )
                                    stopping = false
                                }
                            }) { Text("Stop") }
                        }
                        Text(
                            BROWSER_HINT,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && valid,
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
                            remoteBrowserEnabled = remoteBrowser,
                            remoteBrowserUrl = remoteBrowserUrl.trim(),
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

private const val BROWSER_HINT = "The browser starts on the first source that needs it and stops on its own after a few minutes without pages. \"Open a page…\" lets you log in or pass an anti-bot check by hand; the cookies go to the Mihon extensions."

/** `scheme://host…` with one of the given schemes and a non-empty host — enough to catch a bare hostname or a typo'd scheme. */
private fun isUrl(value: String, vararg schemes: String): Boolean {
    val v = value.trim()
    val scheme = schemes.firstOrNull { v.startsWith("$it://", ignoreCase = true) } ?: return false
    val rest = v.substring(scheme.length + 3)
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    return authority.substringAfter('@').substringBefore(':').isNotBlank()
}

private fun formatSeconds(seconds: Long): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min"

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

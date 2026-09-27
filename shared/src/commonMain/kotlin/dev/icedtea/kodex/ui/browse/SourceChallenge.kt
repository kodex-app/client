package dev.icedtea.kodex.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.multiplatform.webview.cookie.Cookie
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.importSourceCookies
import dev.icedtea.kodex.platform.challengeWebViewUserAgent
import dev.icedtea.kodex.platform.prepareChallengeWebView
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberIsAdmin
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Offered where a source call failed with the server's `challenge` (see `sourceChallengeUrl`): the
 * site wants a human check its own browser couldn't pass — Cloudflare Turnstile rejects every
 * automated browser. The LNReader app's answer is "open it in WebView", and that is what this does:
 * the page opens in a real WebView on this device, the person passes the check, and on Done the
 * site's cookies plus this WebView's User-Agent go to the source on the server
 * (`import-cookies`), after which [onSolved] retries. Admin only, like the endpoint.
 */
@Composable
fun SourceChallengeActions(
    session: SessionManager,
    api: KodexApi,
    sourceId: String,
    challengeUrl: String,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rememberIsAdmin(session)) {
            Text(
                "The site wants a human check the server can't pass by itself. Pass it here on this device " +
                    "and the server will use its cookies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { open = true }) {
                Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Open in WebView")
            }
        } else {
            Text(
                "The site wants a human check the server can't pass by itself — an admin can pass it from their device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (open) {
        SourceWebViewDialog(
            session, api, sourceId, challengeUrl,
            onDismiss = { open = false },
            onImported = { open = false; onSolved() },
        )
    }
}

/**
 * A full-screen WebView at [url] for passing a site's check (or logging in) on this device. Done
 * copies the site's cookies and this WebView's User-Agent to the source [sourceId] on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceWebViewDialog(
    session: SessionManager,
    api: KodexApi,
    sourceId: String,
    url: String,
    title: String = "",
    onDismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val state = rememberWebViewState(url).apply {
        webSettings.isJavaScriptEnabled = true
        webSettings.androidWebSettings.domStorageEnabled = true
    }
    val navigator = rememberWebViewNavigator()
    var native by remember { mutableStateOf<NativeWebView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun userAgent(): String? {
        native?.let { challengeWebViewUserAgent(it) }?.let { return it }
        val result = CompletableDeferred<String>()
        navigator.evaluateJavaScript("navigator.userAgent") { result.complete(it) }
        // Android hands back the value JSON-encoded ("\"Mozilla/5.0 …\""), iOS as-is.
        val raw = withTimeoutOrNull(3_000) { result.await() }?.trim() ?: return null
        return if (raw.startsWith("\"")) runCatching { Json.decodeFromString<String>(raw) }.getOrNull() else raw.ifBlank { null }
    }

    fun done() {
        val s = server ?: return
        scope.launch {
            saving = true
            error = null
            runCatching {
                // The page the check ran on, plus wherever it ended up (a redirect to the site's root).
                val pages = listOfNotNull(url, state.lastLoadedUrl?.takeIf { it.startsWith("http") }).distinct()
                val cookies = pages.flatMap { state.cookieManager.getCookies(it) }.distinctBy { it.name to it.domain }
                if (cookies.isEmpty()) error("The site set no cookies yet — finish the check first (read until the text shows).")
                api.importSourceCookies(s.baseUrl, s.apiKey, sourceId, url, cookiesJson(cookies), userAgent())
            }.onSuccess {
                snackbar?.show("${it.imported} cookies sent to the server" + if (it.userAgentSet) " (User-Agent set)" else "")
                onImported()
            }.onFailure {
                error = if (it is IllegalStateException) it.message else it.friendlyMessage()
            }
            saving = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            title.ifBlank { state.pageTitle ?: "WebView" },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                    actions = {
                        IconButton(enabled = navigator.canGoBack, onClick = { navigator.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Page back")
                        }
                        IconButton(onClick = { navigator.reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                        TextButton(enabled = !saving && server != null, onClick = { done() }) { Text("Done") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val loading = state.loadingState
                if (loading is LoadingState.Loading) {
                    LinearProgressIndicator(progress = { loading.progress }, modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    WebView(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        navigator = navigator,
                        onCreated = { view ->
                            prepareChallengeWebView(view)
                            native = view
                        },
                    )
                    if (saving) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                Text(
                    "Pass the check (read until the chapter text shows), then tap Done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The server's import format (Cookie-Editor JSON): what the platform reported, expiry in seconds. */
private fun cookiesJson(cookies: List<Cookie>): String = buildJsonArray {
    for (c in cookies) {
        add(
            buildJsonObject {
                put("name", c.name)
                put("value", c.value)
                c.domain?.takeIf { it.isNotBlank() }?.let { put("domain", it); put("hostOnly", !it.startsWith(".")) }
                c.path?.takeIf { it.isNotBlank() }?.let { put("path", it) }
                c.isSecure?.let { put("secure", it) }
                c.isHttpOnly?.let { put("httpOnly", it) }
                // Android reports milliseconds, iOS seconds.
                c.expiresDate?.takeIf { it > 0 }?.let { put("expirationDate", JsonPrimitive(if (it > 100_000_000_000L) it / 1000 else it)) }
            },
        )
    }
}.toString()

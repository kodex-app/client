package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.MihonBrowserMouseRequest
import dev.icedtea.kodex.network.MihonBrowserSessionDto
import dev.icedtea.kodex.network.browserAction
import dev.icedtea.kodex.network.browserClose
import dev.icedtea.kodex.network.browserFrame
import dev.icedtea.kodex.network.browserKey
import dev.icedtea.kodex.network.browserMouse
import dev.icedtea.kodex.network.browserNavigate
import dev.icedtea.kodex.network.browserOpen
import dev.icedtea.kodex.network.browserSession
import dev.icedtea.kodex.network.browserText
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Mihon app's WebView screen, driven remotely: a page in the *server's* browser streamed as JPEG
 * frames, with taps forwarded as clicks and a text row for typing (a real keyboard on a remote page
 * is awkward on a phone — type in the field, send, then Enter). Drag scrolls. Used to log in to a
 * source's site or pass an anti-bot check by hand; the cookies the page ends up with go to the
 * extension after every load and when the page is closed. Admin only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihonBrowserScreen(
    session: SessionManager,
    api: KodexApi,
    url: String?,
    sourceId: String?,
    title: String,
    onBack: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf<MihonBrowserSessionDto?>(null) }
    var frame by remember { mutableStateOf<ByteArray?>(null) }
    var frameSeq by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf(url ?: "") }
    var typed by remember { mutableStateOf("") }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Open the page once per screen, then long-poll frames until the screen goes away.
    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        val opened = runCatching { api.browserOpen(s.baseUrl, s.apiKey, url, sourceId) }.getOrElse { error = it.friendlyMessage(); return@LaunchedEffect }
        page = opened
        address = opened.url
        var since = 0L
        var tick = 0
        while (true) {
            try {
                api.browserFrame(s.baseUrl, s.apiKey, opened.id, since)?.let { (bytes, seq) ->
                    frame = bytes; since = seq; frameSeq = seq
                }
                if (++tick % 4 == 0) {
                    runCatching { api.browserSession(s.baseUrl, s.apiKey, opened.id) }.onSuccess { page = it; if (it.url.isNotBlank()) address = it.url }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.friendlyMessage()
                break
            }
        }
    }
    // Closing the page keeps its cookies for the extension.
    DisposableEffect(page?.id) {
        val id = page?.id
        onDispose {
            if (id != null) {
                val s = server
                scope.launch { withContext(NonCancellable) { runCatching { if (s != null) api.browserClose(s.baseUrl, s.apiKey, id) } } }
            }
        }
    }

    fun send(block: suspend (baseUrl: String, apiKey: String, id: String) -> Unit) {
        val s = server ?: return
        val id = page?.id ?: return
        scope.launch { runCatching { block(s.baseUrl, s.apiKey, id) }.onFailure { snackbar?.show(it.friendlyMessage()) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { page?.title ?: "Browser" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(enabled = page != null, onClick = { send { b, k, id -> api.browserAction(b, k, id, "back") } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Page back") }
                    IconButton(enabled = page != null, onClick = { send { b, k, id -> api.browserAction(b, k, id, "reload") } }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                    TextButton(enabled = page != null, onClick = {
                        send { b, k, id -> val p = api.browserAction(b, k, id, "save-cookies"); page = p; snackbar?.show("${p.cookiesSaved} cookies saved for the extension") }
                    }) { Text("Save cookies") }
                    TextButton(onClick = onBack) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                enabled = page != null,
                placeholder = { Text("https://") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { send { b, k, id -> page = api.browserNavigate(b, k, id, address.trim()) } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            val p = page
            Box(
                Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    error != null -> Text(error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    p == null || frame == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                    else -> {
                        val w = p.width.toFloat()
                        val h = p.height.toFloat()
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(w / h).background(Color.White)
                                .onSizeChanged { viewSize = it }
                                .pointerInput(p.id) {
                                    detectTapGestures { offset ->
                                        if (viewSize.width == 0) return@detectTapGestures
                                        val x = offset.x / viewSize.width * w
                                        val y = offset.y / viewSize.height * h
                                        send { b, k, id ->
                                            api.browserMouse(b, k, id, MihonBrowserMouseRequest("mouseMoved", x.toDouble(), y.toDouble()))
                                            api.browserMouse(b, k, id, MihonBrowserMouseRequest("mousePressed", x.toDouble(), y.toDouble()))
                                            api.browserMouse(b, k, id, MihonBrowserMouseRequest("mouseReleased", x.toDouble(), y.toDouble()))
                                        }
                                    }
                                }
                                .pointerInput(p.id) {
                                    // Dragging scrolls the page: wheel deltas in page pixels, opposite to the finger.
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        if (viewSize.width == 0) return@detectDragGestures
                                        val x = change.position.x / viewSize.width * w
                                        val y = change.position.y / viewSize.height * h
                                        val dx = -drag.x / viewSize.width * w
                                        val dy = -drag.y / viewSize.height * h
                                        send { b, k, id -> api.browserMouse(b, k, id, MihonBrowserMouseRequest("mouseWheel", x.toDouble(), y.toDouble(), deltaX = dx.toDouble(), deltaY = dy.toDouble())) }
                                    }
                                },
                        ) {
                            AsyncImage(model = frame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            // Typing row: text goes to the focused element on the page; Enter submits a form.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    enabled = page != null,
                    placeholder = { Text("Type into the page…") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(enabled = page != null && typed.isNotEmpty(), onClick = { val t = typed; typed = ""; send { b, k, id -> api.browserText(b, k, id, t) } }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send text")
                }
                TextButton(enabled = page != null, onClick = { send { b, k, id -> api.browserKey(b, k, id, "Enter") } }) { Text("Enter") }
                IconButton(enabled = page != null, onClick = { send { b, k, id -> api.browserKey(b, k, id, "Backspace") } }) {
                    Icon(Icons.Filled.Close, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "Tap to click, drag to scroll. Type in the field and send, then Enter. Cookies stay with the extension when you close.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package app.kodex.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.kodex.client.network.KodexApi
import app.kodex.client.platform.WebEngineState
import app.kodex.client.platform.createHttpClient
import app.kodex.client.platform.disposeWebEngine
import app.kodex.client.platform.ensureWebEngine
import app.kodex.client.ui.reader.ebook.EbookHost
import app.kodex.client.ui.reader.ebook.EbookHostHandle
import app.kodex.client.ui.reader.ebook.EbookHostSession
import app.kodex.client.ui.reader.ebook.EbookOrigin
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.LinkedBlockingQueue

/**
 * End-to-end check that the reader actually *renders*: boots the real host, points a real WebView at
 * it, and waits for the `ready` event — which `reader.js` only posts once foliate has loaded the
 * book, parsed its OPF and laid out the first page. Anything short of a working engine, a working
 * proxy and working ES-module loading fails to produce it.
 *
 * Run: ./gradlew :composeApp:verifyEbookRender   (needs a live server holding an EPUB; downloads
 * Chromium on first run).
 */
private val events = LinkedBlockingQueue<String>()

/**
 * The next event of one of [types], or null if none arrives in time. Polls with a deadline rather
 * than blocking on `take()`, which ignores coroutine cancellation and would hang the harness.
 */
private suspend fun nextEvent(vararg types: String, timeoutMs: Long = 10_000): kotlinx.serialization.json.JsonObject? =
    withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val raw = events.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
            println("  event: ${raw.take(200)}")
            val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
            if (obj["type"]?.jsonPrimitive?.content in types) return@withContext obj
        }
        null
    }

private fun frac(o: kotlinx.serialization.json.JsonObject?): Double? =
    o?.get("fraction")?.jsonPrimitive?.content?.toDoubleOrNull()

/**
 * Drives the commands the reader's chrome issues — a page turn, a seek, and a settings change — and
 * checks the page reports back a matching position each time.
 */
private suspend fun checkCommands(handle: app.kodex.client.ui.reader.ebook.EbookHostHandle): String {
    events.clear()
    handle.send("""{"cmd":"next"}""")
    val afterTurn = nextEvent("relocate") ?: return "FAIL  the next-page command produced no relocate"
    val turned = frac(afterTurn) ?: return "FAIL  relocate carried no fraction"

    events.clear()
    handle.send("""{"cmd":"goToFraction","fraction":0.75}""")
    val afterSeek = nextEvent("relocate") ?: return "FAIL  the seek command produced no relocate"
    val sought = frac(afterSeek) ?: return "FAIL  seek relocate carried no fraction"
    if (sought <= turned) return "FAIL  seek to 0.75 landed at $sought, not past $turned"

    events.clear()
    handle.send(
        """{"cmd":"prefs","prefs":{"flow":"scrolled","theme":"dark","fontFamily":"serif","columns":"auto",""" +
            """"fontSize":120,"lineHeight":110,"margin":16,"textAlign":"justify","indent":1}}""",
    )
    val afterPrefs = nextEvent("relocate") ?: return "FAIL  the prefs command did not re-render"
    val section = afterPrefs["sectionTotal"]?.jsonPrimitive?.content

    return "PASS  commands drive the book (turn=$turned, seek=$sought, prefs re-rendered, sections=$section)"
}

fun main(args: Array<String>) {
    val host = args.getOrElse(0) { "http://localhost:26000" }
    val key = args.getOrElse(1) { "" }
    val client = createHttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }
    val api = KodexApi(client)

    val bookId = runBlocking {
        runCatching {
            api.booksList(host, key, sort = "createdDate,desc", size = 300)
                .firstOrNull { it.mediaType == "application/epub+zip" }?.id
        }.getOrNull()
    }
    if (bookId == null) {
        println("FAIL  no EPUB book on $host — nothing to render")
        kotlin.system.exitProcess(1)
    }
    println("Rendering EPUB $bookId")

    val bootConfig = """
        {"format":"epub","initialLocator":null,"initialFraction":0,
         "prefs":{"flow":"paginated","theme":"light","fontFamily":"publisher","columns":"auto",
                  "fontSize":100,"lineHeight":100,"margin":24,"textAlign":"auto","indent":null},
         "fonts":[]}
    """.trimIndent().replace("\n", "")

    var verdict: String? = null
    application {
        Window(
            onCloseRequest = { disposeWebEngine(); exitApplication() },
            title = "Kodex reader render check",
            state = rememberWindowState(width = 500.dp, height = 800.dp),
        ) {
            var ready by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("starting engine…") }
            // Opened into state from inside the composition, exactly as EbookReaderScreen does. That
            // null -> real transition is what once disposed the host session the instant it was
            // created (it re-keyed the DisposableEffect below, whose onDispose then read the *new*
            // handle), leaving the WebView to 404 on reader.html. So the harness has to reproduce the
            // lifecycle, not just the transport.
            var handle by remember { mutableStateOf<EbookHostHandle?>(null) }

            LaunchedEffect(Unit) {
                handle = EbookHost.open(
                    EbookHostSession(
                        baseUrl = host,
                        apiKey = key,
                        origin = EbookOrigin.Book(bookId),
                        bootConfigJson = bootConfig,
                        onEvent = { events.put(it) },
                    ),
                )
            }
            DisposableEffect(handle) {
                val current = handle
                onDispose { current?.dispose() }
            }

            LaunchedEffect(Unit) {
                ensureWebEngine { st ->
                    status = when (st) {
                        is WebEngineState.Preparing -> "downloading engine ${st.progress?.let { (it * 100).toInt() } ?: 0}%"
                        is WebEngineState.Failed -> "engine failed: ${st.message}"
                        WebEngineState.RestartRequired -> "restart required"
                        WebEngineState.Ready -> "engine ready"
                    }
                    if (st is WebEngineState.Ready) ready = true
                }
            }

            val openHandle = handle
            if (ready && openHandle != null) {
                val state = rememberWebViewState(openHandle.readerUrl)
                val navigator = rememberWebViewNavigator()
                DisposableEffect(state) {
                    state.webSettings.isJavaScriptEnabled = true
                    onDispose { }
                }
                WebView(state = state, modifier = Modifier.fillMaxSize(), navigator = navigator)

                LaunchedEffect(Unit) {
                    // 90s covers a cold Chromium start; the event itself normally lands in a second.
                    val readyEvent = nextEvent("ready", "error", timeoutMs = 90_000)
                    verdict = when {
                        readyEvent == null -> "FAIL  timed out waiting for the reader to render"
                        else -> {
                            val obj = readyEvent
                            if (obj["type"]?.jsonPrimitive?.content == "error") {
                                "FAIL  reader reported: ${obj["message"]?.jsonPrimitive?.content}"
                            } else {
                                val toc = runCatching { obj["toc"]!!.jsonArray }.getOrNull()
                                val labels = toc?.map { it.jsonObject["label"]?.jsonPrimitive?.content }
                                "PASS  rendered; sections=${obj["sectionTotal"]?.jsonPrimitive?.content}, toc=$labels"
                            }
                        }
                    }
                    // With the book up, exercise the other half of the bridge — the commands Kotlin
                    // sends down. A rendered-but-undrivable reader would otherwise look like a pass.
                    if (verdict?.startsWith("PASS") == true) {
                        verdict = checkCommands(openHandle)
                    }

                    // Report and decide the exit code HERE: JCEF's shutdown terminates the JVM itself,
                    // so nothing after `application {}` is guaranteed to run (and its 0 would mask a
                    // failure).
                    println(verdict)
                    if (!verdict.startsWith("PASS")) kotlin.system.exitProcess(1)
                    disposeWebEngine()
                    exitApplication()
                }
            } else {
                Text(status)
            }
        }
    }

    // Only reached if the window was closed before the reader reported in — the success path exits
    // from inside the composition above. The host session is released by the DisposableEffect.
    if (verdict == null) {
        println("FAIL  window closed before the reader reported in")
        kotlin.system.exitProcess(1)
    }
}

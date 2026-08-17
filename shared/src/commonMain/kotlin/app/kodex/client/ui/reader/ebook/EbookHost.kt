package app.kodex.client.ui.reader.ebook

import app.kodex.client.platform.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kodex_client.shared.generated.resources.Res
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.random.Random

/**
 * Where the ebook being read comes from — decides which upstream endpoints the host proxies. Kept
 * closed (rather than taking arbitrary URLs) so a leaked host token can only ever reach the one book
 * the reader has open, never the rest of the user's library.
 */
sealed interface EbookOrigin {
    /** A book in a library: EPUBs stream entry-by-entry, MOBI/FB2 come down whole. */
    data class Book(val bookId: String) : EbookOrigin

    /** A BOOK-kind source chapter, which the core exposes as an ephemeral single-chapter EPUB. */
    data class SourceChapter(val providerId: String, val chapterId: String) : EbookOrigin
}

/** One open reader's context: what to fetch, which server to fetch it from, and where events go. */
class EbookHostSession(
    val baseUrl: String,
    val apiKey: String,
    val origin: EbookOrigin,
    /** The page's boot config, serialized — see `reader.js`. */
    val bootConfigJson: String,
    /** Raw JSON the page posts back (`ready`, `relocate`, `error`). */
    val onEvent: (String) -> Unit,
) {
    /** Commands waiting for the page to collect; see [EbookHostHandle.send]. */
    internal val commands = Channel<String>(Channel.BUFFERED)
}

/** A live host registration. [readerUrl] is what the WebView loads; [dispose] unregisters it. */
class EbookHostHandle internal constructor(
    val readerUrl: String,
    private val token: String,
    private val session: EbookHostSession,
) {
    /**
     * Queues a command for the page (`{"cmd":"next"}` and friends — see `reader.js`), which is
     * long-polling for them. Deliberately not the WebView's evaluateJavaScript: that is a different
     * implementation on each platform and on some was missing outright, so the reader would have
     * rendered but refused to turn a page. Routing commands over the same loopback
     * connection the page already uses for everything else makes one transport to get right.
     */
    fun send(command: String) {
        session.commands.trySend(command)
    }

    fun dispose() {
        session.commands.close()
        EbookHost.release(token)
    }
}

/**
 * A loopback HTTP server that backs the ebook reader's WebView.
 *
 * foliate-js is a set of ES modules and expects to `fetch` book resources, neither of which works
 * from a `file://` or `data:` document — modules are blocked there and every request would be
 * cross-origin. Serving the engine, the host page and the book's bytes from one real
 * `http://127.0.0.1` origin makes the WebView behave like the browser the web UI already runs in, so
 * `reader.js` stays close to a literal port of `epubLoader.ts`. It also keeps the `X-API-Key` on the
 * Kotlin side of the boundary rather than inside a page that renders untrusted book markup.
 *
 * One engine is shared by every reader that opens; each gets a random path token, so a chapter swap
 * re-registers instead of restarting the server, and requests carrying a stale token 404. The token
 * matters: on Android any other app can reach this port, and it is the only thing standing between
 * them and the proxied book.
 */
object EbookHost {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, EbookHostSession>()

    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = 0

    /** Bare client: raw bytes only, no JSON negotiation and no `expectSuccess` (upstream 404s are data). */
    private val upstream by lazy { createHttpClient { } }

    /** Registers [session] and returns the URL to load. The engine starts on first use. */
    suspend fun open(session: EbookHostSession): EbookHostHandle = mutex.withLock {
        if (server == null) {
            val started = embeddedServer(CIO, host = LOOPBACK, port = 0) { routing { routes() } }
            started.start(wait = false)
            port = started.engine.resolvedConnectors().firstOrNull()?.port
                ?: error("Reader host failed to bind a loopback port")
            server = started
        }
        val token = randomToken()
        sessions[token] = session
        EbookHostHandle("http://$LOOPBACK:$port/$token/reader.html", token, session)
    }

    internal fun release(token: String) {
        sessions.remove(token)
    }

    /** The session this request is scoped to, or null when the token is unknown/expired. */
    private fun RoutingContext.session(): EbookHostSession? =
        call.parameters["token"]?.let { sessions[it] }

    private suspend fun RoutingContext.notFound() =
        call.respondText("", status = HttpStatusCode.NotFound)

    /**
     * Every route is scoped by the caller's token. The engine keeps running once started: readers
     * open and close constantly (a chapter swap remounts the whole screen) and a loopback listener
     * with no live sessions is inert, so cycling it each time would only add latency and bind churn.
     */
    @OptIn(ExperimentalResourceApi::class)
    private fun io.ktor.server.routing.Route.routes() {
        get("/{token}/reader.html") {
            val ctx = session() ?: return@get notFound()
            call.respondText(readerHtml(ctx.bootConfigJson), ContentType.Text.Html)
        }

        // The vendored foliate-js engine, served as real modules so `import` inside it resolves.
        get("/{token}/foliate/{file}") {
            session() ?: return@get notFound()
            val file = call.parameters["file"].orEmpty()
            // Path-traversal guard: these are flat file names, never paths.
            if (!file.endsWith(".js") || '/' in file || '\\' in file || ".." in file) return@get notFound()
            val bytes = runCatching { Res.readBytes("files/foliate/$file") }.getOrNull() ?: return@get notFound()
            call.respondBytes(bytes, ContentType.Text.JavaScript)
        }

        get("/{token}/reader.js") {
            session() ?: return@get notFound()
            call.respondBytes(Res.readBytes("files/reader/reader.js"), ContentType.Text.JavaScript)
        }

        // ── Book bytes, proxied with the API key attached ────────────────────────────────────────
        get("/{token}/manifest") {
            val ctx = session() ?: return@get notFound()
            proxy(ctx, ctx.manifestUrl(), fallback = ContentType.Application.Json)
        }
        get("/{token}/resource") {
            val ctx = session() ?: return@get notFound()
            val href = call.request.queryParameters["href"] ?: return@get notFound()
            proxy(ctx, ctx.resourceUrl(href))
        }
        get("/{token}/file") {
            val ctx = session() ?: return@get notFound()
            proxy(ctx, ctx.fileUrl() ?: return@get notFound())
        }
        get("/{token}/font/{id}") {
            val ctx = session() ?: return@get notFound()
            val id = call.parameters["id"] ?: return@get notFound()
            proxy(ctx, "${ctx.baseUrl}/api/v1/fonts/${id.encodeURLQueryComponent()}/file")
        }

        // The page's only way back into Kotlin: `ready`, `relocate` and `error`, posted as JSON.
        post("/{token}/event") {
            val ctx = session() ?: return@post notFound()
            ctx.onEvent(call.receiveText())
            call.respondText("", ContentType.Text.Plain)
        }

        // …and Kotlin's only way in. The page keeps one of these in flight; it's held open until a
        // command shows up (or the poll ages out), so a page turn is delivered immediately rather
        // than on the next tick of a busy loop.
        get("/{token}/commands") {
            val ctx = session() ?: return@get notFound()
            val first = withTimeoutOrNull(POLL_TIMEOUT_MS) {
                runCatching { ctx.commands.receive() }.getOrNull()
            }
            val batch = buildList {
                if (first != null) {
                    add(first)
                    // Coalesce anything queued behind it — a held slider emits a burst of seeks.
                    while (true) add(ctx.commands.tryReceive().getOrNull() ?: break)
                }
            }
            call.respondText(batch.joinToString(",", "[", "]"), ContentType.Application.Json)
        }
    }

    /** Fetches [url] from the Kodex server with the key attached and replays it to the WebView. */
    private suspend fun RoutingContext.proxy(
        ctx: EbookHostSession,
        url: String,
        fallback: ContentType = ContentType.Application.OctetStream,
    ) {
        val response = runCatching {
            upstream.get(url) { header(HEADER_API_KEY, ctx.apiKey) }
        }.getOrElse { return call.respondText("", status = HttpStatusCode.BadGateway) }

        if (!response.status.isSuccess()) {
            return call.respondText("", status = HttpStatusCode.NotFound)
        }
        call.respondBytes(response.bodyAsBytes(), response.contentType() ?: fallback)
    }

    private fun randomToken(): String = buildString {
        repeat(24) { append(TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)]) }
    }

    private const val LOOPBACK = "127.0.0.1"
    private const val HEADER_API_KEY = "X-API-Key"
    /** How long a command poll is held open before returning empty and being re-issued. */
    private const val POLL_TIMEOUT_MS = 25_000L
    private const val TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

private fun EbookHostSession.manifestUrl(): String = when (origin) {
    is EbookOrigin.Book -> "$baseUrl/api/v1/books/${origin.bookId}/manifest"
    is EbookOrigin.SourceChapter ->
        "$baseUrl/api/v1/content-sources/${origin.providerId.encodeURLQueryComponent()}/chapter-manifest" +
            "?chapterId=${origin.chapterId.encodeURLParameter()}"
}

private fun EbookHostSession.resourceUrl(href: String): String = when (origin) {
    is EbookOrigin.Book ->
        "$baseUrl/api/v1/books/${origin.bookId}/resource?href=${href.encodeURLParameter()}"
    is EbookOrigin.SourceChapter ->
        "$baseUrl/api/v1/content-sources/${origin.providerId.encodeURLQueryComponent()}/chapter-resource" +
            "?chapterId=${origin.chapterId.encodeURLParameter()}&href=${href.encodeURLParameter()}"
}

/** Whole-file reads (MOBI/KF8/FB2) only exist for library books; source chapters are always EPUB. */
private fun EbookHostSession.fileUrl(): String? = when (origin) {
    is EbookOrigin.Book -> "$baseUrl/api/v1/books/${origin.bookId}/file"
    is EbookOrigin.SourceChapter -> null
}

/**
 * The host document. Deliberately minimal — it only parks the boot config on `window` and hands over
 * to `reader.js`; everything else (loading the book, pagination, styling) is foliate's.
 */
private fun readerHtml(bootConfigJson: String): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<style>
  html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; background: #ffffff; }
  #view { position: absolute; inset: 0; }
</style>
</head>
<body>
<div id="view"></div>
<script>window.KDX_CONFIG = $bootConfigJson;</script>
<script type="module" src="./reader.js"></script>
</body>
</html>
""".trimIndent()

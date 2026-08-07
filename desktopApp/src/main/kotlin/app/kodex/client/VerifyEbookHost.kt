package app.kodex.client

import app.kodex.client.network.KodexApi
import app.kodex.client.platform.createHttpClient
import app.kodex.client.ui.reader.ebook.EbookHost
import app.kodex.client.ui.reader.ebook.EbookHostSession
import app.kodex.client.ui.reader.ebook.EbookOrigin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Throwaway harness for the reader's loopback host: starts [EbookHost] for real, then fetches every
 * route the WebView will. The asset routes are checked unconditionally; the proxy routes need a live
 * server plus an EPUB book id, so they're skipped without one.
 *
 * Run: ./gradlew :composeApp:verifyEbookHost   (host/key read from kodex/.env.test, book id optional)
 */
fun main(args: Array<String>) = runBlocking {
    val host = args.getOrElse(0) { "http://localhost:26000" }
    val key = args.getOrElse(1) { "" }
    val client = createHttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    // Find an EPUB to exercise the proxy with, so this tests the real thing rather than a 404 path.
    val api = KodexApi(client)
    val bookId = runCatching {
        api.booksList(host, key, sort = "createdDate,desc", size = 300)
            .firstOrNull { it.mediaType == "application/epub+zip" }?.id
    }.getOrNull()
    println(if (bookId != null) "Using EPUB book $bookId" else "No EPUB found — proxy routes will be skipped")

    val handle = EbookHost.open(
        EbookHostSession(
            baseUrl = host,
            apiKey = key,
            origin = EbookOrigin.Book(bookId ?: "missing"),
            bootConfigJson = """{"format":"epub"}""",
            onEvent = { println("  event: ${it.take(120)}") },
        ),
    )
    val base = handle.readerUrl.removeSuffix("/reader.html")
    println("Host at $base")

    var pass = 0
    var fail = 0
    suspend fun check(label: String, url: String, expect: (String) -> Boolean) {
        try {
            val body = client.get(url).bodyAsText()
            if (expect(body)) {
                println("PASS  $label — ${body.length} bytes")
                pass++
            } else {
                println("FAIL  $label — unexpected body: ${body.take(160)}")
                fail++
            }
        } catch (e: Throwable) {
            println("FAIL  $label — ${e::class.simpleName}: ${e.message}")
            fail++
        }
    }

    check("reader.html", "$base/reader.html") { "KDX_CONFIG" in it && "reader.js" in it }
    check("reader.js", "$base/reader.js") { "foliate/view.js" in it }
    check("foliate/view.js", "$base/foliate/view.js") { it.isNotEmpty() }
    check("foliate/epub.js", "$base/foliate/epub.js") { it.isNotEmpty() }
    check("foliate/paginator.js", "$base/foliate/paginator.js") { it.isNotEmpty() }

    // Traversal + unknown-token guards: both must refuse rather than serve.
    check("traversal blocked", "$base/foliate/..%2Freader.js") { it.isEmpty() }
    check("bad token blocked", "${base.substringBeforeLast('/')}/deadbeefdeadbeefdeadbeef/reader.html") { it.isEmpty() }

    if (bookId != null) {
        check("manifest proxied", "$base/manifest") { "entries" in it }
        val manifest = runCatching { api.bookManifest(host, key, bookId) }.getOrNull()
        val entry = manifest?.entries?.firstOrNull { it.name.endsWith(".opf") || it.name.endsWith(".xhtml") }
        if (entry != null) {
            check("resource proxied (${entry.name})", "$base/resource?href=${entry.name}") { it.isNotEmpty() }
        }
        check("file proxied", "$base/file") { it.isNotEmpty() }
    }

    handle.dispose()
    check("released token 404s", "$base/reader.html") { it.isEmpty() }

    // ── The other origin: a BOOK-kind source chapter, served as an ephemeral single-chapter EPUB ──
    // Only runs when a novel source plugin is installed and has a reachable chapter.
    val novel = runCatching { api.contentSources(host, key).firstOrNull { it.kind == "BOOK" } }.getOrNull()
    if (novel == null) {
        println("SKIP  source chapter — no BOOK-kind content source installed")
    } else {
        val chapterId = runCatching {
            val series = api.sourceFeed(host, key, novel.id, feed = "popular", page = 1).items.firstOrNull()
                ?: return@runCatching null
            api.sourceChapters(host, key, novel.id, series.externalId).firstOrNull()?.externalId
        }.getOrNull()

        if (chapterId == null) {
            println("SKIP  source chapter — ${novel.id} returned no browsable chapter")
        } else {
            val sourceHandle = EbookHost.open(
                EbookHostSession(
                    baseUrl = host,
                    apiKey = key,
                    origin = EbookOrigin.SourceChapter(novel.id, chapterId),
                    bootConfigJson = """{"format":"epub"}""",
                    onEvent = { },
                ),
            )
            val sourceBase = sourceHandle.readerUrl.removeSuffix("/reader.html")
            println("Using ${novel.id} chapter $chapterId")
            check("source reader.html", "$sourceBase/reader.html") { "KDX_CONFIG" in it }
            check("source manifest proxied", "$sourceBase/manifest") { "entries" in it }
            val sourceManifest = runCatching { api.sourceChapterManifest(host, key, novel.id, chapterId) }.getOrNull()
            val sourceEntry = sourceManifest?.entries?.firstOrNull { it.name.endsWith(".opf") || it.name.endsWith(".xhtml") }
            if (sourceEntry != null) {
                check("source resource proxied (${sourceEntry.name})", "$sourceBase/resource?href=${sourceEntry.name}") { it.isNotEmpty() }
            } else {
                println("FAIL  source manifest carried no readable entry")
                fail++
            }
            sourceHandle.dispose()
        }
    }

    println("\n$pass passed, $fail failed")
    client.close()
    if (fail > 0) kotlin.system.exitProcess(1)
}

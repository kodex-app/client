package app.kodex.client

import app.kodex.client.network.KodexApi
import app.kodex.client.platform.createHttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Throwaway harness: runs the real KodexApi (Ktor client + kotlinx.serialization DTOs) against a live
 * server so we verify the client actually deserializes real responses — not just that it compiles.
 * Run: ./gradlew :composeApp:verifyApi   (host/key read from kodex/.env.test)
 */
fun main(args: Array<String>) = runBlocking {
    val host = args.getOrElse(0) { "http://localhost:26000" }
    val key = args.getOrElse(1) { "" }
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val client = createHttpClient {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
    }
    val api = KodexApi(client)

    var pass = 0
    var fail = 0
    suspend fun check(label: String, block: suspend () -> String) {
        try {
            println("PASS  $label — ${block()}")
            pass++
        } catch (e: Throwable) {
            println("FAIL  $label — ${e::class.simpleName}: ${e.message}")
            fail++
        }
    }

    check("getMe") { api.getMe(host, key).let { "${it.email} ${it.roles}" } }
    check("libraries") { api.libraries(host, key).let { "${it.size} libs: ${it.take(4).map { l -> "${l.name}/${l.type}/${l.mediaKind}" }}" } }
    check("recentSeries") { api.recentSeries(host, key).let { "${it.size}, first=${it.firstOrNull()?.title}" } }
    check("recentlyUpdatedSeries") { api.recentlyUpdatedSeries(host, key).size.toString() }
    check("recentBooks") { api.recentBooks(host, key).let { "${it.size}, first=${it.firstOrNull()?.title}" } }
    check("keepReading") { api.keepReading(host, key).size.toString() + " in progress" }
    check("searchSeries('a')") { api.searchSeries(host, key, "a").size.toString() + " hits" }
    check("searchBooks('a')") { api.searchBooks(host, key, "a").size.toString() + " hits" }

    // A known WEB series with downloaded chapters.
    val sid = "0R0XWA0HJ54D8"
    check("seriesDetail($sid)") {
        api.seriesDetail(host, key, sid).let { "web=${it.isWeb} title=${it.title} status=${it.status}" }
    }
    check("seriesChapters($sid)") {
        val ch = api.seriesChapters(host, key, sid)
        "${ch.size} chapters, ${ch.count { c -> c.downloaded }} downloaded, ${ch.count { c -> c.read }} read"
    }
    check("seriesBooks($sid)") { api.seriesBooks(host, key, sid).size.toString() + " books" }

    check("book") {
        api.book(host, key, "0R1Q3XDWXHEWE").let { "${it.title} pages=${it.pageCount} media=${it.mediaType} ready=${it.isReady}" }
    }

    check("contentSources") { api.contentSources(host, key).let { "${it.size} sources, first=${it.firstOrNull()?.displayName}" } }
    check("sourceFeed(popular)") {
        val src = api.contentSources(host, key).first()
        val page = api.sourceFeed(host, key, src.id, "popular", 1)
        "${page.items.size} items, hasNext=${page.hasNextPage}, first=${page.items.firstOrNull()?.title}"
    }

    // Read-from-source while browsing: drill into a live COMIC source the way the Browse screens do —
    // feed → series details → chapter list → per-chapter progress + page count (what the reader needs).
    val comicSource = runCatching {
        api.contentSources(host, key).firstOrNull { it.kind == "COMIC" }
    }.getOrNull()
    if (comicSource != null) {
        val pid = comicSource.id
        val item = runCatching { api.sourceFeed(host, key, pid, "popular", 1).items.firstOrNull() }.getOrNull()
        if (item != null) {
            val ext = item.externalId
            check("sourceSeries($pid)") {
                api.sourceSeries(host, key, pid, ext).let { "${it.title} status=${it.status} genres=${it.genres.size}" }
            }
            val chapters = runCatching { api.sourceChapters(host, key, pid, ext) }.getOrNull().orEmpty()
            check("sourceChapters($pid)") {
                "${chapters.size} chapters, ${chapters.count { c -> c.volume != null }} with a volume, " +
                    "first=${chapters.firstOrNull()?.name}"
            }
            check("sourceSeriesProgress($pid)") {
                api.sourceSeriesProgress(host, key, pid, ext).let { "${it.size} chapters with saved progress" }
            }
            val first = chapters.firstOrNull()?.externalId
            if (first != null) {
                check("sourceChapterPageCount(browse)") {
                    "${api.sourceChapterPageCount(host, key, pid, first)} pages (0 ⇒ source can't fetch right now)"
                }
                check("sourceProgress(browse)") {
                    api.sourceProgress(host, key, pid, first)?.toString() ?: "null (no saved progress)"
                }
            }
        }
    }

    // Streamed reader endpoints (page count 0 today = the source can't fetch pages, not a client bug).
    val detail = runCatching { api.seriesDetail(host, key, sid) }.getOrNull()
    val prov = detail?.sourceProviderId
    val cid = runCatching { api.seriesChapters(host, key, sid).firstOrNull()?.chapterId }.getOrNull()
    if (prov != null && cid != null) {
        check("sourceChapterPageCount") { "${api.sourceChapterPageCount(host, key, prov, cid)} pages (0 ⇒ source can't fetch right now)" }
        check("sourceProgress") { api.sourceProgress(host, key, prov, cid)?.toString() ?: "null (no saved progress)" }
    }

    println("\n=== $pass passed, $fail failed ===")
    client.close()
}

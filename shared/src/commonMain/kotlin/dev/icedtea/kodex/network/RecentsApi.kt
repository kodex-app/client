package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post

// Updates, history and the download queue. Extensions on [KodexApi]; see that file for the auth model.

// ── Recents: Updates + History ────────────────────────────────────────────────────────────────

/** One page of the Mihon-style Updates feed (new source chapters for followed WEB series). */
suspend fun KodexApi.updates(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<UpdateDto> =
    client.get("$baseUrl/api/v1/updates") {
        header(HEADER_API_KEY, apiKey)
        parameter("page", page)
        parameter("size", size)
    }.body()

/** One page of reading history (downloaded-book + streamed-source progress merged, newest first). */
suspend fun KodexApi.history(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<HistoryEntryDto> =
    client.get("$baseUrl/api/v1/history") {
        header(HEADER_API_KEY, apiKey)
        parameter("page", page)
        parameter("size", size)
    }.body()

/** Clears history within an optional `[from, to]` ISO-8601 window (omit both to clear everything). */
suspend fun KodexApi.clearHistory(baseUrl: String, apiKey: String, from: String? = null, to: String? = null) {
    client.delete("$baseUrl/api/v1/history") {
        header(HEADER_API_KEY, apiKey)
        if (from != null) parameter("from", from)
        if (to != null) parameter("to", to)
    }
}

/**
 * Remove one history entry. This deletes the progress record behind it, so the saved position and
 * read flag for that book/chapter go with it — worth spelling out before calling.
 */
suspend fun KodexApi.deleteHistoryEntry(baseUrl: String, apiKey: String, entryId: String) {
    client.delete("$baseUrl/api/v1/history/$entryId") { header(HEADER_API_KEY, apiKey) }
}

// ── Downloads ─────────────────────────────────────────────────────────────────────────────────

suspend fun KodexApi.downloads(baseUrl: String, apiKey: String, page: Int, size: Int = PAGE_SIZE): PageResponse<DownloadJobDto> =
    client.get("$baseUrl/api/v1/downloads") {
        header(HEADER_API_KEY, apiKey)
        parameter("page", page)
        parameter("size", size)
    }.body()

/** Per-job action: [action] is one of "cancel", "pause", "resume", "retry" (all 204). */
suspend fun KodexApi.downloadAction(baseUrl: String, apiKey: String, jobId: String, action: String) {
    client.post("$baseUrl/api/v1/downloads/$jobId/$action") {
        header(HEADER_API_KEY, apiKey)
    }
}

suspend fun KodexApi.cancelAllDownloads(baseUrl: String, apiKey: String) {
    client.post("$baseUrl/api/v1/downloads/cancel-all") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.clearFinishedDownloads(baseUrl: String, apiKey: String): Int =
    client.post("$baseUrl/api/v1/downloads/clear") { header(HEADER_API_KEY, apiKey) }.body<ClearedDto>().cleared

suspend fun KodexApi.retryFailedDownloads(baseUrl: String, apiKey: String): Int =
    client.post("$baseUrl/api/v1/downloads/retry-failed") { header(HEADER_API_KEY, apiKey) }.body<RetriedDto>().retried

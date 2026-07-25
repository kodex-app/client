package app.kodex.client.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A domain event streamed from the server over SSE. [name] is the event type; [data] its JSON payload. */
data class ServerEvent(val name: String, val data: JsonObject) {
    companion object {
        // Names mirror dev.kodex.event.DomainEvents.
        const val LIBRARY_SCAN_STARTED = "LibraryScanStarted"
        const val LIBRARY_SCAN_COMPLETED = "LibraryScanCompleted"
        const val SERIES_ADDED = "SeriesAdded"
        const val BOOK_ADDED = "BookAdded"
        const val BOOK_ANALYZED = "BookAnalyzed"
        const val TASK_STATUS_CHANGED = "TaskStatusChanged"
        const val DOWNLOAD_STATUS_CHANGED = "DownloadStatusChanged"
    }
}

/**
 * Live server events over Server-Sent Events (`GET /api/v1/sse/events`). Owns one SSE connection to
 * the active server and re-broadcasts each event on a shared [events] flow that screens observe to
 * auto-refresh (downloads, updates, library). Reconnects with a fixed backoff; [connect] switches
 * servers, [disconnect] tears down. The Phase-0 real-time enabler behind the poll-free screens.
 */
class EventBus(
    private val client: HttpClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events

    private var job: Job? = null

    fun connect(baseUrl: String, apiKey: String) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    client.sse(
                        urlString = "$baseUrl/api/v1/sse/events",
                        request = { header("X-API-Key", apiKey) },
                    ) {
                        incoming.collect { event ->
                            val name = event.event ?: return@collect
                            val payload = event.data?.takeIf { it.isNotBlank() }
                                ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                                ?: JsonObject(emptyMap())
                            _events.emit(ServerEvent(name, payload))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection dropped (server restart, network blip) — fall through and retry.
                }
                if (isActive) delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 3000L
    }
}

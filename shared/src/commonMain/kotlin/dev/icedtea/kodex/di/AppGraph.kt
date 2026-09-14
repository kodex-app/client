package dev.icedtea.kodex.di

import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.AppSettings
import dev.icedtea.kodex.data.ServerStore
import dev.icedtea.kodex.data.SourcePrefsStore
import dev.icedtea.kodex.network.EventBus
import dev.icedtea.kodex.network.HEADER_API_KEY
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.platform.createHttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Manual composition root. One instance is created at the top of the Compose tree and remembered
 * for the app's lifetime — no DI framework needed yet. Everything platform-specific is reached
 * through `expect`/`actual` (the HTTP engine, prefs, clock), so this graph is fully common code.
 */
class AppGraph {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Server sends JSON null for optional string fields (e.g. author/artist); coerce a null to the
        // property's default instead of throwing "Unexpected 'null' value instead of string literal".
        coerceInputValues = true
    }

    /**
     * API keys the server has answered 401 to. Fed by the HTTP client below and drained into
     * [SessionManager.keyRejected], which drops the app to the login screen when it was the active
     * connection's key. A flow rather than a direct call only because the client is built before
     * the session exists; buffered so a burst of 401s from one screen's parallel requests never
     * blocks the response pipeline.
     */
    private val rejectedApiKeys = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private val httpClient = createHttpClient {
        expectSuccess = true
        HttpResponseValidator {
            // Runs after expectSuccess has turned the 401 into a ClientRequestException, with the
            // outgoing headers still attached — so only key-authenticated calls count. A failed
            // Basic-auth sign-in is also a 401 but carries no key, and must not evict anyone.
            handleResponseExceptionWithRequest { cause, request ->
                if (cause is ClientRequestException && cause.response.status == HttpStatusCode.Unauthorized) {
                    request.headers[HEADER_API_KEY]?.let(rejectedApiKeys::tryEmit)
                }
            }
        }
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    // Dedicated client for the long-lived SSE stream: SSE installed, and NO request timeout (a
    // 30s cap would abort the persistent event stream). Connect timeout still guards the handshake.
    private val sseHttpClient = createHttpClient {
        install(SSE)
        install(HttpTimeout) { connectTimeoutMillis = 15_000 }
    }

    /** Separate bare client for Coil image loading — no JSON negotiation, no expectSuccess. */
    val imageHttpClient = createHttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    private val store = ServerStore()
    val api = KodexApi(httpClient)

    /** Device-local appearance prefs (theme mode, palette, AMOLED, dynamic colour). */
    val appSettings = AppSettings()

    val session: SessionManager = SessionManager(store, api).apply { bootstrap() }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Live server events (SSE), (re)connected to whichever server is active. */
    val eventBus = EventBus(sseHttpClient, appScope)

    /** Per-user Browse favourites/recents, loaded from the active server's user settings. */
    val sourcePrefs = SourcePrefsStore(api, appScope)

    init {
        appScope.launch { rejectedApiKeys.collect(session::keyRejected) }
        appScope.launch {
            session.activeServer.collect { server ->
                if (server != null) {
                    eventBus.connect(server.baseUrl, server.apiKey)
                    sourcePrefs.bind(server.baseUrl, server.apiKey)
                } else {
                    eventBus.disconnect()
                    sourcePrefs.clear()
                }
            }
        }
    }
}

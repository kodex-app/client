package app.kodex.client.di

import app.kodex.client.auth.SessionManager
import app.kodex.client.data.ServerStore
import app.kodex.client.network.KodexApi
import app.kodex.client.platform.createHttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
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
    }

    private val httpClient = createHttpClient {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    private val store = ServerStore()
    private val api = KodexApi(httpClient)

    val session: SessionManager = SessionManager(store, api).apply { bootstrap() }
}

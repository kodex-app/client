package dev.icedtea.kodex.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** Wall-clock millis — used only to order saved servers by "most recently used". */
expect fun nowMillis(): Long

/**
 * Builds an [HttpClient] on the platform's native engine (OkHttp on Android, Darwin on iOS). Shared
 * config (JSON, logging) is applied by the caller via [block].
 */
expect fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

package dev.icedtea.kodex.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

// Mihon extensions, LNReader plugins, their repositories, metadata providers, provider config, and the server-side browser. Extensions on [KodexApi]; see that file for the auth model.

// ── Mihon extensions (Keiyoushi JAR builds run by the server's kodex-mihon runtime) ───────────

suspend fun KodexApi.mihonAvailable(baseUrl: String, apiKey: String, refresh: Boolean = false): List<MihonAvailableDto> =
    client.get("$baseUrl/api/v1/mihon/extensions/available") {
        header(HEADER_API_KEY, apiKey)
        if (refresh) parameter("refresh", "true")
    }.body()

suspend fun KodexApi.mihonInstalled(baseUrl: String, apiKey: String): List<MihonInstalledDto> =
    client.get("$baseUrl/api/v1/mihon/extensions") { header(HEADER_API_KEY, apiKey) }.body()

/** Installs, or updates to the repository's version. */
suspend fun KodexApi.mihonInstall(baseUrl: String, apiKey: String, packageName: String): MihonInstalledDto =
    client.post("$baseUrl/api/v1/mihon/extensions/${packageName.encodeURLPathPart()}") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.mihonUninstall(baseUrl: String, apiKey: String, packageName: String) {
    client.delete("$baseUrl/api/v1/mihon/extensions/${packageName.encodeURLPathPart()}") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.mihonUpdateStatus(baseUrl: String, apiKey: String): MihonUpdateStatusDto =
    client.get("$baseUrl/api/v1/mihon/extensions/update-status") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.mihonCheckUpdates(baseUrl: String, apiKey: String): MihonUpdateStatusDto =
    client.post("$baseUrl/api/v1/mihon/extensions/check-updates") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.mihonUpdateAll(baseUrl: String, apiKey: String): List<MihonUpdateOutcome> =
    client.post("$baseUrl/api/v1/mihon/extensions/update-all") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.mihonRepositories(baseUrl: String, apiKey: String, refresh: Boolean = false): List<MihonRepositoryDto> =
    client.get("$baseUrl/api/v1/mihon/repos") {
        header(HEADER_API_KEY, apiKey)
        if (refresh) parameter("refresh", "true")
    }.body()

/** The server fetches the index before storing the URL, so a typo or an APK-only repo is reported at once. */
suspend fun KodexApi.mihonAddRepository(baseUrl: String, apiKey: String, url: String): MihonRepositoryDto =
    client.post("$baseUrl/api/v1/mihon/repos") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MihonAddRepositoryRequest(url))
    }.body()

/** Changes a repository's URL in place; the server fetches the new index before storing it. */
suspend fun KodexApi.mihonUpdateRepository(baseUrl: String, apiKey: String, url: String, newUrl: String): MihonRepositoryDto =
    client.put("$baseUrl/api/v1/mihon/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
        contentType(ContentType.Application.Json)
        setBody(MihonAddRepositoryRequest(newUrl))
    }.body()

/** Switches a repository on/off without forgetting it. */
suspend fun KodexApi.mihonSetRepositoryEnabled(baseUrl: String, apiKey: String, url: String, enabled: Boolean): MihonRepositoryDto =
    client.patch("$baseUrl/api/v1/mihon/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
        contentType(ContentType.Application.Json)
        setBody(RepositoryEnabledRequest(enabled))
    }.body()

/** The full new order — every configured URL exactly once. */
suspend fun KodexApi.mihonReorderRepositories(baseUrl: String, apiKey: String, urls: List<String>) {
    client.put("$baseUrl/api/v1/mihon/repos/order") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(RepositoryOrderRequest(urls))
    }
}

suspend fun KodexApi.mihonRemoveRepository(baseUrl: String, apiKey: String, url: String) {
    client.delete("$baseUrl/api/v1/mihon/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
    }
}

// ── Interactive browser pages (admin): a page in the server's browser, driven from here ───────

suspend fun KodexApi.browserOpen(baseUrl: String, apiKey: String, url: String?, sourceId: String?): MihonBrowserSessionDto =
    client.post("$baseUrl/api/v1/mihon/browser/sessions") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MihonBrowserOpenRequest(url, sourceId))
    }.body()

suspend fun KodexApi.browserSession(baseUrl: String, apiKey: String, id: String): MihonBrowserSessionDto =
    client.get("$baseUrl/api/v1/mihon/browser/sessions/$id") { header(HEADER_API_KEY, apiKey) }.body()

/**
 * The newest JPEG frame newer than [since], or null when none arrived within the server's wait
 * (204). Returns the bytes with the frame sequence from `X-Frame-Seq`.
 */
suspend fun KodexApi.browserFrame(baseUrl: String, apiKey: String, id: String, since: Long): Pair<ByteArray, Long>? {
    val response = client.get("$baseUrl/api/v1/mihon/browser/sessions/$id/frame") {
        header(HEADER_API_KEY, apiKey)
        parameter("since", since)
    }
    if (response.status == HttpStatusCode.NoContent) return null
    val seq = response.headers["X-Frame-Seq"]?.toLongOrNull() ?: since
    return response.body<ByteArray>() to seq
}

suspend fun KodexApi.browserNavigate(baseUrl: String, apiKey: String, id: String, url: String): MihonBrowserSessionDto =
    client.post("$baseUrl/api/v1/mihon/browser/sessions/$id/navigate") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MihonBrowserNavigateRequest(url))
    }.body()

/** [action] is "back", "reload" or "save-cookies". */
suspend fun KodexApi.browserAction(baseUrl: String, apiKey: String, id: String, action: String): MihonBrowserSessionDto =
    client.post("$baseUrl/api/v1/mihon/browser/sessions/$id/$action") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.browserMouse(baseUrl: String, apiKey: String, id: String, event: MihonBrowserMouseRequest) {
    client.post("$baseUrl/api/v1/mihon/browser/sessions/$id/mouse") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(event)
    }
}

suspend fun KodexApi.browserText(baseUrl: String, apiKey: String, id: String, text: String) {
    client.post("$baseUrl/api/v1/mihon/browser/sessions/$id/text") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MihonBrowserTextRequest(text))
    }
}

/** A DOM key name: Enter, Backspace, Tab, Escape, Delete, Arrow*, Home, End, PageUp, PageDown. */
suspend fun KodexApi.browserKey(baseUrl: String, apiKey: String, id: String, key: String) {
    client.post("$baseUrl/api/v1/mihon/browser/sessions/$id/key") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(MihonBrowserKeyRequest(key))
    }
}

suspend fun KodexApi.browserClose(baseUrl: String, apiKey: String, id: String) {
    client.delete("$baseUrl/api/v1/mihon/browser/sessions/$id") { header(HEADER_API_KEY, apiKey) }
}

// ── Metadata providers (built into the server) ────────────────────────────────────────────────

suspend fun KodexApi.metadataProviders(baseUrl: String, apiKey: String): List<MetadataProviderDto> =
    client.get("$baseUrl/api/v1/metadata-providers") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.browserStatus(baseUrl: String, apiKey: String): BrowserStatusDto =
    client.get("$baseUrl/api/v1/server/network/browser") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.stopBrowser(baseUrl: String, apiKey: String): BrowserStatusDto =
    client.post("$baseUrl/api/v1/server/network/browser/stop") { header(HEADER_API_KEY, apiKey) }.body()

// ── LNReader plugins ──────────────────────────────────────────────────────────────────────────

suspend fun KodexApi.lnreaderAvailable(baseUrl: String, apiKey: String, refresh: Boolean = false): List<LnReaderAvailableDto> =
    client.get("$baseUrl/api/v1/lnreader/plugins/available") {
        header(HEADER_API_KEY, apiKey)
        if (refresh) parameter("refresh", true)
    }.body()

suspend fun KodexApi.lnreaderInstalled(baseUrl: String, apiKey: String): List<LnReaderInstalledDto> =
    client.get("$baseUrl/api/v1/lnreader/plugins") { header(HEADER_API_KEY, apiKey) }.body()

/** Installs, or updates to the repository's version. */
suspend fun KodexApi.lnreaderInstall(baseUrl: String, apiKey: String, id: String): LnReaderInstalledDto =
    client.post("$baseUrl/api/v1/lnreader/plugins/${id.encodeURLPathPart()}") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.lnreaderUninstall(baseUrl: String, apiKey: String, id: String) {
    client.delete("$baseUrl/api/v1/lnreader/plugins/${id.encodeURLPathPart()}") { header(HEADER_API_KEY, apiKey) }
}

suspend fun KodexApi.lnreaderUpdateStatus(baseUrl: String, apiKey: String): LnReaderUpdateStatusDto =
    client.get("$baseUrl/api/v1/lnreader/plugins/update-status") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.lnreaderCheckUpdates(baseUrl: String, apiKey: String): LnReaderUpdateStatusDto =
    client.post("$baseUrl/api/v1/lnreader/plugins/check-updates") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.lnreaderUpdateAll(baseUrl: String, apiKey: String): List<LnReaderUpdateOutcome> =
    client.post("$baseUrl/api/v1/lnreader/plugins/update-all") { header(HEADER_API_KEY, apiKey) }.body()

suspend fun KodexApi.lnreaderRepositories(baseUrl: String, apiKey: String, refresh: Boolean = false): List<LnReaderRepositoryDto> =
    client.get("$baseUrl/api/v1/lnreader/repos") {
        header(HEADER_API_KEY, apiKey)
        if (refresh) parameter("refresh", true)
    }.body()

/** Adds a repository by manifest URL; the server fetches it first and rejects what it can't read. */
suspend fun KodexApi.lnreaderAddRepository(baseUrl: String, apiKey: String, url: String): LnReaderRepositoryDto =
    client.post("$baseUrl/api/v1/lnreader/repos") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(LnReaderAddRepositoryRequest(url))
    }.body()

/** Changes a repository's URL in place (its merge priority stays); the server fetches the new manifest first. */
suspend fun KodexApi.lnreaderUpdateRepository(baseUrl: String, apiKey: String, url: String, newUrl: String): LnReaderRepositoryDto =
    client.put("$baseUrl/api/v1/lnreader/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
        contentType(ContentType.Application.Json)
        setBody(LnReaderAddRepositoryRequest(newUrl))
    }.body()

/** Switches a repository on/off without forgetting it (its merge position is kept). */
suspend fun KodexApi.lnreaderSetRepositoryEnabled(baseUrl: String, apiKey: String, url: String, enabled: Boolean): LnReaderRepositoryDto =
    client.patch("$baseUrl/api/v1/lnreader/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
        contentType(ContentType.Application.Json)
        setBody(RepositoryEnabledRequest(enabled))
    }.body()

/** The full new order — every configured URL exactly once; later entries win on a shared plugin id. */
suspend fun KodexApi.lnreaderReorderRepositories(baseUrl: String, apiKey: String, urls: List<String>) {
    client.put("$baseUrl/api/v1/lnreader/repos/order") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(RepositoryOrderRequest(urls))
    }
}

suspend fun KodexApi.lnreaderRemoveRepository(baseUrl: String, apiKey: String, url: String) {
    client.delete("$baseUrl/api/v1/lnreader/repos") {
        header(HEADER_API_KEY, apiKey)
        parameter("url", url)
    }
}

// ── Provider configuration (content sources and metadata providers) ───────────────────────────

/**
 * A provider's admin-level configuration schema plus the values currently stored. [metadata] picks
 * the metadata-provider endpoint; the default is a content source.
 */
suspend fun KodexApi.sourceConfig(baseUrl: String, apiKey: String, providerId: String, metadata: Boolean = false): SourceConfigDto =
    client.get("$baseUrl/api/v1/${if (metadata) "metadata-providers" else "content-sources"}/$providerId/config") { header(HEADER_API_KEY, apiKey) }.body()

/** Values keyed by field. Omit a SECRET's key to keep the stored secret; send "" to clear it. */
suspend fun KodexApi.saveSourceConfig(
    baseUrl: String,
    apiKey: String,
    providerId: String,
    values: Map<String, String>,
    metadata: Boolean = false,
): SourceConfigDto =
    client.put("$baseUrl/api/v1/${if (metadata) "metadata-providers" else "content-sources"}/$providerId/config") {
        header(HEADER_API_KEY, apiKey)
        contentType(ContentType.Application.Json)
        setBody(values)
    }.body()

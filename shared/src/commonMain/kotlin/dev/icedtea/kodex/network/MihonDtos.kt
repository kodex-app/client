package dev.icedtea.kodex.network

import kotlinx.serialization.Serializable

// ── Mihon extensions (the Keiyoushi JAR builds run by the server's kodex-mihon runtime) ─────────────

/** A repository entry with its install state on this server (`/api/v1/mihon/extensions/available`). */
@Serializable
data class MihonAvailableDto(
    val packageName: String,
    val name: String = "",
    /** One language, or "all" when the extension bundles several. */
    val lang: String = "all",
    val versionName: String = "",
    val versionCode: Long = 0,
    val libVersion: String = "",
    val iconUrl: String? = null,
    val nsfw: Boolean = false,
    val contentWarning: String = "UNSPECIFIED",
    val sources: List<MihonSourceDto> = emptyList(),
    val installed: Boolean = false,
    val installedVersion: String? = null,
    val updateAvailable: Boolean = false,
    /** Badge label of the repository this entry came from (e.g. KEI). */
    val repo: String? = null,
    val repoUrl: String = "",
)

@Serializable
data class MihonSourceDto(
    /** Mihon's source id — also the content-source id once installed. */
    val id: String,
    val name: String = "",
    val lang: String = "all",
    val homeUrl: String = "",
)

@Serializable
data class MihonInstalledDto(
    val packageName: String,
    val name: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val libVersion: String = "",
    val nsfw: Boolean = false,
    val iconUrl: String? = null,
    val sources: List<MihonInstalledSourceDto> = emptyList(),
)

@Serializable
data class MihonInstalledSourceDto(
    val id: String,
    val name: String = "",
    val lang: String? = null,
    val website: String? = null,
)

@Serializable
data class MihonUpdateStatusDto(
    val checkedAt: String? = null,
    val updates: List<MihonAvailableDtoRef> = emptyList(),
)

/** An index entry with a newer version than the installed one (subset the update list needs). */
@Serializable
data class MihonAvailableDtoRef(
    val packageName: String = "",
    val name: String = "",
    val versionName: String = "",
)

@Serializable
data class MihonUpdateOutcome(
    val packageName: String,
    val name: String = "",
    val version: String = "",
    val updated: Boolean = false,
    val error: String? = null,
)

/** A configured extension repository and its last fetch (`/api/v1/mihon/repos`). */
@Serializable
data class MihonRepositoryDto(
    val url: String,
    /** Off = kept in the list but neither fetched nor merged into the extension list. */
    val enabled: Boolean = true,
    val indexUrl: String? = null,
    val name: String? = null,
    val badgeLabel: String? = null,
    val signingKey: String? = null,
    val website: String? = null,
    val extensionCount: Int = 0,
    /** Entries with a JAR build — the only ones that run on the server. */
    val jarCount: Int = 0,
    val fetchedAt: String? = null,
    val error: String? = null,
)

@Serializable
data class MihonAddRepositoryRequest(val url: String)

@Serializable
data class RepositoryEnabledRequest(val enabled: Boolean)

@Serializable
data class RepositoryOrderRequest(val urls: List<String>)

// ── Interactive browser pages (`/api/v1/mihon/browser/sessions`, admin) ────────────────────────────

@Serializable
data class MihonBrowserSessionDto(
    val id: String,
    val url: String = "",
    val title: String = "",
    /** Emulated viewport; mouse coordinates are sent in this space. */
    val width: Int = 1024,
    val height: Int = 768,
    val frameSeq: Long = 0,
    val cookiesSaved: Int = 0,
    val closed: Boolean = false,
)

@Serializable
data class MihonBrowserOpenRequest(val url: String? = null, val sourceId: String? = null)

@Serializable
data class MihonBrowserNavigateRequest(val url: String)

/** `type`: mouseMoved / mousePressed / mouseReleased / mouseWheel, page coordinates. */
@Serializable
data class MihonBrowserMouseRequest(
    val type: String,
    val x: Double,
    val y: Double,
    val button: String = "left",
    val clickCount: Int = 1,
    val deltaX: Double = 0.0,
    val deltaY: Double = 0.0,
)

@Serializable
data class MihonBrowserTextRequest(val text: String)

@Serializable
data class MihonBrowserKeyRequest(val key: String, val modifiers: Int = 0)

// ── Metadata providers (built into the server) ─────────────────────────────────────────────────────

@Serializable
data class MetadataProviderDto(
    val id: String,
    val displayName: String = "",
    val configSchema: List<ConfigFieldDto> = emptyList(),
)

/** Live state of the server's shared headless browser (`/api/v1/server/network/browser`). */
@Serializable
data class BrowserStatusDto(
    /** remote / local / none. */
    val mode: String = "none",
    val running: Boolean = false,
    val pagesOpen: Int = 0,
    val maxPages: Int = 0,
    val idleSeconds: Long? = null,
    val uptimeSeconds: Long? = null,
    val endpoint: String? = null,
)

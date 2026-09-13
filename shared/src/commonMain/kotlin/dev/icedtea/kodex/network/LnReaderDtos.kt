package dev.icedtea.kodex.network

import kotlinx.serialization.Serializable

// ── LNReader plugins (compiled .js novel sources run by the server's kodex-lnreader runtime) ─────────

/** A repository entry with its install state on this server (`/api/v1/lnreader/plugins/available`). */
@Serializable
data class LnReaderAvailableDto(
    val id: String,
    val name: String = "",
    val site: String = "",
    /** ISO code, or "all" for multi-language plugins. */
    val lang: String = "all",
    val langName: String = "",
    val version: String = "",
    val iconUrl: String? = null,
    val installed: Boolean = false,
    val installedVersion: String? = null,
    val updateAvailable: Boolean = false,
    /** The content-source id once installed (what Browse / WEB libraries use). */
    val sourceId: String? = null,
    /** Reads the site's browser storage — needs the localStorage JSON pasted into its settings. */
    val webStorageUtilized: Boolean = false,
)

@Serializable
data class LnReaderInstalledDto(
    val id: String,
    val name: String = "",
    val version: String = "",
    val site: String = "",
    val lang: String = "all",
    val iconUrl: String? = null,
    val sourceId: String = "",
    val webStorageUtilized: Boolean = false,
    val hasFilters: Boolean = false,
    val sideloaded: Boolean = false,
)

@Serializable
data class LnReaderUpdateDto(
    val id: String,
    val name: String = "",
    val installedVersion: String? = null,
    val latestVersion: String = "",
)

@Serializable
data class LnReaderUpdateStatusDto(
    val checkedAt: String? = null,
    val updates: List<LnReaderUpdateDto> = emptyList(),
)

@Serializable
data class LnReaderUpdateOutcome(
    val id: String,
    val name: String = "",
    val version: String = "",
    val updated: Boolean = false,
    val error: String? = null,
)

/** A configured plugin repository (a plugins.min.json URL) with its last fetch result. */
@Serializable
data class LnReaderRepositoryDto(
    val url: String,
    /** Off = kept in the list (and its merge position) but neither fetched nor merged. */
    val enabled: Boolean = true,
    /** Pre-seeded through the server's kodex.lnreader.repositories rather than added here. */
    val builtIn: Boolean = false,
    val pluginCount: Int = 0,
    val fetchedAt: String? = null,
    val error: String? = null,
)

@Serializable
data class LnReaderAddRepositoryRequest(val url: String)

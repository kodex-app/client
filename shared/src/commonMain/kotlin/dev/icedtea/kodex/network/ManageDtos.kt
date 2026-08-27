package dev.icedtea.kodex.network

import kotlinx.serialization.Serializable

// ── Libraries CRUD ──────────────────────────────────────────────────────────────────────────────

/** Body of `POST /api/v1/libraries`. [type] is LOCAL | WEB; [mediaKind] is COMIC | BOOK. */
@Serializable
data class CreateLibraryRequest(
    val name: String,
    val type: String,
    val mediaKind: String? = null,
    val root: String? = null,
    val contentSourceId: String? = null,
    val refresh: RefreshSettingsDto? = null,
)

/** Body of `PATCH /api/v1/libraries/{id}`. */
@Serializable
data class UpdateLibraryRequest(
    val name: String,
    val root: String? = null,
    val contentSourceId: String? = null,
    val refresh: RefreshSettingsDto? = null,
)

/**
 * Refresh schedule + what a scan indexes, shared by create and update. Every field is nullable and
 * omitted fields keep their current value server-side, so a form only needs to send what it edits.
 */
@Serializable
data class RefreshSettingsDto(
    /** NONE | EVERY_3H | EVERY_6H | EVERY_12H | EVERY_24H | WEEKLY. */
    val refreshInterval: String? = null,
    val refreshOnStartup: Boolean? = null,
    val scanForceModifiedTime: Boolean? = null,
    val scanCbx: Boolean? = null,
    val scanPdf: Boolean? = null,
    val scanEpub: Boolean? = null,
    val scanDirectoryExclusions: Set<String>? = null,
    val specialFolders: Set<String>? = null,
    val autoDownload: Boolean? = null,
)

/** A directory entry in the admin folder picker (`GET /api/v1/filesystem`). */
@Serializable
data class DirEntry(val name: String = "", val path: String = "")

@Serializable
data class DirectoryListing(
    val path: String = "",
    val parent: String? = null,
    val directories: List<DirEntry> = emptyList(),
)

// ── Series metadata (partial edit) ──────────────────────────────────────────────────────────────

/** Partial `PATCH /api/v1/series/{id}/metadata`; only non-null fields are applied. */
@Serializable
data class UpdateSeriesMetadataRequest(
    val title: String? = null,
    val summary: String? = null,
    val publisher: String? = null,
    val status: String? = null,
    val language: String? = null,
    val readingDirection: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val labelIds: List<String>? = null,
    /** Non-null replaces the whole locked set. */
    val lockedFields: List<String>? = null,
    val identifiers: Map<String, String>? = null,
)

// ── Labels ──────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class LabelRequest(val name: String)

// ── Plugins ─────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class InstalledPluginDto(
    val id: String,
    val name: String = "",
    val version: String = "",
    val state: String = "",
    val kind: String? = null,
)

@Serializable
data class AvailablePluginDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val provider: String? = null,
    val latestVersion: String = "",
    val kind: String? = null,
)

@Serializable
data class InstallRequest(val pluginId: String, val version: String)

@Serializable
data class PluginUpdateDto(
    val id: String,
    val name: String = "",
    val installedVersion: String? = null,
    val latestVersion: String? = null,
)

@Serializable
data class PluginUpdateStatusDto(
    val checkedAt: String? = null,
    val updates: List<PluginUpdateDto> = emptyList(),
)

// ── Migration ───────────────────────────────────────────────────────────────────────────────────

@Serializable
data class MigrateRequest(
    val targetProviderId: String,
    val targetExternalId: String,
    val migrateRead: Boolean? = true,
    val deleteDownloads: Boolean? = false,
    val migrateMetadata: Boolean? = true,
)

@Serializable
data class MigrationResultDto(
    val seriesId: String = "",
    val name: String = "",
    val providerId: String = "",
    val externalId: String = "",
    val chapterCount: Int = 0,
    val deletedDownloads: Int = 0,
)

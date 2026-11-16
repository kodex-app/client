package dev.icedtea.kodex.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
internal data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/**
 * Reads the client's releases the way CI publishes them (see .github/workflows/build.yml): a GitHub
 * Release per `v*` tag, and one rolling `dev` prerelease whose single APK is named
 * `kodex-client-dev-<sha>.apk`. Unauthenticated — 60 requests/hour per IP is plenty for a check per
 * launch.
 */
internal class GitHubReleases(private val client: HttpClient) {

    /** The newest build on [build]'s channel when it differs from (or is newer than) the running one. */
    suspend fun findUpdate(build: AppBuildInfo): AvailableUpdate? {
        val channel = build.channel ?: return null
        val release = when (channel) {
            UpdateChannel.RELEASE -> fetch("https://api.github.com/repos/${build.repo}/releases/latest")
            UpdateChannel.DEV -> fetch("https://api.github.com/repos/${build.repo}/releases/tags/dev")
        } ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        val version = when (channel) {
            UpdateChannel.RELEASE -> release.tagName.removePrefix("v")
            UpdateChannel.DEV -> apk.name.removePrefix("kodex-client-").removeSuffix(".apk")
        }
        val newer = when (channel) {
            UpdateChannel.RELEASE -> compareVersions(version, build.versionName) > 0
            UpdateChannel.DEV -> version != build.versionName
        }
        if (!newer) return null
        return AvailableUpdate(
            version = version,
            title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            notes = release.body.orEmpty().trim(),
            pageUrl = release.htmlUrl,
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
        )
    }

    private suspend fun fetch(url: String): GhRelease? {
        val response: HttpResponse = client.get(url) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        // No release yet on this channel — nothing to update to, not an error.
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("GitHub answered ${response.status.value}")
        return response.body<GhRelease>().takeUnless { it.draft }
    }
}

/**
 * Semantic-ish version order: numeric dot parts compared numerically (missing parts are 0), then a
 * build without a pre-release suffix ranks above one with it (`1.2.0` > `1.2.0-beta.1`); two
 * suffixes compare as strings. Anything unparsable sorts as `0`.
 */
internal fun compareVersions(a: String, b: String): Int {
    fun split(v: String): Pair<List<Int>, String?> {
        val clean = v.trim().removePrefix("v").substringBefore('+')
        val core = clean.substringBefore('-')
        val pre = clean.substringAfter('-', "").takeIf { it.isNotEmpty() }
        return core.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }
    val (na, pa) = split(a)
    val (nb, pb) = split(b)
    for (i in 0 until maxOf(na.size, nb.size)) {
        val c = na.getOrElse(i) { 0 }.compareTo(nb.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return when {
        pa == null && pb == null -> 0
        pa == null -> 1
        pb == null -> -1
        else -> pa.compareTo(pb)
    }
}

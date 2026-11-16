package dev.icedtea.kodex.update

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

/**
 * Self-update from GitHub Releases, for builds that were *not* installed from an app store.
 *
 * Whether a build may update itself is decided twice:
 * - at build time — only the Android `github` flavor ships the update channel (and the
 *   install-packages permission); the `store` flavor and iOS have none, so they never check;
 * - at run time — even a `github` APK that a store re-distributed is left alone when the platform
 *   reports a store as its installer, so the store stays the single source of updates.
 */
enum class UpdateChannel(val id: String) {
    /** Tagged `v*` releases; compared by semantic version. */
    RELEASE("release"),

    /** The rolling `dev` prerelease; any build other than the installed one is an update. */
    DEV("dev");

    companion object {
        fun parse(raw: String?): UpdateChannel? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/** What the running build is, and where its updates come from. */
data class AppBuildInfo(
    val versionName: String,
    val versionCode: Long,
    /** Null when this build has no update source (store flavor, local builds, iOS). */
    val channel: UpdateChannel?,
    /**
     * GitHub `owner/name` the releases are published under — the repository whose workflow built the
     * APK (`GITHUB_REPOSITORY`), never a constant. Null outside CI builds.
     */
    val repo: String?,
    /** Package name of the store that installed the app, when it was a store. */
    val storeInstaller: String?,
)

/** A downloadable build found on GitHub. */
data class AvailableUpdate(
    /** Comparable version: `1.2.3` for releases, `dev-<sha>` for dev builds. */
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface InstallOutcome {
    /** Handed to the system installer; the app is replaced (and restarted by the user) from here. */
    data object Started : InstallOutcome

    /** The user has to allow "install unknown apps" for Kodex first; settings were opened. */
    data object NeedsPermission : InstallOutcome

    data object Cancelled : InstallOutcome

    data class Failed(val message: String) : InstallOutcome
}

/** The platform half: build identity plus the actual download-and-install. */
interface AppUpdatePlatform {
    val build: AppBuildInfo

    /** Downloads [update]'s APK with [client] and hands it to the system installer. */
    suspend fun install(client: HttpClient, update: AvailableUpdate, onProgress: (Float?) -> Unit): InstallOutcome
}

/** True when this build checks GitHub for updates at all. */
val AppBuildInfo.selfUpdates: Boolean get() = channel != null && repo != null && storeInstaller == null

@Composable
expect fun rememberAppUpdatePlatform(): AppUpdatePlatform

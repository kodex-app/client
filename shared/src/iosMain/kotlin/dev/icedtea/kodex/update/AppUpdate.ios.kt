package dev.icedtea.kodex.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import platform.Foundation.NSBundle

/** iOS can't install an app outside the App Store / TestFlight, so there is never a channel. */
@Composable
actual fun rememberAppUpdatePlatform(): AppUpdatePlatform = remember { IosAppUpdatePlatform }

private object IosAppUpdatePlatform : AppUpdatePlatform {
    override val build: AppBuildInfo = run {
        val info = NSBundle.mainBundle.infoDictionary
        AppBuildInfo(
            versionName = info?.get("CFBundleShortVersionString") as? String ?: "",
            versionCode = (info?.get("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L,
            channel = null,
            repo = null,
            storeInstaller = null,
        )
    }

    override suspend fun install(client: HttpClient, update: AvailableUpdate, onProgress: (Float?) -> Unit) =
        InstallOutcome.Failed("Updates come from the App Store on iOS")
}

package dev.icedtea.kodex.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/**
 * Build identity comes from the manifest, not BuildConfig: the shared module is a library and can't
 * see the app's BuildConfig, while the `github` flavor's manifest carries the channel and repo as
 * meta-data (androidApp/src/github/AndroidManifest.xml). The `store` flavor has no such entries, so
 * it reads as "no channel" and never checks.
 */
@Composable
actual fun rememberAppUpdatePlatform(): AppUpdatePlatform {
    val context = LocalContext.current
    return remember(context) { AndroidAppUpdatePlatform(context) }
}

private class AndroidAppUpdatePlatform(private val context: Context) : AppUpdatePlatform {

    override val build: AppBuildInfo = readBuildInfo(context)

    override suspend fun install(
        client: HttpClient,
        update: AvailableUpdate,
        onProgress: (Float?) -> Unit,
    ): InstallOutcome {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return InstallOutcome.NeedsPermission
        }

        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (update.apkSize > 0) setSize(update.apkSize)
            // From the second self-update on, Kodex is its own installer of record, which lets the
            // system skip the confirmation screen entirely.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = withContext(Dispatchers.IO) { installer.createSession(params) }
        val session = installer.openSession(sessionId)
        try {
            download(client, update, session, onProgress)
        } catch (e: Throwable) {
            session.abandon()
            throw e
        }

        val action = "${context.packageName}.UPDATE_STATUS.$sessionId"
        val statuses = Channel<Intent>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                statuses.trySend(intent)
            }
        }
        val appContext = context.applicationContext
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            // Mutable because the installer writes the status extras into it; explicit (package set)
            // because Android 14 refuses a mutable implicit PendingIntent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                appContext, sessionId, Intent(action).setPackage(context.packageName), flags,
            )
            session.commit(pending.intentSender)
            session.close()

            val status = statuses.receive()
            return when (val code = status.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = IntentCompat.getParcelableExtra(status, Intent.EXTRA_INTENT, Intent::class.java)
                        ?: return InstallOutcome.Failed("The system installer gave nothing to confirm")
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    // From here the system installer owns the flow; on success the app is replaced.
                    InstallOutcome.Started
                }
                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Started
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
                else -> InstallOutcome.Failed(
                    status.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($code)",
                )
            }
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    private suspend fun download(
        client: HttpClient,
        update: AvailableUpdate,
        session: PackageInstaller.Session,
        onProgress: (Float?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // The asset URL redirects to GitHub's object storage; Ktor follows it.
        client.prepareGet(update.apkUrl).execute { response ->
            if (!response.status.isSuccess()) error("Download failed (HTTP ${response.status.value})")
            val total = response.contentLength()?.takeIf { it > 0 } ?: update.apkSize.takeIf { it > 0 }
            val body = response.bodyAsChannel()
            session.openWrite("kodex.apk", 0, total ?: -1).use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                var written = 0L
                var lastReported = -1
                while (true) {
                    val n = body.readAvailable(buffer, 0, buffer.size)
                    if (n == -1) break
                    if (n == 0) continue
                    out.write(buffer, 0, n)
                    written += n
                    if (total != null) {
                        val pct = (written * 100 / total).toInt()
                        if (pct != lastReported) {
                            lastReported = pct
                            onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    } else if (lastReported == -1) {
                        lastReported = 0
                        onProgress(null)
                    }
                }
                session.fsync(out)
            }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}

private const val META_CHANNEL = "dev.icedtea.kodex.update.CHANNEL"
private const val META_REPO = "dev.icedtea.kodex.update.REPO"

/**
 * Installers that are app stores in their own right. A build one of these put on the phone is
 * updated by that store; the self-updater stepping in would fight it (and, for Play, break its
 * policy). Anything else — a browser, a file manager, the package installer, adb — is a sideload.
 */
private val STORE_INSTALLERS = setOf(
    "com.android.vending", // Google Play
    "com.aurora.store", // Aurora (Play mirror)
    "com.amazon.venezia", // Amazon Appstore
    "com.sec.android.app.samsungapps", // Galaxy Store
    "com.huawei.appmarket", // AppGallery
    "com.xiaomi.market", // Xiaomi GetApps
    "com.xiaomi.mipicks",
    "com.heytap.market", // OPPO / OnePlus / realme
    "com.oppo.market",
    "com.bbk.appstore", // vivo
    "org.fdroid.fdroid", // F-Droid
    "org.fdroid.basic",
)

@Suppress("DEPRECATION")
private fun readBuildInfo(context: Context): AppBuildInfo {
    val pm = context.packageManager
    val pkg = context.packageName
    val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
    val meta = runCatching { pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA).metaData }.getOrNull()
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    }.getOrNull()
    return AppBuildInfo(
        versionName = info?.versionName.orEmpty(),
        versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
        channel = UpdateChannel.parse(meta?.getString(META_CHANNEL)),
        repo = meta?.getString(META_REPO)?.takeIf { it.contains('/') } ?: "kodex-app/client",
        storeInstaller = installer?.takeIf { it in STORE_INSTALLERS },
    )
}

package dev.icedtea.kodex.update

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import dev.icedtea.kodex.platform.createHttpClient
import dev.icedtea.kodex.platform.nowMillis
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    /** [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    /** Install-unknown-apps permission was missing; settings were opened, retry once it is granted. */
    data class NeedsPermission(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String, val update: AvailableUpdate?) : UpdateState
}

/**
 * App-wide self-update state: checks GitHub on launch (throttled) and on demand from About, prompts
 * once per new version, and drives the download/install. Inert when the build doesn't self-update
 * (see [AppBuildInfo.selfUpdates]) — every entry point returns immediately.
 */
class AppUpdateController(
    private val platform: AppUpdatePlatform,
    private val scope: CoroutineScope,
    private val settings: Settings = Settings(),
) {
    val build: AppBuildInfo get() = platform.build
    val enabled: Boolean get() = platform.build.selfUpdates

    private val client by lazy {
        createHttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
            // GitHub rejects API requests without a User-Agent.
            defaultRequest { header("User-Agent", "kodex-client/${platform.build.versionName}") }
        }
    }
    private val releases by lazy { GitHubReleases(client) }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the update dialog is up. Set by a check that found something worth interrupting for. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val _autoCheck = MutableStateFlow(settings.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private var job: Job? = null

    fun setAutoCheck(on: Boolean) {
        _autoCheck.value = on
        settings.putBoolean(KEY_AUTO, on)
    }

    /** Launch-time check: honours the auto-check toggle, a throttle, and a skipped version. */
    fun checkOnLaunch() {
        if (!enabled || !_autoCheck.value) return
        if (nowMillis() - settings.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    /** A manual check always prompts when something newer exists, even a skipped version. */
    fun check(manual: Boolean = true) {
        if (!enabled || job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val update = releases.findUpdate(platform.build)
                settings.putLong(KEY_LAST_CHECK, nowMillis())
                if (update == null) {
                    UpdateState.UpToDate
                } else {
                    if (manual || settings.getStringOrNull(KEY_SKIPPED) != update.version) _prompt.value = true
                    UpdateState.Available(update)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A failed background check stays silent; About shows it on a manual one.
                if (manual) UpdateState.Failed(e.message ?: "Couldn't reach GitHub", null) else UpdateState.Idle
            }
        }
    }

    fun install() {
        val update = currentUpdate() ?: return
        if (job?.isActive == true) return
        _prompt.value = true
        job = scope.launch {
            _state.value = UpdateState.Downloading(update, 0f)
            val outcome = try {
                platform.install(client, update) { p -> _state.value = UpdateState.Downloading(update, p) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                InstallOutcome.Failed(e.message ?: "Download failed")
            }
            _state.value = when (outcome) {
                // The system installer now owns the screen; keep the offer around in case it is backed out of.
                InstallOutcome.Started, InstallOutcome.Cancelled -> UpdateState.Available(update)
                InstallOutcome.NeedsPermission -> UpdateState.NeedsPermission(update)
                is InstallOutcome.Failed -> UpdateState.Failed(outcome.message, update)
            }
            if (outcome == InstallOutcome.Started || outcome == InstallOutcome.Cancelled) _prompt.value = false
        }
    }

    /** Brings the dialog back for an update already found. */
    fun reopen() {
        if (currentUpdate() != null) _prompt.value = true
    }

    /** "Later": close the dialog; the next launch past the throttle asks again. */
    fun dismiss() {
        if (_state.value is UpdateState.Downloading) return
        _prompt.value = false
    }

    /** "Skip this version": never auto-prompt for it again (a manual check still offers it). */
    fun skip() {
        currentUpdate()?.let { settings.putString(KEY_SKIPPED, it.version) }
        dismiss()
    }

    private fun currentUpdate(): AvailableUpdate? = when (val s = _state.value) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.NeedsPermission -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    }

    private companion object {
        const val KEY_AUTO = "update.autoCheck"
        const val KEY_SKIPPED = "update.skippedVersion"
        const val KEY_LAST_CHECK = "update.lastCheckAt"
        const val AUTO_CHECK_INTERVAL_MS = 60L * 60 * 1000
    }
}

val LocalAppUpdate = staticCompositionLocalOf<AppUpdateController?> { null }

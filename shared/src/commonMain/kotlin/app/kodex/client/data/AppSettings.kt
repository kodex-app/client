package app.kodex.client.data

import app.kodex.client.ui.theme.AppTheme
import app.kodex.client.ui.theme.ThemeMode
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local appearance preferences (theme mode, colour theme, AMOLED, dynamic colour). These are
 * app-level, not per-server, so they live in multiplatform-settings alongside the saved servers — the
 * UI observes the [StateFlow]s and [KodexTheme] applies them. Persisted immediately on each setter.
 */
class AppSettings(private val settings: Settings = Settings()) {

    private val _themeMode = MutableStateFlow(readEnum(KEY_MODE, ThemeMode.entries, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appTheme = MutableStateFlow(readEnum(KEY_THEME, AppTheme.entries, AppTheme.DEFAULT))
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _amoled = MutableStateFlow(settings.getBoolean(KEY_AMOLED, false))
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    private val _dynamicColor = MutableStateFlow(settings.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Library series view: true = cover grid, false = list rows. */
    private val _libraryGridView = MutableStateFlow(settings.getBoolean(KEY_LIBRARY_GRID, true))
    val libraryGridView: StateFlow<Boolean> = _libraryGridView.asStateFlow()

    /** What to display as a series' name: "title" (metadata) or "name" (folder). */
    private val _libraryDisplayBy = MutableStateFlow(settings.getStringOrNull(KEY_LIBRARY_DISPLAY) ?: "title")
    val libraryDisplayBy: StateFlow<String> = _libraryDisplayBy.asStateFlow()

    /** Global incognito reading: when on, no reader saves progress/history. */
    private val _incognito = MutableStateFlow(settings.getBoolean(KEY_INCOGNITO, false))
    val incognitoMode: StateFlow<Boolean> = _incognito.asStateFlow()

    /**
     * Bumped whenever an "Updates seen" mark is written, so the Recents badge recomputes the moment
     * the tab is opened. The marks themselves are per-server and read on demand rather than held
     * here, since only one server is active at a time.
     */
    private val _updatesSeenMark = MutableStateFlow(0)
    val updatesSeenMark: StateFlow<Int> = _updatesSeenMark.asStateFlow()

    fun setThemeMode(value: ThemeMode) {
        settings.putString(KEY_MODE, value.name); _themeMode.value = value
    }

    fun setAppTheme(value: AppTheme) {
        settings.putString(KEY_THEME, value.name); _appTheme.value = value
    }

    fun setAmoled(value: Boolean) {
        settings.putBoolean(KEY_AMOLED, value); _amoled.value = value
    }

    fun setDynamicColor(value: Boolean) {
        settings.putBoolean(KEY_DYNAMIC, value); _dynamicColor.value = value
    }

    fun setLibraryGridView(value: Boolean) {
        settings.putBoolean(KEY_LIBRARY_GRID, value); _libraryGridView.value = value
    }

    fun setIncognitoMode(value: Boolean) {
        settings.putBoolean(KEY_INCOGNITO, value); _incognito.value = value
    }

    /**
     * Per-library grouping dimension (none | status | source) and the group tab last open within it.
     * Device-local rather than a server setting, matching the web UI, which keeps these two in
     * localStorage while sort lives server-side — grouping is about how *this* screen is laid out on
     * *this* device, not a preference worth syncing. Callers coerce the stored value against the
     * dimensions the library actually offers, so an entry written by an older build reads as "none".
     */
    fun libraryGroupBy(libraryId: String): String =
        settings.getStringOrNull("$KEY_LIBRARY_GROUP.$libraryId") ?: "none"

    fun setLibraryGroupBy(libraryId: String, value: String) {
        settings.putString("$KEY_LIBRARY_GROUP.$libraryId", value)
    }

    fun libraryGroupTab(libraryId: String, groupBy: String): String? =
        settings.getStringOrNull("$KEY_LIBRARY_GROUP_TAB.$libraryId.$groupBy")

    fun setLibraryGroupTab(libraryId: String, groupBy: String, key: String) {
        settings.putString("$KEY_LIBRARY_GROUP_TAB.$libraryId.$groupBy", key)
    }

    /**
     * When the user last looked at this server's Updates feed, as epoch millis. Device-local by
     * design: the badge answers "new since *you* last looked here", which is a property of this
     * device, not of the account.
     */
    fun updatesSeenAt(serverId: String): Long =
        settings.getLong("$KEY_UPDATES_SEEN.$serverId", 0L)

    fun markUpdatesSeen(serverId: String, millis: Long) {
        settings.putLong("$KEY_UPDATES_SEEN.$serverId", millis)
        _updatesSeenMark.value += 1
    }

    fun setLibraryDisplayBy(value: String) {
        settings.putString(KEY_LIBRARY_DISPLAY, value); _libraryDisplayBy.value = value
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, values: List<T>, fallback: T): T {
        val stored = settings.getStringOrNull(key) ?: return fallback
        return values.firstOrNull { it.name == stored } ?: fallback
    }

    private companion object {
        const val KEY_MODE = "appearance.themeMode"
        const val KEY_THEME = "appearance.theme"
        const val KEY_AMOLED = "appearance.amoled"
        const val KEY_DYNAMIC = "appearance.dynamicColor"
        const val KEY_LIBRARY_GRID = "library.gridView"
        const val KEY_LIBRARY_DISPLAY = "library.displayBy"
        const val KEY_LIBRARY_GROUP = "library.groupBy"
        const val KEY_LIBRARY_GROUP_TAB = "library.groupTab"
        const val KEY_INCOGNITO = "reader.incognito"
        const val KEY_UPDATES_SEEN = "recents.updatesSeenAt"
    }
}

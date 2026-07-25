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

    private inline fun <reified T : Enum<T>> readEnum(key: String, values: List<T>, fallback: T): T {
        val stored = settings.getStringOrNull(key) ?: return fallback
        return values.firstOrNull { it.name == stored } ?: fallback
    }

    private companion object {
        const val KEY_MODE = "appearance.themeMode"
        const val KEY_THEME = "appearance.theme"
        const val KEY_AMOLED = "appearance.amoled"
        const val KEY_DYNAMIC = "appearance.dynamicColor"
    }
}

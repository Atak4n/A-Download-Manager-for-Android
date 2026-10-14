package com.atakan.adm.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMaxConcurrent(value: Int) = put(Keys.MAX_CONCURRENT, value.coerceIn(1, 10))

    suspend fun setConnections(value: Int) = put(Keys.CONNECTIONS, value.coerceIn(1, 16))

    suspend fun setSpeedLimitKbps(value: Int) = put(Keys.SPEED_LIMIT, value.coerceAtLeast(0))

    suspend fun setUseCategorySubfolders(value: Boolean) = put(Keys.CATEGORY_SUBFOLDERS, value)

    suspend fun setTargetDirUri(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.TARGET_DIR) else prefs[Keys.TARGET_DIR] = value
        }
    }

    suspend fun setAutoRetryCount(value: Int) = put(Keys.RETRY_COUNT, value.coerceIn(0, 20))

    suspend fun setAutoRetryDelay(value: Int) = put(Keys.RETRY_DELAY, value.coerceIn(0, 3600))

    suspend fun setWatchClipboard(value: Boolean) = put(Keys.WATCH_CLIPBOARD, value)

    suspend fun setWifiOnly(value: Boolean) = put(Keys.WIFI_ONLY, value)

    suspend fun setDefaultContainer(value: VideoContainer) = put(Keys.CONTAINER, value.name)

    suspend fun setSubtitleMode(value: SubtitleMode) = put(Keys.SUBTITLE_MODE, value.name)

    suspend fun setSubtitleLanguages(value: String) = put(Keys.SUBTITLE_LANGS, value)

    suspend fun setIncludeAutoSubs(value: Boolean) = put(Keys.AUTO_SUBS, value)

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.THEME, value.name)

    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC_COLOR, value)

    suspend fun setBlurEffects(value: Boolean) = put(Keys.BLUR, value)

    suspend fun setYtdlpLastUpdateCheck(value: Long) = put(Keys.YTDLP_CHECK, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun Preferences.toSettings() = AppSettings(
        maxConcurrentDownloads = this[Keys.MAX_CONCURRENT] ?: 3,
        connectionsPerDownload = this[Keys.CONNECTIONS] ?: 4,
        speedLimitKbps = this[Keys.SPEED_LIMIT] ?: 0,
        useCategorySubfolders = this[Keys.CATEGORY_SUBFOLDERS] ?: false,
        targetDirUri = this[Keys.TARGET_DIR],
        autoRetryCount = this[Keys.RETRY_COUNT] ?: 3,
        autoRetryDelaySeconds = this[Keys.RETRY_DELAY] ?: 30,
        watchClipboard = this[Keys.WATCH_CLIPBOARD] ?: true,
        wifiOnly = this[Keys.WIFI_ONLY] ?: false,
        defaultContainer = this[Keys.CONTAINER].toEnumOrDefault(VideoContainer.MP4),
        subtitleMode = this[Keys.SUBTITLE_MODE].toEnumOrDefault(SubtitleMode.NONE),
        subtitleLanguages = this[Keys.SUBTITLE_LANGS] ?: "tr,en",
        includeAutoSubs = this[Keys.AUTO_SUBS] ?: false,
        themeMode = this[Keys.THEME].toEnumOrDefault(ThemeMode.SYSTEM),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        blurEffects = this[Keys.BLUR] ?: true,
        ytdlpLastUpdateCheck = this[Keys.YTDLP_CHECK] ?: 0L,
    )

    private object Keys {
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val CONNECTIONS = intPreferencesKey("connections")
        val SPEED_LIMIT = intPreferencesKey("speed_limit_kbps")
        val CATEGORY_SUBFOLDERS = booleanPreferencesKey("category_subfolders")
        val TARGET_DIR = stringPreferencesKey("target_dir_uri")
        val RETRY_COUNT = intPreferencesKey("retry_count")
        val RETRY_DELAY = intPreferencesKey("retry_delay")
        val WATCH_CLIPBOARD = booleanPreferencesKey("watch_clipboard")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CONTAINER = stringPreferencesKey("container")
        val SUBTITLE_MODE = stringPreferencesKey("subtitle_mode")
        val SUBTITLE_LANGS = stringPreferencesKey("subtitle_langs")
        val AUTO_SUBS = booleanPreferencesKey("auto_subs")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BLUR = booleanPreferencesKey("blur_effects")
        val YTDLP_CHECK = longPreferencesKey("ytdlp_last_update_check")
    }
}

/** Tolerates values written by an older build instead of crashing on them. */
private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(fallback: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrDefault(fallback) } ?: fallback

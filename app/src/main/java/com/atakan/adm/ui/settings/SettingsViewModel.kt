package com.atakan.adm.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.data.settings.AppSettings
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setMaxConcurrent(value: Int) = viewModelScope.launch { settings.setMaxConcurrent(value) }

    fun setConnections(value: Int) = viewModelScope.launch { settings.setConnections(value) }

    fun setSpeedLimit(kbps: Int) = viewModelScope.launch { settings.setSpeedLimitKbps(kbps) }

    fun setRetryCount(value: Int) = viewModelScope.launch { settings.setAutoRetryCount(value) }

    fun setRetryDelay(value: Int) = viewModelScope.launch { settings.setAutoRetryDelay(value) }

    fun setCategoryFolders(value: Boolean) =
        viewModelScope.launch { settings.setUseCategorySubfolders(value) }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch { settings.setWifiOnly(value) }

    fun setWatchClipboard(value: Boolean) =
        viewModelScope.launch { settings.setWatchClipboard(value) }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { settings.setThemeMode(value) }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settings.setDynamicColor(value) }

    fun setBlur(value: Boolean) = viewModelScope.launch { settings.setBlurEffects(value) }

    fun setContainer(value: VideoContainer) =
        viewModelScope.launch { settings.setDefaultContainer(value) }

    fun setSubtitleMode(value: SubtitleMode) =
        viewModelScope.launch { settings.setSubtitleMode(value) }

    fun setSubtitleLanguages(value: String) =
        viewModelScope.launch { settings.setSubtitleLanguages(value) }

    fun setIncludeAutoSubs(value: Boolean) =
        viewModelScope.launch { settings.setIncludeAutoSubs(value) }

    /** Keeps read/write access to the picked tree across reboots. */
    fun setTargetDir(uri: Uri?) = viewModelScope.launch {
        if (uri == null) {
            settings.setTargetDirUri(null)
            return@launch
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.setTargetDirUri(uri.toString())
    }
}

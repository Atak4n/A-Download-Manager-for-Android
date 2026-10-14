package com.atakan.adm.ui.video

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.data.repo.DownloadRepository
import com.atakan.adm.data.repo.NewDownload
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.download.HttpProber
import com.atakan.adm.service.DownloadService
import com.atakan.adm.video.VideoFormatSelector
import com.atakan.adm.video.YtDlpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoPickerState(
    val url: String = "",
    val loading: Boolean = false,
    val title: String? = null,
    val options: List<VideoFormatSelector.Option> = emptyList(),
    val error: String? = null,
    val container: VideoContainer = VideoContainer.MP4,
    val subtitleMode: SubtitleMode = SubtitleMode.NONE,
    val subtitleLanguages: String = "tr,en",
    val includeAutoSubs: Boolean = false,
)

@HiltViewModel
class VideoPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ytDlp: YtDlpManager,
    private val repository: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPickerState())
    val state: StateFlow<VideoPickerState> = _state.asStateFlow()

    private var cookieHeader: String? = null
    private var cookieHost: String? = null

    /** Reads the page with "yt-dlp -J" and builds the quality list. */
    fun load(url: String, cookies: String? = null) {
        cookieHeader = cookies
        cookieHost = HttpProber.hostOf(url)
        viewModelScope.launch {
            val appSettings = settings.current()
            _state.value = VideoPickerState(
                url = url,
                loading = true,
                container = appSettings.defaultContainer,
                subtitleMode = appSettings.subtitleMode,
                subtitleLanguages = appSettings.subtitleLanguages,
                includeAutoSubs = appSettings.includeAutoSubs,
            )
            try {
                val info = ytDlp.fetchInfo(url)
                val options = VideoFormatSelector.options(info)
                _state.value = _state.value.copy(
                    loading = false,
                    title = info.title ?: info.fulltitle,
                    options = options,
                    error = if (options.isEmpty()) {
                        context.getString(com.atakan.adm.R.string.video_no_formats)
                    } else {
                        null
                    },
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: error::class.java.simpleName,
                )
            }
        }
    }

    fun setContainer(value: VideoContainer) {
        _state.value = _state.value.copy(container = value)
    }

    fun setSubtitleMode(value: SubtitleMode) {
        _state.value = _state.value.copy(subtitleMode = value)
    }

    fun setSubtitleLanguages(value: String) {
        _state.value = _state.value.copy(subtitleLanguages = value)
    }

    fun setIncludeAutoSubs(value: Boolean) {
        _state.value = _state.value.copy(includeAutoSubs = value)
    }

    fun clear() {
        _state.value = VideoPickerState()
    }

    fun download(option: VideoFormatSelector.Option) {
        val current = _state.value
        viewModelScope.launch {
            val title = current.title?.takeIf { it.isNotBlank() }
            val extension = when {
                option.isAudioOnly -> option.ext
                current.container == VideoContainer.NONE -> option.ext
                else -> current.container.name.lowercase()
            }
            val id = repository.create(
                NewDownload(
                    url = current.url,
                    fileName = title?.let { "$it.$extension" },
                    kind = if (option.isAudioOnly) DownloadKind.AUDIO else DownloadKind.VIDEO,
                    estimatedBytes = option.estimatedBytes.takeIf { it > 0 },
                    cookieHeader = cookieHeader,
                    cookieHost = cookieHost,
                    videoFormatId = option.formatSelector,
                    videoAudioFormatId = option.audioFormatId,
                    videoQualityLabel = option.label,
                    container = current.container,
                    audioFormat = if (option.isAudioOnly) option.ext else null,
                    subtitleMode = if (option.isAudioOnly) SubtitleMode.NONE else current.subtitleMode,
                    subtitleLanguages = current.subtitleLanguages,
                    includeAutoSubs = current.includeAutoSubs,
                ),
            )
            DownloadService.start(context, id)
            clear()
        }
    }
}

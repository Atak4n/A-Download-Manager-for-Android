package com.atakan.adm.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.repo.DownloadRepository
import com.atakan.adm.download.DownloadEngine
import com.atakan.adm.download.ProgressSnapshot
import com.atakan.adm.download.SegmentProgress
import com.atakan.adm.service.DownloadService
import com.atakan.adm.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DetailUiState(
    val entity: DownloadEntity? = null,
    val progress: ProgressSnapshot? = null,
    val segments: List<SegmentProgress> = emptyList(),
    val log: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
) : ViewModel() {

    private val downloadId: Long =
        savedStateHandle.get<String>(Routes.ARG_DOWNLOAD_ID)?.toLongOrNull() ?: -1L

    private val log = MutableStateFlow<String?>(null)

    val state: StateFlow<DetailUiState> = combine(
        repository.observe(downloadId),
        repository.observeSegments(downloadId),
        engine.progress,
        log,
    ) { entity, segments, progress, logText ->
        val snapshot = progress[downloadId]
        val liveSegments = snapshot?.segments?.takeIf { it.isNotEmpty() }
            ?: segments.map {
                SegmentProgress(
                    index = it.index,
                    startByte = it.startByte,
                    endByte = it.endByte,
                    downloadedBytes = it.downloadedBytes,
                    status = it.status,
                    attempts = it.attempts,
                )
            }
        DetailUiState(
            entity = entity,
            progress = snapshot,
            segments = liveSegments.sortedBy { it.index },
            log = logText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        refreshLog()
    }

    /** yt-dlp writes its output to a per-download file. */
    fun refreshLog() {
        viewModelScope.launch {
            val path = repository.find(downloadId)?.logFilePath ?: return@launch
            val text = withContext(Dispatchers.IO) {
                val file = File(path)
                if (file.exists()) file.readText().takeLast(MAX_LOG_CHARS) else null
            }
            log.value = text
        }
    }

    fun pause() = viewModelScope.launch { engine.pause(downloadId) }

    fun resume() {
        DownloadService.resume(context, downloadId)
    }

    fun cancel() = viewModelScope.launch { engine.cancel(downloadId) }

    fun setChecksum(value: String?) = viewModelScope.launch {
        repository.setChecksum(downloadId, value)
    }

    fun segmentIsLive(status: SegmentStatus): Boolean = status == SegmentStatus.RUNNING

    companion object {
        private const val MAX_LOG_CHARS = 20_000
    }
}

package com.atakan.adm.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SortOrder
import com.atakan.adm.core.util.Checksum
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.repo.DownloadRepository
import com.atakan.adm.data.repo.NewDownload
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.download.DownloadEngine
import com.atakan.adm.download.ProgressSnapshot
import com.atakan.adm.service.DownloadService
import com.atakan.adm.service.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadRow(
    val entity: DownloadEntity,
    val progress: ProgressSnapshot?,
) {
    val downloadedBytes: Long get() = progress?.downloadedBytes ?: entity.downloadedBytes
    val speed: Long get() = progress?.bytesPerSecond ?: 0L
    val fraction: Float
        get() = if (entity.totalBytes > 0) {
            (downloadedBytes.toDouble() / entity.totalBytes).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class DownloadsUiState(
    val rows: List<DownloadRow> = emptyList(),
    val query: String = "",
    val category: Category? = null,
    val sortOrder: SortOrder = SortOrder.DATE,
    val activeCount: Int = 0,
    val totalSpeed: Long = 0L,
    val errorMessage: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<Category?>(null)
    private val sortOrder = MutableStateFlow(SortOrder.DATE)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<DownloadsUiState> = combine(
        repository.observeAll(),
        engine.progress,
        combine(query, category, sortOrder) { q, c, s -> Triple(q, c, s) },
        errorMessage,
    ) { downloads, progress, filters, error ->
        val (text, cat, sort) = filters
        val rows = downloads
            .asSequence()
            .filter { cat == null || it.category == cat }
            .filter { text.isBlank() || it.fileName.contains(text, ignoreCase = true) || it.url.contains(text, ignoreCase = true) }
            .map { DownloadRow(it, progress[it.id]) }
            .sortedWith(comparatorFor(sort))
            .toList()
        DownloadsUiState(
            rows = rows,
            query = text,
            category = cat,
            sortOrder = sort,
            activeCount = downloads.count { it.status.isActive },
            totalSpeed = progress.values.sumOf { it.bytesPerSecond },
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    private fun comparatorFor(sort: SortOrder): Comparator<DownloadRow> = when (sort) {
        SortOrder.DATE -> compareByDescending { it.entity.createdAt }
        SortOrder.NAME -> compareBy { it.entity.fileName.lowercase() }
        SortOrder.SIZE -> compareByDescending { it.entity.totalBytes }
        SortOrder.STATUS -> compareBy { it.entity.status.ordinal }
        SortOrder.SPEED -> compareByDescending { it.speed }
    }

    fun setQuery(value: String) { query.value = value }

    fun setCategory(value: Category?) { category.value = value }

    fun setSortOrder(value: SortOrder) { sortOrder.value = value }

    fun dismissError() { errorMessage.value = null }

    fun addDownload(
        url: String,
        fileName: String?,
        connections: Int,
        checksum: String?,
        scheduledAt: Long?,
        cookieHeader: String? = null,
        cookieHost: String? = null,
        referer: String? = null,
    ) {
        viewModelScope.launch {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                errorMessage.value = context.getString(com.atakan.adm.R.string.url_invalid)
                return@launch
            }
            val id = repository.create(
                NewDownload(
                    url = trimmed,
                    fileName = fileName?.takeIf { it.isNotBlank() },
                    connections = connections,
                    checksum = checksum?.takeIf { Checksum.normalize(it) != null },
                    scheduledAt = scheduledAt,
                    cookieHeader = cookieHeader,
                    cookieHost = cookieHost,
                    referer = referer,
                ),
            )
            if (scheduledAt != null) {
                DownloadWorker.enqueueAt(context, id, scheduledAt)
            } else {
                DownloadService.start(context, id)
            }
        }
    }

    fun pause(id: Long) = viewModelScope.launch { engine.pause(id) }

    fun resume(id: Long) = viewModelScope.launch {
        val entity = repository.find(id) ?: return@launch
        if (entity.scheduledAt != null && entity.status == DownloadStatus.SCHEDULED) {
            DownloadWorker.cancelScheduled(context, id)
            repository.setSchedule(id, null)
        }
        DownloadService.resume(context, id)
    }

    fun cancel(id: Long) = viewModelScope.launch { engine.cancel(id) }

    fun remove(id: Long) = viewModelScope.launch {
        DownloadWorker.cancelScheduled(context, id)
        engine.remove(id)
    }

    fun pauseAll() = viewModelScope.launch { engine.pauseAll() }
}

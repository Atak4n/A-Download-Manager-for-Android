package com.atakan.adm.download

import android.content.Context
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.db.SegmentDao
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.data.storage.FileStore
import com.atakan.adm.di.ApplicationScope
import com.atakan.adm.video.VideoDownloadTask
import com.atakan.adm.video.YtDlpManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the running downloads: queue admission, pause/resume/cancel and the
 * automatic retry policy. Progress is exposed in memory; the database only
 * carries checkpoints.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val prober: HttpProber,
    private val downloadDao: DownloadDao,
    private val segmentDao: SegmentDao,
    private val fileStore: FileStore,
    private val limiter: GlobalRateLimiter,
    private val settings: SettingsRepository,
    private val ytDlp: YtDlpManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val jobs = mutableMapOf<Long, Job>()
    private val jobsLock = Mutex()

    private val _progress = MutableStateFlow<Map<Long, ProgressSnapshot>>(emptyMap())
    val progress: StateFlow<Map<Long, ProgressSnapshot>> = _progress.asStateFlow()

    /** Emitted when a download reaches a terminal state, for notifications. */
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: MutableSharedFlow<Event> get() = _events

    sealed interface Event {
        data class Completed(val downloadId: Long) : Event
        data class Failed(val downloadId: Long, val message: String) : Event
        data class ChecksumMismatch(val downloadId: Long) : Event
        data object QueueIdle : Event
    }

    val activeCount: Int get() = jobs.size

    fun isRunning(downloadId: Long): Boolean = jobs.containsKey(downloadId)

    /** Starts [downloadId] now if a slot is free, otherwise leaves it queued. */
    suspend fun start(downloadId: Long, force: Boolean = false) {
        val entity = downloadDao.find(downloadId) ?: return
        if (entity.status == DownloadStatus.COMPLETED) return
        jobsLock.withLock {
            if (jobs.containsKey(downloadId)) return
            val maxConcurrent = settings.current().maxConcurrentDownloads
            if (!force && jobs.size >= maxConcurrent) {
                downloadDao.setStatus(downloadId, DownloadStatus.QUEUED)
                return
            }
            jobs[downloadId] = scope.launch { execute(downloadId) }
        }
    }

    private suspend fun execute(downloadId: Long) {
        val entity = downloadDao.find(downloadId)
        val outcome = if (entity != null && entity.kind != DownloadKind.FILE) {
            runVideo(downloadId, entity.totalBytes)
        } else {
            runFile(downloadId)
        }
        jobsLock.withLock { jobs.remove(downloadId) }
        _progress.update { it - downloadId }

        when (outcome) {
            is Outcome.Completed -> _events.emit(Event.Completed(downloadId))
            is Outcome.ChecksumMismatch -> _events.emit(Event.ChecksumMismatch(downloadId))
            is Outcome.Failed -> {
                _events.emit(Event.Failed(downloadId, outcome.message))
                scheduleAutoRetry(downloadId)
            }
            is Outcome.Paused -> Unit
        }
        pumpQueue()
    }

    /** Engine level result, shared by the file and video paths. */
    private sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data class Failed(val message: String) : Outcome
        data object ChecksumMismatch : Outcome
    }

    private suspend fun runVideo(downloadId: Long, totalBytes: Long): Outcome = coroutineScope {
        val task = VideoDownloadTask(
            context = context,
            downloadId = downloadId,
            ytDlp = ytDlp,
            downloadDao = downloadDao,
            fileStore = fileStore,
            settings = settings,
            onProgress = { snapshot -> _progress.update { it + (downloadId to snapshot) } },
        )
        // yt-dlp reports progress through its own callback thread, so the
        // snapshot is polled instead of pushed.
        val poller = launch {
            while (isActive) {
                delay(500)
                _progress.update { it + (downloadId to task.snapshot(totalBytes)) }
            }
        }
        try {
            when (val result = task.run()) {
                is VideoDownloadTask.Outcome.Completed -> Outcome.Completed
                is VideoDownloadTask.Outcome.Paused -> Outcome.Paused
                is VideoDownloadTask.Outcome.Failed -> Outcome.Failed(result.message)
            }
        } finally {
            poller.cancel()
        }
    }

    private suspend fun runFile(downloadId: Long): Outcome {
        val task = DownloadTask(
            downloadId = downloadId,
            client = client,
            prober = prober,
            downloadDao = downloadDao,
            segmentDao = segmentDao,
            fileStore = fileStore,
            limiter = limiter,
            settings = settings,
            onProgress = { snapshot ->
                _progress.update { it + (snapshot.downloadId to snapshot) }
            },
        )
        return when (val result = task.run()) {
            is DownloadTask.Outcome.Completed -> Outcome.Completed
            is DownloadTask.Outcome.ChecksumMismatch -> Outcome.ChecksumMismatch
            is DownloadTask.Outcome.Failed -> Outcome.Failed(result.message)
            is DownloadTask.Outcome.Paused -> Outcome.Paused
        }
    }

    /** Re-tries a failed download up to the configured limit. */
    private fun scheduleAutoRetry(downloadId: Long) {
        scope.launch {
            val appSettings = settings.current()
            val entity = downloadDao.find(downloadId) ?: return@launch
            if (entity.retryCount >= minOf(entity.maxRetries, appSettings.autoRetryCount)) return@launch
            downloadDao.setRetryCount(downloadId, entity.retryCount + 1)
            delay(appSettings.autoRetryDelaySeconds * 1000L)
            val still = downloadDao.find(downloadId) ?: return@launch
            if (still.status == DownloadStatus.FAILED) start(downloadId)
        }
    }

    suspend fun pause(downloadId: Long) {
        val job = jobsLock.withLock { jobs.remove(downloadId) }
        job?.cancel()
        job?.join()
        downloadDao.setStatus(downloadId, DownloadStatus.PAUSED)
        _progress.update { it - downloadId }
        pumpQueue()
    }

    suspend fun pauseAll() {
        val running = jobsLock.withLock { jobs.toMap().also { jobs.clear() } }
        running.forEach { (id, job) ->
            job.cancel()
            job.join()
            downloadDao.setStatus(id, DownloadStatus.PAUSED)
        }
        _progress.value = emptyMap()
        downloadDao.byStatus(DownloadStatus.QUEUED).forEach {
            downloadDao.setStatus(it.id, DownloadStatus.PAUSED)
        }
    }

    /** Manual start resets the retry counter, as specified. */
    suspend fun resume(downloadId: Long) {
        downloadDao.setRetryCount(downloadId, 0)
        start(downloadId)
    }

    suspend fun cancel(downloadId: Long, deletePartFile: Boolean = true) {
        val job = jobsLock.withLock { jobs.remove(downloadId) }
        job?.cancel()
        job?.join()
        downloadDao.setStatus(downloadId, DownloadStatus.CANCELLED)
        _progress.update { it - downloadId }
        if (deletePartFile) {
            runCatching { fileStore.partFile(downloadId).delete() }
        }
        pumpQueue()
    }

    suspend fun remove(downloadId: Long) {
        cancel(downloadId, deletePartFile = true)
        segmentDao.deleteForDownload(downloadId)
        downloadDao.delete(downloadId)
    }

    /** Fills free slots from the queue, oldest first. */
    suspend fun pumpQueue() {
        val maxConcurrent = settings.current().maxConcurrentDownloads
        val free = maxConcurrent - jobsLock.withLock { jobs.size }
        if (free <= 0) {
            return
        }
        val next = downloadDao.nextQueued(free)
        if (next.isEmpty() && jobsLock.withLock { jobs.isEmpty() }) {
            _events.emit(Event.QueueIdle)
            return
        }
        next.forEach { start(it.id) }
    }

    /** Called at process start: nothing can still be running, so unstick rows. */
    suspend fun recoverAfterProcessDeath() {
        downloadDao.markInterruptedAsPaused()
    }
}

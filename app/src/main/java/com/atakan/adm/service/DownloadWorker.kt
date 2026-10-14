package com.atakan.adm.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.download.DownloadEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Drives the queue from the background, where starting a foreground service is
 * not allowed. The worker itself runs as a foreground job, which is permitted.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: androidx.work.WorkerParameters,
    private val engine: DownloadEngine,
    private val downloadDao: DownloadDao,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        notifications.ensureChannels()
        val active = downloadDao.byStatus(DownloadStatus.RUNNING)
        return ForegroundInfo(
            NotificationHelper.FOREGROUND_ID,
            notifications.buildSummary(active, engine.progress.value),
        )
    }

    override suspend fun doWork(): Result {
        notifications.ensureChannels()
        val requestedId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (requestedId > 0) {
            engine.start(requestedId, force = true)
        } else {
            engine.pumpQueue()
        }

        // Stay alive while transfers run so the foreground job keeps the process up.
        while (engine.activeCount > 0) {
            if (isStopped) return Result.success()
            delay(POLL_INTERVAL_MILLIS)
            setForeground(getForegroundInfo())
        }
        return Result.success()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val UNIQUE_NAME = "adm-download-queue"
        private const val POLL_INTERVAL_MILLIS = 2_000L

        fun enqueueNow(context: Context, downloadId: Long? = null) {
            val data: Data = downloadId?.let { workDataOf(KEY_DOWNLOAD_ID to it) } ?: Data.EMPTY
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Scheduled start: fires even while the device is idle. */
        fun enqueueAt(context: Context, downloadId: Long, atMillis: Long) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_DOWNLOAD_ID to downloadId))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("adm-scheduled-$downloadId", ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelScheduled(context: Context, downloadId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("adm-scheduled-$downloadId")
        }
    }
}

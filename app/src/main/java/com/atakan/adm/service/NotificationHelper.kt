package com.atakan.adm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atakan.adm.MainActivity
import com.atakan.adm.R
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.util.Formatters
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.download.ProgressSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the progress and result notifications, including their actions. */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notification_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_progress_desc)
            setShowBadge(false)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            context.getString(R.string.notification_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_done_desc)
        }
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(done)
    }

    /** Foreground notification: one active download, or a count when several run. */
    fun buildSummary(
        active: List<DownloadEntity>,
        progress: Map<Long, ProgressSnapshot>,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (active.isEmpty()) {
            return builder
                .setContentTitle(context.getString(R.string.app_name))
                .setProgress(0, 0, false)
                .build()
        }

        val totalSpeed = progress.values.sumOf { it.bytesPerSecond }
        if (active.size == 1) {
            val item = active.first()
            val snapshot = progress[item.id]
            val done = snapshot?.downloadedBytes ?: item.downloadedBytes
            val total = if (item.totalBytes > 0) item.totalBytes else snapshot?.totalBytes ?: -1L
            builder.setContentTitle(item.fileName)
                .setContentText(
                    context.getString(
                        R.string.notification_summary_text,
                        Formatters.bytes(done),
                        Formatters.speed(snapshot?.bytesPerSecond ?: 0L),
                    ),
                )
            if (total > 0) {
                builder.setProgress(100, Formatters.percent(done, total), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            addActions(builder, item)
        } else {
            val totalBytes = active.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
            val doneBytes = active.sumOf { item ->
                progress[item.id]?.downloadedBytes ?: item.downloadedBytes
            }
            builder.setContentTitle(
                context.getString(R.string.notification_summary_title, active.size),
            ).setContentText(
                context.getString(
                    R.string.notification_summary_text,
                    Formatters.bytes(doneBytes),
                    Formatters.speed(totalSpeed),
                ),
            )
            if (totalBytes > 0) {
                builder.setProgress(100, Formatters.percent(doneBytes, totalBytes), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            builder.addAction(
                R.drawable.ic_stat_download,
                context.getString(R.string.action_pause_all),
                servicePendingIntent(DownloadService.ACTION_PAUSE_ALL, 0L),
            )
        }
        return builder.build()
    }

    private fun addActions(builder: NotificationCompat.Builder, item: DownloadEntity) {
        when (item.status) {
            DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.QUEUED ->
                builder.addAction(
                    R.drawable.ic_stat_download,
                    context.getString(R.string.action_resume),
                    servicePendingIntent(DownloadService.ACTION_RESUME, item.id),
                )
            else -> builder.addAction(
                R.drawable.ic_stat_download,
                context.getString(R.string.action_pause),
                servicePendingIntent(DownloadService.ACTION_PAUSE, item.id),
            )
        }
        builder.addAction(
            R.drawable.ic_stat_download,
            context.getString(R.string.action_cancel),
            servicePendingIntent(DownloadService.ACTION_CANCEL, item.id),
        )
    }

    /** Posted when a download leaves the active set. */
    fun notifyFinished(item: DownloadEntity, failureMessage: String?) {
        val title = when {
            failureMessage != null -> context.getString(R.string.notification_failed)
            item.checksumVerified == false -> context.getString(R.string.notification_checksum_failed)
            else -> context.getString(R.string.notification_completed)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(
                if (failureMessage == null) R.drawable.ic_stat_done else R.drawable.ic_stat_download,
            )
            .setContentTitle(title)
            .setContentText(item.fileName)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    failureMessage?.let { "${item.fileName}\n$it" } ?: item.fileName,
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent(item.id))
            .build()
        postIfAllowed(DONE_ID_BASE + item.id.toInt(), notification)
    }

    fun postSummary(id: Int, notification: Notification) = postIfAllowed(id, notification)

    fun cancel(id: Int) = manager.cancel(id)

    private fun postIfAllowed(id: Int, notification: Notification) {
        // POST_NOTIFICATIONS may be denied; the download itself must still run.
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun contentIntent(downloadId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            downloadId?.let { putExtra(MainActivity.EXTRA_DOWNLOAD_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            downloadId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, downloadId: Long): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).apply {
            this.action = action
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val requestCode = (action.hashCode() * 31 + downloadId.toInt())
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "download_progress"
        const val CHANNEL_DONE = "download_done"
        const val FOREGROUND_ID = 1
        const val DONE_ID_BASE = 2000
    }
}

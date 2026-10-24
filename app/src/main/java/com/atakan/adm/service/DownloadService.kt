package com.atakan.adm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.repo.DownloadRepository
import com.atakan.adm.download.DownloadEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps downloads alive while the app is in the background. Typed dataSync, as
 * required from Android 14 on.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var notifications: NotificationHelper

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private var observer: Job? = null
    private var eventsJob: Job? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        promote()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_START -> scope.launch { if (downloadId > 0) engine.start(downloadId) }
            ACTION_RESUME -> scope.launch { if (downloadId > 0) engine.resume(downloadId) }
            ACTION_PAUSE -> scope.launch { if (downloadId > 0) engine.pause(downloadId) }
            ACTION_CANCEL -> scope.launch { if (downloadId > 0) engine.cancel(downloadId) }
            ACTION_PAUSE_ALL -> scope.launch {
                engine.pauseAll()
                stopSelf()
            }
            else -> scope.launch { engine.pumpQueue() }
        }
        return START_STICKY
    }

    private fun promote() {
        if (startedForeground) return
        val notification = notifications.buildSummary(emptyList(), emptyMap())
        ServiceCompat.startForeground(
            this,
            NotificationHelper.FOREGROUND_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        startedForeground = true
    }

    /** Mirrors engine progress into the ongoing notification and posts result notifications. */
    private fun observeState() {
        observer?.cancel()
        observer = scope.launch {
            combine(repository.observeAll(), engine.progress) { downloads, progress ->
                downloads.filter { it.status.isActive || it.status == DownloadStatus.QUEUED } to progress
            }.sample(350L).collect { (active, progress) ->
                if (active.isEmpty() && engine.activeCount == 0) {
                    stopSelf()
                    return@collect
                }
                val running = active.filter { it.status.isActive }
                notifications.postSummary(
                    NotificationHelper.FOREGROUND_ID,
                    notifications.buildSummary(running.ifEmpty { active }, progress),
                )
            }
        }

        eventsJob?.cancel()
        eventsJob = scope.launch {
            engine.events.collect { event ->
                when (event) {
                    is DownloadEngine.Event.Completed -> {
                        repository.find(event.downloadId)?.let {
                            notifications.notifyFinished(it, null)
                        }
                    }
                    is DownloadEngine.Event.Failed -> {
                        repository.find(event.downloadId)?.let {
                            notifications.notifyFinished(it, event.message)
                        }
                    }
                    is DownloadEngine.Event.ChecksumMismatch -> {
                        repository.find(event.downloadId)?.let {
                            notifications.notifyFinished(it, null)
                        }
                    }
                    is DownloadEngine.Event.QueueIdle -> {
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        observer?.cancel()
        eventsJob?.cancel()
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.atakan.adm.action.START"
        const val ACTION_RESUME = "com.atakan.adm.action.RESUME"
        const val ACTION_PAUSE = "com.atakan.adm.action.PAUSE"
        const val ACTION_CANCEL = "com.atakan.adm.action.CANCEL"
        const val ACTION_PAUSE_ALL = "com.atakan.adm.action.PAUSE_ALL"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(context: Context, downloadId: Long) =
            send(context, ACTION_START, downloadId)

        fun resume(context: Context, downloadId: Long) =
            send(context, ACTION_RESUME, downloadId)

        fun sync(context: Context) = send(context, ACTION_START, -1L)

        private fun send(context: Context, action: String, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}

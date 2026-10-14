package com.atakan.adm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.atakan.adm.download.DownloadEngine
import com.atakan.adm.service.NotificationHelper
import com.atakan.adm.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AdmApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var notifications: NotificationHelper

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        applicationScope.launch {
            // Rows that claimed to be running when the process died are resumable.
            engine.recoverAfterProcessDeath()
        }
    }
}

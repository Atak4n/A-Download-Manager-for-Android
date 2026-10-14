package com.atakan.adm.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.atakan.adm.MainActivity
import com.atakan.adm.R
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.download.DownloadEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick settings tile that opens the app straight on the add-link sheet. */
@AndroidEntryPoint
class AddUrlTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.tile_add_url)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_ADD_URL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From API 34 the Intent overload is no longer allowed.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Quick settings tile that pauses everything that is running. */
@AndroidEntryPoint
class PauseQueueTileService : TileService() {

    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var downloadDao: DownloadDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val active = downloadDao.activeCount()
            val queued = downloadDao.byStatus(DownloadStatus.QUEUED).size
            qsTile?.apply {
                label = getString(R.string.tile_pause_queue)
                state = if (active + queued > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            engine.pauseAll()
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

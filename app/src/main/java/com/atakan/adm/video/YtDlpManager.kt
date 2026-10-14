package com.atakan.adm.video

import android.content.Context
import com.atakan.adm.data.settings.SettingsRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded yt-dlp / ffmpeg runtime: one time init, update checks and
 * the actual process invocations.
 */
@Singleton
class YtDlpManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val initMutex = Mutex()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    @Volatile
    private var initialized = false

    /** Unpacks the Python runtime on first use; safe to call repeatedly. */
    suspend fun ensureInitialized(): Boolean {
        if (initialized) return true
        return initMutex.withLock {
            if (initialized) return@withLock true
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    _version.value = YoutubeDL.getInstance().version(context)
                }
                initialized = true
                _initError.value = null
                true
            } catch (error: Exception) {
                _initError.value = error.message ?: error::class.java.simpleName
                false
            }
        }
    }

    /**
     * Checks the stable channel at most once a day unless [force] is set.
     * Returns a human readable result, or null when nothing was attempted.
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateResult {
        if (!ensureInitialized()) {
            return UpdateResult.Failed(_initError.value ?: "init failed")
        }
        val now = System.currentTimeMillis()
        val last = settings.current().ytdlpLastUpdateCheck
        if (!force && now - last < UPDATE_INTERVAL_MILLIS) return UpdateResult.Skipped
        return try {
            val status = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            }
            settings.setYtdlpLastUpdateCheck(now)
            withContext(Dispatchers.IO) {
                _version.value = YoutubeDL.getInstance().version(context)
            }
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> UpdateResult.Updated(_version.value)
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> UpdateResult.UpToDate
                null -> UpdateResult.Failed("Update failed")
            }
        } catch (error: Exception) {
            UpdateResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult
        data object UpToDate : UpdateResult
        data object Skipped : UpdateResult
        data class Failed(val message: String) : UpdateResult
    }

    /** Runs "yt-dlp -J" and returns the format list for the quality picker. */
    suspend fun fetchInfo(url: String, cookieFile: File? = null): VideoInfo =
        withContext(Dispatchers.IO) {
            if (!ensureInitialized()) {
                throw IllegalStateException(_initError.value ?: "yt-dlp not initialised")
            }
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                cookieFile?.let { addOption("--cookies", it.absolutePath) }
            }
            YoutubeDL.getInstance().getInfo(request)
        }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    companion object {
        private const val UPDATE_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}

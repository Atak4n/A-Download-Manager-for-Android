package com.atakan.adm.video

import android.content.Context
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.core.util.FileNames
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.data.storage.FileStore
import com.atakan.adm.download.ProgressSnapshot
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException

/**
 * Runs one yt-dlp download: format selection, subtitles, container handling,
 * then publishes the produced file through [FileStore].
 */
class VideoDownloadTask(
    private val context: Context,
    private val downloadId: Long,
    private val ytDlp: YtDlpManager,
    private val downloadDao: DownloadDao,
    private val fileStore: FileStore,
    private val settings: SettingsRepository,
    private val onProgress: suspend (ProgressSnapshot) -> Unit,
) {
    sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val processId = "adm-$downloadId"

    suspend fun run(): Outcome {
        val entity = downloadDao.find(downloadId) ?: return Outcome.Failed("Download not found")
        val logFile = File(entity.logFilePath ?: fileStore.logFile(downloadId).absolutePath)
        // The yt-dlp call blocks, so coroutine cancellation alone would not stop
        // it: kill the process as soon as this job is cancelled.
        val cancelHook = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause != null) ytDlp.cancel(processId)
        }
        return try {
            if (!ytDlp.ensureInitialized()) {
                throw IOException(ytDlp.initError.value ?: "yt-dlp could not start")
            }
            execute(entity, logFile)
        } catch (cancellation: CancellationException) {
            ytDlp.cancel(processId)
            withContext(NonCancellable) {
                downloadDao.setStatus(downloadId, DownloadStatus.PAUSED)
                downloadDao.setPhase(downloadId, null)
            }
            Outcome.Paused
        } catch (error: Exception) {
            // A killed process surfaces as YoutubeDL.CanceledException, which is
            // a pause, not a failure.
            if (error is YoutubeDL.CanceledException || !coroutineContext.job.isActive) {
                withContext(NonCancellable) {
                    downloadDao.setStatus(downloadId, DownloadStatus.PAUSED)
                    downloadDao.setPhase(downloadId, null)
                }
                return Outcome.Paused
            }
            val message = error.message ?: error::class.java.simpleName
            withContext(NonCancellable) {
                appendLog(logFile, "ERROR: $message")
                downloadDao.setFailed(
                    downloadId, DownloadStatus.FAILED, message, System.currentTimeMillis(),
                )
                downloadDao.setPhase(downloadId, null)
            }
            Outcome.Failed(message)
        } finally {
            cancelHook.dispose()
        }
    }

    private suspend fun execute(entity: DownloadEntity, logFile: File): Outcome {
        val appSettings = settings.current()
        val workDir = File(fileStore.partDir(), "video-$downloadId").apply { mkdirs() }
        workDir.listFiles()?.forEach { it.delete() }

        val cookieFile = entity.cookieHost?.let { host ->
            CookieFileWriter.write(File(workDir, "cookies.txt"), host, entity.cookieHeader)
        }

        val audioOnly = entity.kind == DownloadKind.AUDIO
        val container = runCatching {
            VideoContainer.valueOf(entity.container ?: VideoContainer.MP4.name)
        }.getOrDefault(VideoContainer.MP4)
        val subtitleMode = runCatching {
            SubtitleMode.valueOf(entity.subtitleMode ?: SubtitleMode.NONE.name)
        }.getOrDefault(SubtitleMode.NONE)

        val request = YoutubeDLRequest(entity.url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--no-mtime")
            addOption("-o", File(workDir, "out.%(ext)s").absolutePath)
            entity.videoFormatId?.let { addOption("-f", it) }
            cookieFile?.let { addOption("--cookies", it.absolutePath) }

            if (audioOnly) {
                addOption("--extract-audio")
                addOption("--audio-format", if (entity.container == "mp3") "mp3" else "m4a")
            } else if (container != VideoContainer.NONE) {
                val name = container.name.lowercase()
                addOption("--merge-output-format", name)
                // Remux keeps the streams as they are; recoding would re-encode.
                addOption("--remux-video", name)
            }

            when (subtitleMode) {
                SubtitleMode.NONE -> Unit
                SubtitleMode.EMBED -> {
                    addOption("--write-subs")
                    addOption("--embed-subs")
                    addOption("--sub-langs", entity.subtitleLanguages ?: appSettings.subtitleLanguages)
                    if (entity.includeAutoSubs) addOption("--write-auto-subs")
                }
                SubtitleMode.SEPARATE -> {
                    addOption("--write-subs")
                    addOption("--convert-subs", "srt")
                    addOption("--sub-langs", entity.subtitleLanguages ?: appSettings.subtitleLanguages)
                    if (entity.includeAutoSubs) addOption("--write-auto-subs")
                }
            }

            if (appSettings.speedLimitKbps > 0) {
                addOption("--limit-rate", "${appSettings.speedLimitKbps}K")
            }
        }

        appendLog(logFile, "$ yt-dlp ${request.buildCommand().joinToString(" ")}")
        downloadDao.update(
            entity.copy(
                status = DownloadStatus.RUNNING,
                startedAt = entity.startedAt ?: System.currentTimeMillis(),
                errorMessage = null,
                phase = context.getString(com.atakan.adm.R.string.video_phase_downloading),
            ),
        )

        val response = withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request, processId) { progress, etaSeconds, line ->
                // Callback arrives on the yt-dlp reader thread.
                handleLine(entity, logFile, progress, etaSeconds, line)
            }
        }
        appendLog(logFile, response.out)
        if (response.err.isNotBlank()) appendLog(logFile, response.err)
        if (response.exitCode != 0) {
            throw IOException(response.err.lines().lastOrNull { it.isNotBlank() } ?: "yt-dlp exit ${response.exitCode}")
        }

        return publish(entity, workDir, logFile)
    }

    /** Parses yt-dlp output for phase and progress; never throws. */
    private fun handleLine(
        entity: DownloadEntity,
        logFile: File,
        progress: Float,
        etaSeconds: Long,
        line: String,
    ) {
        appendLog(logFile, line)
        val phaseRes = when {
            line.contains("[Merger]") || line.contains("Merging formats") ->
                com.atakan.adm.R.string.video_phase_merging
            line.contains("[VideoConvertor]") || line.contains("[ExtractAudio]") ||
                line.contains("[VideoRemuxer]") || line.contains("Converting") ->
                com.atakan.adm.R.string.video_phase_converting
            else -> com.atakan.adm.R.string.video_phase_downloading
        }
        lastPhase = context.getString(phaseRes)
        lastProgress = progress
        lastEta = etaSeconds
    }

    @Volatile private var lastPhase: String? = null
    @Volatile private var lastProgress: Float = 0f
    @Volatile private var lastEta: Long = -1L

    /** Snapshot for the UI; the engine polls this while the process runs. */
    fun snapshot(totalBytes: Long): ProgressSnapshot = ProgressSnapshot(
        downloadId = downloadId,
        downloadedBytes = if (totalBytes > 0) {
            (totalBytes * (lastProgress / 100f)).toLong()
        } else {
            0L
        },
        totalBytes = totalBytes,
        bytesPerSecond = 0L,
        phase = lastPhase,
    )

    private suspend fun publish(
        entity: DownloadEntity,
        workDir: File,
        logFile: File,
    ): Outcome = withContext(NonCancellable) {
        val produced = workDir.listFiles()
            ?.filter { it.isFile && it.name != "cookies.txt" && !it.name.endsWith(".part") }
            ?.sortedByDescending { it.length() }
            .orEmpty()
        val main = produced.firstOrNull()
            ?: throw IOException("yt-dlp produced no file")

        val appSettings = settings.current()
        val baseName = entity.fileName.takeIf { it.isNotBlank() && it != "download" }
            ?: main.name
        val extension = FileNames.extensionOf(main.name)
        val desired = if (extension.isNotEmpty() && !baseName.endsWith(".$extension", true)) {
            "${FileNames.sanitize(baseName.substringBeforeLast('.'))}.$extension"
        } else {
            FileNames.sanitize(baseName)
        }
        val category = if (entity.kind == DownloadKind.AUDIO) Category.MUSIC else Category.VIDEO
        val displayName = fileStore.uniqueDisplayName(
            desired = desired,
            treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
            category = category,
            useSubfolder = appSettings.useCategorySubfolders,
        )

        val published = fileStore.publish(
            part = main,
            displayName = displayName,
            mimeType = null,
            category = category,
            treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
            useSubfolder = appSettings.useCategorySubfolders,
        )

        // Separate subtitle files travel with the video.
        produced.drop(1).filter { it.name.endsWith(".srt") || it.name.endsWith(".vtt") }
            .forEach { subtitle ->
                runCatching {
                    fileStore.publish(
                        part = subtitle,
                        displayName = fileStore.uniqueDisplayName(
                            desired = subtitle.name,
                            treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
                            category = category,
                            useSubfolder = appSettings.useCategorySubfolders,
                        ),
                        mimeType = "application/x-subrip",
                        category = category,
                        treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
                        useSubfolder = appSettings.useCategorySubfolders,
                    )
                }
            }

        val size = published.path?.let { File(it).length() } ?: main.length()
        downloadDao.update(
            entity.copy(
                status = DownloadStatus.COMPLETED,
                fileName = published.displayName,
                finalUri = published.uri,
                category = category,
                totalBytes = if (size > 0) size else entity.totalBytes,
                downloadedBytes = if (size > 0) size else entity.downloadedBytes,
                finishedAt = System.currentTimeMillis(),
                phase = null,
            ),
        )
        workDir.deleteRecursively()
        appendLog(logFile, "Saved as ${published.displayName}")
        Outcome.Completed
    }

    private fun appendLog(logFile: File, text: String) {
        if (text.isBlank()) return
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText(text.trimEnd() + "\n")
        }
    }
}

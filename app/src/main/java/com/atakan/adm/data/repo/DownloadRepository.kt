package com.atakan.adm.data.repo

import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.core.util.Checksum
import com.atakan.adm.core.util.FileNames
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.db.SegmentDao
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.db.entity.SegmentEntity
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.data.storage.FileStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI / capture paths hand over when adding a download. */
data class NewDownload(
    val url: String,
    val fileName: String? = null,
    val kind: DownloadKind = DownloadKind.FILE,
    val connections: Int? = null,
    val checksum: String? = null,
    val scheduledAt: Long? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val cookieHeader: String? = null,
    val cookieHost: String? = null,
    val mimeType: String? = null,
    val targetDirUri: String? = null,
    /** Known or estimated size, used for video progress before bytes are known. */
    val estimatedBytes: Long? = null,
    // video options
    val videoFormatId: String? = null,
    val videoAudioFormatId: String? = null,
    val videoQualityLabel: String? = null,
    val container: VideoContainer? = null,
    /** "m4a" or "mp3" for audio only downloads; overrides [container]. */
    val audioFormat: String? = null,
    val subtitleMode: SubtitleMode? = null,
    val subtitleLanguages: String? = null,
    val includeAutoSubs: Boolean? = null,
)

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val segmentDao: SegmentDao,
    private val settings: SettingsRepository,
    private val fileStore: FileStore,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    fun observe(id: Long): Flow<DownloadEntity?> = downloadDao.observe(id)

    fun observeSegments(id: Long): Flow<List<SegmentEntity>> = segmentDao.observeForDownload(id)

    suspend fun find(id: Long): DownloadEntity? = downloadDao.find(id)

    suspend fun segments(id: Long): List<SegmentEntity> = segmentDao.forDownload(id)

    suspend fun create(request: NewDownload): Long {
        val appSettings = settings.current()
        val fallbackName = request.fileName
            ?: FileNames.fromUrl(request.url)
            ?: "download"
        val name = FileNames.sanitize(fallbackName)
        val normalizedChecksum = Checksum.normalize(request.checksum)
        val entity = DownloadEntity(
            url = request.url,
            fileName = name,
            mimeType = request.mimeType,
            category = if (request.kind == DownloadKind.AUDIO) {
                Category.MUSIC
            } else {
                FileNames.categoryFor(name)
            },
            kind = request.kind,
            status = if (request.scheduledAt != null) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED,
            totalBytes = request.estimatedBytes ?: -1L,
            connections = (request.connections ?: appSettings.connectionsPerDownload)
                .coerceIn(1, 16),
            targetDirUri = request.targetDirUri ?: appSettings.targetDirUri,
            partFilePath = "", // filled in below, needs the row id
            referer = request.referer,
            userAgent = request.userAgent,
            cookieHeader = request.cookieHeader,
            cookieHost = request.cookieHost,
            createdAt = System.currentTimeMillis(),
            scheduledAt = request.scheduledAt,
            maxRetries = appSettings.autoRetryCount,
            checksumExpected = normalizedChecksum,
            checksumAlgorithm = normalizedChecksum?.let { Checksum.detect(it)?.name },
            videoFormatId = request.videoFormatId,
            videoAudioFormatId = request.videoAudioFormatId,
            videoQualityLabel = request.videoQualityLabel,
            container = request.audioFormat ?: (request.container ?: appSettings.defaultContainer).name,
            subtitleMode = (request.subtitleMode ?: appSettings.subtitleMode).name,
            subtitleLanguages = request.subtitleLanguages ?: appSettings.subtitleLanguages,
            includeAutoSubs = request.includeAutoSubs ?: appSettings.includeAutoSubs,
        )
        val id = downloadDao.insert(entity)
        val part = fileStore.partFile(id)
        downloadDao.update(
            entity.copy(
                id = id,
                partFilePath = part.absolutePath,
                logFilePath = if (request.kind == DownloadKind.FILE) null else fileStore.logFile(id).absolutePath,
            ),
        )
        return id
    }

    suspend fun setChecksum(id: Long, raw: String?) {
        val entity = downloadDao.find(id) ?: return
        val normalized = Checksum.normalize(raw)
        downloadDao.update(
            entity.copy(
                checksumExpected = normalized,
                checksumAlgorithm = normalized?.let { Checksum.detect(it)?.name },
                checksumActual = null,
                checksumVerified = null,
            ),
        )
    }

    suspend fun setConnections(id: Long, connections: Int) {
        val entity = downloadDao.find(id) ?: return
        downloadDao.update(entity.copy(connections = connections.coerceIn(1, 16)))
    }

    suspend fun setSchedule(id: Long, atMillis: Long?) {
        val entity = downloadDao.find(id) ?: return
        downloadDao.update(
            entity.copy(
                scheduledAt = atMillis,
                status = if (atMillis != null) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED,
            ),
        )
    }

    suspend fun dueScheduled(now: Long = System.currentTimeMillis()): List<DownloadEntity> =
        downloadDao.dueScheduled(now)

    suspend fun queued(): List<DownloadEntity> = downloadDao.byStatus(DownloadStatus.QUEUED)
}

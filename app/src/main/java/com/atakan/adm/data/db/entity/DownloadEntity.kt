package com.atakan.adm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.DownloadStatus

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** URL as supplied by the user / capture path. */
    val url: String,
    /** Final URL after redirects; requests resume against this one. */
    val resolvedUrl: String? = null,
    val fileName: String,
    val mimeType: String? = null,
    val category: Category = Category.OTHER,
    val kind: DownloadKind = DownloadKind.FILE,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** -1 when the server did not advertise a length. */
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val supportsRange: Boolean = false,
    /** Connections requested for this item (1..16). */
    val connections: Int = 4,
    /** Connections actually in use; lowered by the 429/503 backoff logic. */
    val activeConnections: Int = 0,
    val etag: String? = null,
    val lastModified: String? = null,
    /** SAF tree uri chosen by the user, null means the app default folder. */
    val targetDirUri: String? = null,
    /** Filesystem directory when not using SAF. */
    val targetDirPath: String? = null,
    /** Scratch .part file inside app-owned storage. */
    val partFilePath: String,
    /** MediaStore / SAF uri of the published file once finished. */
    val finalUri: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    /** Cookie header captured for [cookieHost] only; never replayed cross-host. */
    val cookieHeader: String? = null,
    val cookieHost: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val scheduledAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val checksumExpected: String? = null,
    val checksumAlgorithm: String? = null,
    val checksumActual: String? = null,
    val checksumVerified: Boolean? = null,
    // ---- video (yt-dlp) fields ----
    val videoFormatId: String? = null,
    val videoAudioFormatId: String? = null,
    val videoQualityLabel: String? = null,
    val container: String? = null,
    val subtitleMode: String? = null,
    val subtitleLanguages: String? = null,
    val includeAutoSubs: Boolean = false,
    /** Human readable yt-dlp phase, surfaced in the UI. */
    val phase: String? = null,
    val logFilePath: String? = null,
)

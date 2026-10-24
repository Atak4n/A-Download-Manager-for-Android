package com.atakan.adm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.atakan.adm.core.model.SegmentStatus

@Entity(
    tableName = "segments",
    indices = [Index("downloadId")],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val downloadId: Long,
    val index: Int,
    /** Absolute offset of the first byte this segment owns. */
    val startByte: Long,
    /** Inclusive absolute offset of the last byte, -1 when open ended. */
    val endByte: Long,
    /** Bytes already written for this segment; resume offset is start + this. */
    val downloadedBytes: Long = 0L,
    val status: SegmentStatus = SegmentStatus.PENDING,
    val attempts: Int = 0,
    val errorMessage: String? = null,
) {
    /** Next absolute byte offset to request. */
    val currentOffset: Long get() = startByte + downloadedBytes

    /** Total bytes this segment must deliver, or -1 when open ended. */
    val length: Long get() = if (endByte < 0) -1L else endByte - startByte + 1

    /** Remaining bytes left to download in this segment, or 0 if complete/unbounded. */
    val remainingBytes: Long get() = if (endByte < 0) 0L else (length - downloadedBytes).coerceAtLeast(0L)

    val isComplete: Boolean get() = endByte >= 0 && downloadedBytes >= length
}

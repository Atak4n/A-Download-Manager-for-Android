package com.atakan.adm.download

import com.atakan.adm.core.model.SegmentStatus

data class SegmentProgress(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val status: SegmentStatus,
    val bytesPerSecond: Long = 0L,
    val attempts: Int = 0,
) {
    val length: Long get() = if (endByte < 0) -1L else endByte - startByte + 1
}

data class ProgressSnapshot(
    val downloadId: Long,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val segments: List<SegmentProgress> = emptyList(),
    /** Newest sample last; drives the detail screen graph. */
    val speedHistory: List<Long> = emptyList(),
    val phase: String? = null,
) {
    val remainingBytes: Long get() = if (totalBytes <= 0) -1L else totalBytes - downloadedBytes

    val etaSeconds: Long?
        get() = if (totalBytes <= 0 || bytesPerSecond <= 0) null
        else ((totalBytes - downloadedBytes) / bytesPerSecond).coerceAtLeast(0)
}

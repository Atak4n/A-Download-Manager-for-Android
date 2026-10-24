package com.atakan.adm.download

import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.data.db.entity.SegmentEntity

/**
 * Splits a byte range into contiguous segments. Pure logic, covered by unit tests.
 */
object SegmentPlanner {

    /** Below this size extra connections cost more than they gain. */
    const val MIN_SEGMENT_BYTES = 1L shl 20 // 1 MB

    const val MAX_CONNECTIONS = 16

    data class Range(val start: Long, val endInclusive: Long) {
        val length: Long get() = endInclusive - start + 1
    }

    /**
     * Returns [connections] contiguous ranges covering 0 until [totalBytes].
     * The connection count is clamped so each segment holds at least
     * [MIN_SEGMENT_BYTES], and the remainder is spread over the first segments
     * so the ranges stay gap free.
     */
    fun plan(totalBytes: Long, connections: Int): List<Range> {
        if (totalBytes <= 0) return listOf(Range(0, -1))
        val desired = connections.coerceIn(1, MAX_CONNECTIONS)
        val affordable = (totalBytes / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
        val count = minOf(desired, affordable)
        val base = totalBytes / count
        val remainder = totalBytes % count

        val ranges = ArrayList<Range>(count)
        var cursor = 0L
        for (index in 0 until count) {
            val length = base + if (index < remainder) 1 else 0
            ranges += Range(cursor, cursor + length - 1)
            cursor += length
        }
        return ranges
    }

    fun toEntities(downloadId: Long, ranges: List<Range>): List<SegmentEntity> =
        ranges.mapIndexed { index, range ->
            SegmentEntity(
                downloadId = downloadId,
                index = index,
                startByte = range.start,
                endByte = range.endInclusive,
                downloadedBytes = 0L,
                status = SegmentStatus.PENDING,
            )
        }

    /** Single open ended segment, used when the server has no Range support. */
    fun singleSegment(downloadId: Long, totalBytes: Long): List<SegmentEntity> = listOf(
        SegmentEntity(
            downloadId = downloadId,
            index = 0,
            startByte = 0L,
            endByte = if (totalBytes > 0) totalBytes - 1 else -1L,
            downloadedBytes = 0L,
            status = SegmentStatus.PENDING,
        ),
    )

    /**
     * Bytes already on disk for a plan; the resume offset of each segment is
     * [SegmentEntity.currentOffset].
     */
    fun completedBytes(segments: List<SegmentEntity>): Long =
        segments.sumOf { it.downloadedBytes }

    /** Segments that still have work left. */
    fun pending(segments: List<SegmentEntity>): List<SegmentEntity> =
        segments.filterNot { it.isComplete }

    /**
     * Finds the segment with the largest remaining byte work that can be split into two.
     * To be eligible, the remaining un-downloaded portion must be at least 2 * [MIN_SEGMENT_BYTES].
     * Returns a Pair of:
     * - newEndByte for the donor segment (clamped to its current progress + half remaining)
     * - New SegmentEntity for the new sub-segment covering (newEndByte + 1 .. original endByte)
     * Or null if no segment has enough remaining work to justify splitting.
     */
    fun splitLargestPending(
        segments: List<SegmentEntity>,
        newIndex: Int,
    ): Pair<Long, SegmentEntity>? {
        val candidate = segments
            .filter { !it.isComplete && it.endByte > 0 }
            .maxByOrNull { it.remainingBytes }
            ?: return null

        val remaining = candidate.remainingBytes
        if (remaining < 2 * MIN_SEGMENT_BYTES) return null

        val splitSize = remaining / 2
        val newEndForDonor = candidate.startByte + candidate.downloadedBytes + splitSize - 1
        val newSegmentStart = newEndForDonor + 1
        val newSegmentEnd = candidate.endByte

        val newSegment = SegmentEntity(
            downloadId = candidate.downloadId,
            index = newIndex,
            startByte = newSegmentStart,
            endByte = newSegmentEnd,
            downloadedBytes = 0L,
            status = SegmentStatus.PENDING,
        )
        return Pair(newEndForDonor, newSegment)
    }
}

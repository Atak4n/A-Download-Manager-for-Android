package com.atakan.adm.download

import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.data.db.entity.SegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {

    @Test
    fun `plan covers the whole file without gaps or overlap`() {
        val total = 100L * 1024 * 1024 + 7 // deliberately not divisible
        val ranges = SegmentPlanner.plan(total, 8)

        assertEquals(8, ranges.size)
        assertEquals(0L, ranges.first().start)
        assertEquals(total - 1, ranges.last().endInclusive)
        assertEquals(total, ranges.sumOf { it.length })
        ranges.zipWithNext().forEach { (a, b) ->
            assertEquals(a.endInclusive + 1, b.start)
        }
    }

    @Test
    fun `remainder is spread over the first segments`() {
        val ranges = SegmentPlanner.plan(10L * 1024 * 1024 + 3, 4)
        val lengths = ranges.map { it.length }
        // Three segments carry one extra byte, none differ by more than one.
        assertEquals(3, lengths.count { it == lengths.min() + 1 })
        assertTrue(lengths.max() - lengths.min() <= 1)
    }

    @Test
    fun `connection count is clamped so segments stay useful`() {
        // 3 MB cannot usefully be split into 16 connections at 1 MB minimum.
        val ranges = SegmentPlanner.plan(3L * 1024 * 1024, 16)
        assertEquals(3, ranges.size)
        assertTrue(ranges.all { it.length >= SegmentPlanner.MIN_SEGMENT_BYTES })
    }

    @Test
    fun `connection count is capped at sixteen`() {
        val ranges = SegmentPlanner.plan(64L * 1024 * 1024, 99)
        assertEquals(SegmentPlanner.MAX_CONNECTIONS, ranges.size)
    }

    @Test
    fun `unknown length yields one open ended segment`() {
        val ranges = SegmentPlanner.plan(-1, 8)
        assertEquals(1, ranges.size)
        assertEquals(0L, ranges.first().start)
        assertEquals(-1L, ranges.first().endInclusive)
    }

    @Test
    fun `resume offset is start plus bytes already written`() {
        val segment = SegmentEntity(
            id = 1,
            downloadId = 1,
            index = 1,
            startByte = 1_000,
            endByte = 1_999,
            downloadedBytes = 400,
        )
        assertEquals(1_400L, segment.currentOffset)
        assertEquals(1_000L, segment.length)
        assertFalse(segment.isComplete)
        assertTrue(segment.copy(downloadedBytes = 1_000).isComplete)
    }

    @Test
    fun `pending skips finished segments and totals are summed`() {
        val segments = listOf(
            SegmentEntity(id = 1, downloadId = 1, index = 0, startByte = 0, endByte = 99, downloadedBytes = 100, status = SegmentStatus.DONE),
            SegmentEntity(id = 2, downloadId = 1, index = 1, startByte = 100, endByte = 199, downloadedBytes = 25),
        )
        assertEquals(125L, SegmentPlanner.completedBytes(segments))
        assertEquals(listOf(1), SegmentPlanner.pending(segments).map { it.index })
    }

    @Test
    fun `entities mirror the planned ranges`() {
        val ranges = SegmentPlanner.plan(8L * 1024 * 1024, 4)
        val entities = SegmentPlanner.toEntities(downloadId = 42, ranges = ranges)
        assertEquals(4, entities.size)
        entities.forEachIndexed { index, entity ->
            assertEquals(42L, entity.downloadId)
            assertEquals(index, entity.index)
            assertEquals(ranges[index].start, entity.startByte)
            assertEquals(ranges[index].endInclusive, entity.endByte)
            assertEquals(0L, entity.downloadedBytes)
        }
    }

    @Test
    fun `single segment is used when the server has no range support`() {
        val segments = SegmentPlanner.singleSegment(downloadId = 7, totalBytes = 500)
        assertEquals(1, segments.size)
        assertEquals(0L, segments.first().startByte)
        assertEquals(499L, segments.first().endByte)

        val unknown = SegmentPlanner.singleSegment(downloadId = 7, totalBytes = -1)
        assertEquals(-1L, unknown.first().endByte)
    }

    @Test
    fun `splitLargestPending divides segment with sufficient remaining bytes`() {
        val segments = listOf(
            SegmentEntity(id = 1, downloadId = 42, index = 0, startByte = 0, endByte = 10L * 1024 * 1024 - 1, downloadedBytes = 2L * 1024 * 1024),
            SegmentEntity(id = 2, downloadId = 42, index = 1, startByte = 10L * 1024 * 1024, endByte = 12L * 1024 * 1024 - 1, downloadedBytes = 1L * 1024 * 1024),
        )
        // Segment 0 has 8MB remaining (> 2MB min). It should be split into 4MB + 4MB.
        val result = SegmentPlanner.splitLargestPending(segments, newIndex = 2)
        org.junit.Assert.assertNotNull(result)
        val (newDonorEnd, newSegment) = result!!

        assertEquals(0 + 2L * 1024 * 1024 + 4L * 1024 * 1024 - 1, newDonorEnd)
        assertEquals(newDonorEnd + 1, newSegment.startByte)
        assertEquals(10L * 1024 * 1024 - 1, newSegment.endByte)
        assertEquals(2, newSegment.index)
        assertEquals(42L, newSegment.downloadId)
        assertEquals(0L, newSegment.downloadedBytes)
    }

    @Test
    fun `splitLargestPending returns null when remaining bytes are below threshold`() {
        val segments = listOf(
            SegmentEntity(id = 1, downloadId = 42, index = 0, startByte = 0, endByte = 1L * 1024 * 1024, downloadedBytes = 500L * 1024),
        )
        // Remaining is ~500KB (< 2MB min)
        val result = SegmentPlanner.splitLargestPending(segments, newIndex = 1)
        org.junit.Assert.assertNull(result)
    }
}

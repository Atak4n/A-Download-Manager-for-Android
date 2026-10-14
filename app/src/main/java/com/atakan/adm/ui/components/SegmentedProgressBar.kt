package com.atakan.adm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.download.SegmentProgress

/**
 * One track per connection, drawn in file order: each slice is as wide as the
 * byte range its connection owns and filled by how much of it has arrived.
 */
@Composable
fun SegmentedProgressBar(
    segments: List<SegmentProgress>,
    totalBytes: Long,
    fallbackProgress: Float,
    modifier: Modifier = Modifier,
    trackHeight: Int = 8,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant
    val fill = scheme.primary
    val failed = scheme.error
    val idle = scheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, size = size, cornerRadius = radius)

        if (segments.isEmpty() || totalBytes <= 0) {
            val width = size.width * fallbackProgress.coerceIn(0f, 1f)
            if (width > 0f) {
                drawRoundRect(
                    color = fill,
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
            return@Canvas
        }

        val gap = if (segments.size > 1) 2f else 0f
        segments.forEach { segment ->
            val length = if (segment.length > 0) segment.length else totalBytes
            val startFraction = segment.startByte.toFloat() / totalBytes
            val widthFraction = length.toFloat() / totalBytes
            val slotLeft = startFraction * size.width
            val slotWidth = (widthFraction * size.width - gap).coerceAtLeast(0f)
            val doneFraction = if (length > 0) {
                (segment.downloadedBytes.toFloat() / length).coerceIn(0f, 1f)
            } else {
                0f
            }

            // Slot outline so idle connections are still visible.
            drawRoundRect(
                color = idle.copy(alpha = 0.45f),
                topLeft = Offset(slotLeft, 0f),
                size = Size(slotWidth, size.height),
                cornerRadius = radius,
            )
            val color = when (segment.status) {
                SegmentStatus.FAILED -> failed
                SegmentStatus.DONE -> fill
                SegmentStatus.RUNNING -> fill
                SegmentStatus.PENDING -> fill.copy(alpha = 0.6f)
            }
            if (doneFraction > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(slotLeft, 0f),
                    size = Size(slotWidth * doneFraction, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Colour used for a status chip. */
@Composable
fun statusColor(status: com.atakan.adm.core.model.DownloadStatus): Color {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        com.atakan.adm.core.model.DownloadStatus.COMPLETED -> scheme.primary
        com.atakan.adm.core.model.DownloadStatus.FAILED -> scheme.error
        com.atakan.adm.core.model.DownloadStatus.CANCELLED -> scheme.outline
        com.atakan.adm.core.model.DownloadStatus.PAUSED -> scheme.secondary
        else -> scheme.tertiary
    }
}

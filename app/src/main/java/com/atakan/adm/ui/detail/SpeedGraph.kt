package com.atakan.adm.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Live speed samples, newest on the right. */
@Composable
fun SpeedGraph(
    samples: List<Long>,
    modifier: Modifier = Modifier,
    heightDp: Int = 96,
) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val grid = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        val baseline = size.height
        drawLine(grid, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1f)
        if (samples.size < 2) return@Canvas

        val peak = (samples.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (samples.size - 1).toFloat()
        val points = samples.mapIndexed { index, value ->
            Offset(index * stepX, baseline - (value / peak) * (size.height * 0.92f))
        }

        val area = Path().apply {
            moveTo(points.first().x, baseline)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, baseline)
            close()
        }
        drawPath(area, fill)

        val stroke = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(stroke, line, style = Stroke(width = 3f))
    }
}

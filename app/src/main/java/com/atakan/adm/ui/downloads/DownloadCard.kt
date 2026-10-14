package com.atakan.adm.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atakan.adm.R
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.util.Formatters
import com.atakan.adm.ui.components.SegmentedProgressBar
import com.atakan.adm.ui.components.statusColor
import com.atakan.adm.ui.labelRes

@Composable
fun DownloadCard(
    row: DownloadRow,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entity = row.entity
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            // Translucent so the blurred background shows through without
            // dropping contrast for the text on top.
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = entity.category.icon(),
                        contentDescription = stringResource(entity.category.labelRes()),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = entity.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sizeLine(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ActionButtons(entity.status, onPause, onResume, onCancel, onRemove)
            }

            SegmentedProgressBar(
                segments = row.progress?.segments.orEmpty(),
                totalBytes = entity.totalBytes,
                fallbackProgress = row.fraction,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = statusLine(row),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(entity.status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val eta = row.progress?.etaSeconds
                if (eta != null) {
                    Text(
                        text = stringResource(R.string.detail_eta, Formatters.duration(eta)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    status: DownloadStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Row {
        when {
            status.isActive -> IconButton(onClick = onPause) {
                Icon(Icons.Filled.Pause, stringResource(R.string.action_pause))
            }
            status == DownloadStatus.PAUSED || status == DownloadStatus.QUEUED ->
                IconButton(onClick = onResume) {
                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.action_resume))
                }
            status == DownloadStatus.FAILED -> IconButton(onClick = onResume) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.action_retry))
            }
            else -> Unit
        }
        if (status.isActive || status == DownloadStatus.PAUSED || status == DownloadStatus.QUEUED) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
            }
        } else {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_remove))
            }
        }
    }
}

@Composable
private fun sizeLine(row: DownloadRow): String {
    val total = row.entity.totalBytes
    val downloaded = row.downloadedBytes
    return when {
        row.entity.status == DownloadStatus.COMPLETED ->
            Formatters.bytes(if (total > 0) total else downloaded)
        total > 0 ->
            "${Formatters.bytes(downloaded)} / ${Formatters.bytes(total)}"
        downloaded > 0 ->
            Formatters.bytes(downloaded)
        row.entity.status == DownloadStatus.PROBING ->
            stringResource(R.string.status_probing)
        row.entity.status == DownloadStatus.QUEUED ->
            stringResource(R.string.status_queued)
        row.entity.status == DownloadStatus.SCHEDULED ->
            stringResource(R.string.status_scheduled)
        else ->
            "—"
    }
}

@Composable
private fun statusLine(row: DownloadRow): String {
    val status = stringResource(row.entity.status.labelRes())
    val phase = row.entity.phase
    val speed = row.speed
    return buildString {
        append(phase ?: status)
        if (speed > 0) {
            append(" · ")
            append(Formatters.speed(speed))
        }
        val connections = row.entity.activeConnections
        if (connections > 0 && row.entity.status.isActive) {
            append(" · ")
            append(connections)
            append("×")
        }
        row.entity.errorMessage?.let {
            append(" · ")
            append(it)
        }
    }
}

fun Category.icon(): ImageVector = when (this) {
    Category.IMAGE -> Icons.Filled.Image
    Category.MUSIC -> Icons.Filled.MusicNote
    Category.VIDEO -> Icons.Filled.Movie
    Category.APPLICATION -> Icons.Filled.Android
    Category.DOCUMENT -> Icons.Filled.Description
    Category.ARCHIVE -> Icons.Filled.Folder
    Category.OTHER -> Icons.Filled.InsertDriveFile
}

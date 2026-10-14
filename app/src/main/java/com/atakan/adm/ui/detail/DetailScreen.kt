package com.atakan.adm.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.adm.R
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.util.Formatters
import com.atakan.adm.ui.components.SegmentedProgressBar
import com.atakan.adm.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entity = state.entity

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entity?.fileName ?: stringResource(R.string.detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.browser_back))
                    }
                },
                actions = {
                    if (entity != null) {
                        if (entity.status.isActive) {
                            IconButton(onClick = viewModel::pause) {
                                Icon(Icons.Filled.Pause, stringResource(R.string.action_pause))
                            }
                            IconButton(onClick = viewModel::cancel) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                            }
                        } else if (entity.status != DownloadStatus.COMPLETED) {
                            IconButton(onClick = viewModel::resume) {
                                Icon(
                                    if (entity.status == DownloadStatus.FAILED) {
                                        Icons.Filled.Refresh
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    stringResource(R.string.action_resume),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entity == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {}
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val done = state.progress?.downloadedBytes ?: entity.downloadedBytes
            SectionCard {
                Text(
                    text = entity.phase ?: stringResource(entity.status.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = when {
                        entity.status == DownloadStatus.COMPLETED ->
                            Formatters.bytes(if (entity.totalBytes > 0) entity.totalBytes else done)
                        entity.totalBytes > 0 ->
                            "${Formatters.bytes(done)} / ${Formatters.bytes(entity.totalBytes)}"
                        done > 0 ->
                            Formatters.bytes(done)
                        entity.status == DownloadStatus.PROBING ->
                            stringResource(R.string.status_probing)
                        entity.status == DownloadStatus.QUEUED ->
                            stringResource(R.string.status_queued)
                        entity.status == DownloadStatus.SCHEDULED ->
                            stringResource(R.string.status_scheduled)
                        else ->
                            "—"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                SegmentedProgressBar(
                    segments = state.segments,
                    totalBytes = entity.totalBytes,
                    fallbackProgress = if (entity.totalBytes > 0) {
                        (done.toDouble() / entity.totalBytes).toFloat()
                    } else {
                        0f
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    trackHeight = 12,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = Formatters.speed(state.progress?.bytesPerSecond ?: 0L),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.progress?.etaSeconds?.let {
                        Text(
                            text = stringResource(R.string.detail_eta, Formatters.duration(it)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.detail_retries,
                        entity.retryCount,
                        entity.maxRetries,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entity.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.detail_speed_graph),
                    style = MaterialTheme.typography.titleSmall,
                )
                SpeedGraph(samples = state.progress?.speedHistory.orEmpty())
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.detail_connections),
                    style = MaterialTheme.typography.titleSmall,
                )
                state.segments.forEach { segment ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.detail_segment, segment.index + 1),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.detail_segment_range,
                                Formatters.bytes(segment.startByte),
                                if (segment.endByte >= 0) Formatters.bytes(segment.endByte) else "—",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = Formatters.bytes(segment.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = segment.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.detail_url),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = entity.resolvedUrl ?: entity.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entity.checksumExpected?.let { expected ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val algorithm = entity.checksumAlgorithm.orEmpty()
                    when (entity.checksumVerified) {
                        true -> Text(
                            text = stringResource(R.string.detail_checksum_ok, algorithm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        false -> Column {
                            Text(
                                text = stringResource(R.string.detail_checksum_failed, algorithm),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.detail_checksum_expected, expected),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = stringResource(
                                    R.string.detail_checksum_actual,
                                    entity.checksumActual.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        null -> Text(
                            text = stringResource(R.string.detail_checksum_expected, expected),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            state.log?.let { log ->
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.detail_log),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = viewModel::refreshLog) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.browser_reload))
                        }
                    }
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

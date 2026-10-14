package com.atakan.adm.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.adm.R
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.core.util.Formatters
import com.atakan.adm.ui.label
import com.atakan.adm.ui.labelRes
import com.atakan.adm.video.VideoFormatSelector

/** Quality picker shown for video pages: resolutions, sizes, audio only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPickerSheet(
    url: String,
    cookies: String?,
    onDismiss: () -> Unit,
    viewModel: VideoPickerViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isNotBlank() && state.url != url) viewModel.load(url, cookies)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.video_quality_title),
                style = MaterialTheme.typography.titleLarge,
            )
            state.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.video_analyzing))
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.options.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.video_container),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoContainer.entries.forEach { container ->
                        FilterChip(
                            selected = state.container == container,
                            onClick = { viewModel.setContainer(container) },
                            label = {
                                Text(
                                    container.label().ifEmpty {
                                        stringResource(R.string.video_container_none)
                                    },
                                )
                            },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.video_subtitles),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.subtitleMode == mode,
                            onClick = { viewModel.setSubtitleMode(mode) },
                            label = { Text(stringResource(mode.labelRes())) },
                        )
                    }
                }
                if (state.subtitleMode != SubtitleMode.NONE) {
                    OutlinedTextField(
                        value = state.subtitleLanguages,
                        onValueChange = viewModel::setSubtitleLanguages,
                        label = { Text(stringResource(R.string.video_subtitle_langs)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.video_auto_subs),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.includeAutoSubs,
                            onCheckedChange = viewModel::setIncludeAutoSubs,
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                state.options.forEach { option ->
                    OptionRow(option) {
                        viewModel.download(option)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: VideoFormatSelector.Option, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (option.isAudioOnly) Icons.Filled.MusicNote else Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (option.isAudioOnly) {
                    "${stringResource(R.string.video_audio_only)} · ${option.label}"
                } else {
                    option.label
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = buildString {
                    append(option.ext.uppercase())
                    if (option.fps > 0) append(" · ${option.fps}fps")
                    if (!option.isProgressive && !option.isAudioOnly) append(" · +audio")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (option.estimatedBytes > 0) {
                stringResource(R.string.video_estimated_size, Formatters.bytes(option.estimatedBytes))
            } else {
                "—"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

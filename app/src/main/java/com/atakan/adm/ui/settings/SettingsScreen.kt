package com.atakan.adm.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.adm.R
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.data.settings.ThemeMode
import com.atakan.adm.ui.label
import com.atakan.adm.ui.labelRes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ytDlpVersion: String?,
    onCheckYtDlpUpdate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> viewModel.setTargetDir(uri) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_downloads))
            SliderRow(
                label = stringResource(R.string.settings_max_concurrent, state.maxConcurrentDownloads),
                value = state.maxConcurrentDownloads.toFloat(),
                range = 1f..10f,
                steps = 8,
                onChange = { viewModel.setMaxConcurrent(it.roundToInt()) },
            )
            SliderRow(
                label = stringResource(R.string.settings_connections, state.connectionsPerDownload),
                value = state.connectionsPerDownload.toFloat(),
                range = 1f..16f,
                steps = 14,
                onChange = { viewModel.setConnections(it.roundToInt()) },
            )
            SliderRow(
                label = if (state.speedLimitKbps <= 0) {
                    "${stringResource(R.string.settings_speed_limit)}: ${stringResource(R.string.settings_speed_unlimited)}"
                } else {
                    "${stringResource(R.string.settings_speed_limit)}: ${
                        stringResource(R.string.settings_speed_limit_value, state.speedLimitKbps)
                    }"
                },
                value = state.speedLimitKbps.toFloat(),
                range = 0f..20_480f,
                steps = 0,
                onChange = { viewModel.setSpeedLimit(it.roundToInt()) },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_retry))
            SliderRow(
                label = stringResource(R.string.settings_retry_count, state.autoRetryCount),
                value = state.autoRetryCount.toFloat(),
                range = 0f..10f,
                steps = 9,
                onChange = { viewModel.setRetryCount(it.roundToInt()) },
            )
            SliderRow(
                label = stringResource(R.string.settings_retry_delay, state.autoRetryDelaySeconds),
                value = state.autoRetryDelaySeconds.toFloat(),
                range = 0f..600f,
                steps = 0,
                onChange = { viewModel.setRetryDelay(it.roundToInt()) },
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_storage))
            Text(
                text = stringResource(R.string.settings_folder),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = state.targetDirUri ?: stringResource(R.string.settings_folder_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { folderPicker.launch(null) }) {
                    Text(stringResource(R.string.settings_pick_folder))
                }
                if (state.targetDirUri != null) {
                    TextButton(onClick = { viewModel.setTargetDir(null) }) {
                        Text(stringResource(R.string.settings_folder_default))
                    }
                }
            }
            SwitchRow(
                label = stringResource(R.string.settings_category_folders),
                checked = state.useCategorySubfolders,
                onChange = viewModel::setCategoryFolders,
            )
            SwitchRow(
                label = stringResource(R.string.settings_wifi_only),
                checked = state.wifiOnly,
                onChange = viewModel::setWifiOnly,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_capture))
            SwitchRow(
                label = stringResource(R.string.settings_clipboard),
                checked = state.watchClipboard,
                onChange = viewModel::setWatchClipboard,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_video))
            Text(stringResource(R.string.video_container), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoContainer.entries.forEach { container ->
                    FilterChip(
                        selected = state.defaultContainer == container,
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
            Text(stringResource(R.string.video_subtitles), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.subtitleMode == mode,
                        onClick = { viewModel.setSubtitleMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }
            OutlinedTextField(
                value = state.subtitleLanguages,
                onValueChange = viewModel::setSubtitleLanguages,
                label = { Text(stringResource(R.string.video_subtitle_langs)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                label = stringResource(R.string.video_auto_subs),
                checked = state.includeAutoSubs,
                onChange = viewModel::setIncludeAutoSubs,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_appearance))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }
            SwitchRow(
                label = stringResource(R.string.settings_dynamic_color),
                checked = state.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )
            SwitchRow(
                label = stringResource(R.string.settings_blur),
                checked = state.blurEffects,
                onChange = viewModel::setBlur,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_ytdlp_version, ytDlpVersion ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onCheckYtDlpUpdate) {
                Text(stringResource(R.string.settings_ytdlp_update))
            }

            Text(
                text = stringResource(R.string.settings_developed_by),
                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 40.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

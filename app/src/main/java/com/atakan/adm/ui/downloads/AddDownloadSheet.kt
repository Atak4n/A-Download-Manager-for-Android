package com.atakan.adm.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atakan.adm.R
import com.atakan.adm.capture.UrlExtractor
import com.atakan.adm.core.util.Checksum
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Sheet used by the + button, the share intent and the clipboard prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    initialUrl: String,
    defaultConnections: Int,
    onDismiss: () -> Unit,
    onVideoRequested: (String) -> Unit,
    onConfirm: (url: String, fileName: String?, connections: Int, checksum: String?, scheduledAt: Long?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf(initialUrl) }
    var fileName by remember { mutableStateOf("") }
    var checksum by remember { mutableStateOf("") }
    var connections by remember { mutableFloatStateOf(defaultConnections.toFloat()) }
    var scheduled by remember { mutableStateOf(false) }
    var scheduledAt by remember { mutableLongStateOf(System.currentTimeMillis() + 60 * 60 * 1000L) }

    val checksumInvalid = checksum.isNotBlank() && Checksum.normalize(checksum) == null
    val urlValid = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.field_url)) },
                isError = url.isNotBlank() && !urlValid,
                supportingText = if (url.isNotBlank() && !urlValid) {
                    { Text(stringResource(R.string.url_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(stringResource(R.string.field_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.field_connections, connections.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = connections,
                onValueChange = { connections = it },
                valueRange = 1f..16f,
                steps = 14,
            )
            OutlinedTextField(
                value = checksum,
                onValueChange = { checksum = it },
                label = { Text(stringResource(R.string.field_checksum)) },
                isError = checksumInvalid,
                supportingText = if (checksumInvalid) {
                    { Text(stringResource(R.string.field_checksum_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.field_schedule))
                    if (scheduled) {
                        Text(
                            text = stringResource(
                                R.string.field_schedule_at,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(scheduledAt)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = scheduled, onCheckedChange = { scheduled = it })
            }
            if (scheduled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1L, 3L, 8L, 24L).forEach { hours ->
                        TextButton(onClick = {
                            scheduledAt = System.currentTimeMillis() + hours * 3_600_000L
                        }) {
                            Text("+${hours}h")
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (urlValid) {
                    TextButton(onClick = { onVideoRequested(url.trim()) }) {
                        Text(stringResource(R.string.video_quality_title))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                Button(
                    onClick = {
                        val trimmedUrl = url.trim()
                        if (UrlExtractor.looksLikeVideo(trimmedUrl)) {
                            onVideoRequested(trimmedUrl)
                        } else {
                            onConfirm(
                                trimmedUrl,
                                fileName.trim().takeIf { it.isNotBlank() },
                                connections.roundToInt(),
                                checksum.trim().takeIf { it.isNotBlank() },
                                if (scheduled) scheduledAt else null,
                            )
                        }
                    },
                    enabled = urlValid && !checksumInvalid,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        }
    }
}

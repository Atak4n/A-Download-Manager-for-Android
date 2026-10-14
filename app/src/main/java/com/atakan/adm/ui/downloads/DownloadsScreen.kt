package com.atakan.adm.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.adm.R
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.SortOrder
import com.atakan.adm.core.util.Formatters
import com.atakan.adm.ui.components.DepthBackground
import com.atakan.adm.ui.labelRes
import com.atakan.adm.ui.video.QualityPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onOpenDetail: (Long) -> Unit,
    pendingUrl: String?,
    onPendingUrlConsumed: () -> Unit,
    blurEnabled: Boolean,
    defaultConnections: Int,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var sheetUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingUrl) {
        if (!pendingUrl.isNullOrBlank()) {
            sheetUrl = pendingUrl
            showSheet = true
            onPendingUrlConsumed()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.downloads_title))
                        if (state.activeCount > 0) {
                            Text(
                                text = "${state.activeCount} · ${Formatters.speed(state.totalSpeed)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                ),
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, stringResource(R.string.search_hint))
                    }
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.Filled.Sort, stringResource(R.string.sort_by))
                    }
                    DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.labelRes())) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSort = false
                                },
                            )
                        }
                    }
                    if (state.activeCount > 0) {
                        IconButton(onClick = viewModel::pauseAll) {
                            Icon(Icons.Filled.Pause, stringResource(R.string.action_pause_all))
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sheetUrl = ""
                    showSheet = true
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_download)) },
            )
        },
    ) { padding ->
        DepthBackground(Modifier.fillMaxSize(), enabled = blurEnabled) {
            Column(Modifier.padding(padding)) {
                if (showSearch) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.category == null,
                            onClick = { viewModel.setCategory(null) },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                    }
                    items(Category.entries) { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = {
                                viewModel.setCategory(
                                    if (state.category == category) null else category,
                                )
                            },
                            label = { Text(stringResource(category.labelRes())) },
                        )
                    }
                }

                if (state.rows.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.downloads_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.rows, key = { it.entity.id }) { row ->
                            DownloadCard(
                                row = row,
                                onClick = { onOpenDetail(row.entity.id) },
                                onPause = { viewModel.pause(row.entity.id) },
                                onResume = { viewModel.resume(row.entity.id) },
                                onCancel = { viewModel.cancel(row.entity.id) },
                                onRemove = { viewModel.remove(row.entity.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        AddDownloadSheet(
            initialUrl = sheetUrl,
            defaultConnections = defaultConnections,
            onDismiss = { showSheet = false },
            onVideoRequested = { url ->
                showSheet = false
                videoUrl = url
            },
            onConfirm = { url, name, connections, checksum, scheduledAt ->
                viewModel.addDownload(url, name, connections, checksum, scheduledAt)
                showSheet = false
            },
        )
    }

    videoUrl?.let { url ->
        QualityPickerSheet(
            url = url,
            cookies = null,
            onDismiss = { videoUrl = null },
        )
    }
}

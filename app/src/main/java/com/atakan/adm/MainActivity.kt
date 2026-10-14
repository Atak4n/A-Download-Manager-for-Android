package com.atakan.adm

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.atakan.adm.capture.UrlExtractor
import com.atakan.adm.data.settings.AppSettings
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.service.DownloadService
import com.atakan.adm.ui.browser.BrowserScreen
import com.atakan.adm.ui.detail.DetailScreen
import com.atakan.adm.ui.downloads.DownloadsScreen
import com.atakan.adm.ui.navigation.AdmTab
import com.atakan.adm.ui.navigation.Routes
import com.atakan.adm.ui.settings.SettingsScreen
import com.atakan.adm.ui.theme.ADMTheme
import com.atakan.adm.video.YtDlpManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ytDlpManager: YtDlpManager

    /** URL handed in by share / view intents or the clipboard watcher. */
    private val pendingUrl = MutableStateFlow<String?>(null)
    private val pendingDetailId = MutableStateFlow<Long?>(null)
    private var lastClipboardUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)


        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val url by pendingUrl.collectAsStateWithLifecycle()
            val detailId by pendingDetailId.collectAsStateWithLifecycle()

            ADMTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                NotificationPermissionGate()
                AdmApp(
                    settings = settings,
                    pendingUrl = url,
                    onPendingUrlConsumed = { pendingUrl.value = null },
                    pendingDetailId = detailId,
                    onPendingDetailConsumed = { pendingDetailId.value = null },
                    ytDlpVersion = ytDlpManager.version.collectAsStateWithLifecycle().value,
                    onCheckYtDlpUpdate = {
                        lifecycleScope.launch { ytDlpManager.checkForUpdate(force = true) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L).takeIf { it > 0 }?.let {
            pendingDetailId.value = it
        }
        val candidate = when (intent.action) {
            Intent.ACTION_SEND -> UrlExtractor.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            ACTION_ADD_URL -> UrlExtractor.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        }
        candidate?.let { pendingUrl.value = it }
    }

    /**
     * Android 10+ only exposes the clipboard to the foreground app, so this runs
     * on resume and never in the background.
     */
    private fun checkClipboard() {
        lifecycleScope.launch {
            if (!settingsRepository.current().watchClipboard) return@launch
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@launch
            val text = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this@MainActivity)
                ?.toString()
            val url = UrlExtractor.firstUrl(text) ?: return@launch
            if (url == lastClipboardUrl) return@launch
            lastClipboardUrl = url
            clipboardCandidate.value = url
        }
    }

    private val clipboardCandidate = MutableStateFlow<String?>(null)

    @Composable
    private fun NotificationPermissionGate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        var asked by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !asked) {
                asked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    private fun AdmApp(
        settings: AppSettings,
        pendingUrl: String?,
        onPendingUrlConsumed: () -> Unit,
        pendingDetailId: Long?,
        onPendingDetailConsumed: () -> Unit,
        ytDlpVersion: String?,
        onCheckYtDlpUpdate: () -> Unit,
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val clipboard by clipboardCandidate.collectAsStateWithLifecycle()
        var queuedUrl by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(pendingDetailId) {
            pendingDetailId?.let {
                navController.navigate(Routes.detail(it))
                onPendingDetailConsumed()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (currentRoute?.startsWith("detail") != true) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ) {
                        AdmTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(AdmTab.DOWNLOADS.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AdmTab.DOWNLOADS.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable(AdmTab.DOWNLOADS.route) {
                    DownloadsScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it)) },
                        pendingUrl = pendingUrl ?: queuedUrl,
                        onPendingUrlConsumed = {
                            onPendingUrlConsumed()
                            queuedUrl = null
                        },
                        blurEnabled = settings.blurEffects,
                        defaultConnections = settings.connectionsPerDownload,
                    )
                }
                composable(AdmTab.BROWSER.route) {
                    BrowserScreen()
                }
                composable(AdmTab.SETTINGS.route) {
                    SettingsScreen(
                        ytDlpVersion = ytDlpVersion,
                        onCheckYtDlpUpdate = onCheckYtDlpUpdate,
                    )
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_DOWNLOAD_ID) { type = NavType.StringType }),
                ) {
                    DetailScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        clipboard?.let { url ->
            AlertDialog(
                onDismissRequest = { clipboardCandidate.value = null },
                title = { Text(stringResource(R.string.clipboard_prompt_title)) },
                text = { Text(stringResource(R.string.clipboard_prompt_text, url)) },
                confirmButton = {
                    TextButton(onClick = {
                        queuedUrl = url
                        clipboardCandidate.value = null
                        navController.navigate(AdmTab.DOWNLOADS.route) {
                            popUpTo(AdmTab.DOWNLOADS.route) { inclusive = true }
                        }
                    }) { Text(stringResource(R.string.action_download)) }
                },
                dismissButton = {
                    TextButton(onClick = { clipboardCandidate.value = null }) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                },
            )
        }
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val ACTION_ADD_URL = "com.atakan.adm.action.ADD_URL"
    }
}

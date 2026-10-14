package com.atakan.adm.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.adm.R
import com.atakan.adm.capture.UrlExtractor
import com.atakan.adm.ui.video.QualityPickerSheet

private const val DEFAULT_HOMEPAGE = "https://www.google.com"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var addressField by remember { mutableStateOf(DEFAULT_HOMEPAGE) }
    var currentUrl by remember { mutableStateOf(DEFAULT_HOMEPAGE) }
    var progress by remember { mutableIntStateOf(0) }
    var desktopMode by remember { mutableStateOf(false) }
    var mediaUrls by remember { mutableStateOf(listOf<String>()) }
    var pickerUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val queued by viewModel.lastQueued.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                val cookies = CookieManager.getInstance().getCookie(url)
                viewModel.enqueue(
                    url = url,
                    cookies = cookies,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    referer = this.url,
                )
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = if (newProgress in 1..99) newProgress else 0
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentUrl = url
                    addressField = url
                    mediaUrls = emptyList()
                    progress = 15
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    addressField = url
                    progress = 0
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val urlStr = request.url.toString()
                    if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                        return false
                    }
                    return runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                        context.startActivity(intent)
                        true
                    }.getOrDefault(true)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (UrlExtractor.looksLikeStream(url) || isMediaFile(url)) {
                        view.post {
                            if (!mediaUrls.contains(url)) {
                                mediaUrls = (mediaUrls + url).takeLast(20)
                            }
                        }
                    }
                    return null
                }
            }

            // Immediately load the default home page!
            loadUrl(DEFAULT_HOMEPAGE)
        }.also {
            webViewInstance = it
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Address input field
        OutlinedTextField(
            value = addressField,
            onValueChange = { addressField = it },
            label = { Text(stringResource(R.string.browser_url_hint)) },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = { webViewInstance?.loadUrl(normalizeQuery(addressField)) },
                onDone = { webViewInstance?.loadUrl(normalizeQuery(addressField)) },
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Go,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // Browser navigation bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = { webViewInstance?.goBack() },
                enabled = canGoBack,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_back))
            }
            IconButton(
                onClick = { webViewInstance?.goForward() },
                enabled = canGoForward,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.browser_forward))
            }
            IconButton(onClick = { webViewInstance?.reload() }) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.browser_reload))
            }
            IconButton(onClick = {
                addressField = DEFAULT_HOMEPAGE
                webViewInstance?.loadUrl(DEFAULT_HOMEPAGE)
            }) {
                Icon(Icons.Filled.Home, stringResource(R.string.browser_home))
            }
            IconButton(onClick = {
                desktopMode = !desktopMode
                webViewInstance?.settings?.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else null
                webViewInstance?.reload()
            }) {
                Icon(
                    Icons.Filled.DesktopWindows,
                    stringResource(R.string.browser_desktop_site),
                    tint = if (desktopMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = { CookieManager.getInstance().removeAllCookies(null) }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.browser_clear_cookies))
            }
        }

        // Quick bookmarks row
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Google" to "https://www.google.com",
                "YouTube" to "https://m.youtube.com",
                "Archive.org" to "https://archive.org",
                "GitHub" to "https://github.com",
            ).forEach { (label, targetUrl) ->
                FilterChip(
                    selected = currentUrl.startsWith(targetUrl),
                    onClick = {
                        addressField = targetUrl
                        webViewInstance?.loadUrl(targetUrl)
                    },
                    label = { Text(label) },
                )
            }
        }

        // Loading progress indicator
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Video detection action button
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            Button(
                onClick = { pickerUrl = mediaUrls.firstOrNull() ?: currentUrl },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Movie, null, Modifier.padding(end = 8.dp))
                Text(
                    if (mediaUrls.isEmpty()) {
                        stringResource(R.string.browser_download_video)
                    } else {
                        stringResource(R.string.browser_videos_found, mediaUrls.size)
                    },
                )
            }
        }

        // WebView viewport
        Box(
            Modifier
                .fillMaxSize()
                .weight(1f, fill = true),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            queued?.let { name ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        Button(onClick = viewModel::consumeQueued) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                ) {
                    Text(name)
                }
            }
        }
    }

    pickerUrl?.let { url ->
        QualityPickerSheet(
            url = url,
            cookies = CookieManager.getInstance().getCookie(url),
            onDismiss = { pickerUrl = null },
        )
    }
}

private fun isMediaFile(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return listOf(".mp4", ".webm", ".mkv", ".m4a", ".mp3", ".ts").any { path.endsWith(it) }
}

private fun normalizeQuery(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val looksLikeHost = !trimmed.contains(' ') && trimmed.contains('.')
    return if (looksLikeHost) {
        "https://$trimmed"
    } else {
        "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }
}

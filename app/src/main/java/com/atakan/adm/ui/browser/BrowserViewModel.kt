package com.atakan.adm.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.adm.core.util.FileNames
import com.atakan.adm.data.repo.DownloadRepository
import com.atakan.adm.data.repo.NewDownload
import com.atakan.adm.download.HttpProber
import com.atakan.adm.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
) : ViewModel() {

    private val _lastQueued = MutableStateFlow<String?>(null)
    val lastQueued: StateFlow<String?> = _lastQueued.asStateFlow()

    /**
     * Called from WebView.setDownloadListener. The session cookies are attached
     * for the download host only, never carried to another host.
     */
    fun enqueue(
        url: String,
        cookies: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
    ) {
        viewModelScope.launch {
            val name = FileNames.resolve(url, contentDisposition, mimeType)
            val id = repository.create(
                NewDownload(
                    url = url,
                    fileName = name,
                    mimeType = mimeType,
                    userAgent = userAgent,
                    cookieHeader = cookies,
                    cookieHost = HttpProber.hostOf(url),
                    referer = referer,
                ),
            )
            DownloadService.start(context, id)
            _lastQueued.value = name
        }
    }

    fun consumeQueued() {
        _lastQueued.value = null
    }
}

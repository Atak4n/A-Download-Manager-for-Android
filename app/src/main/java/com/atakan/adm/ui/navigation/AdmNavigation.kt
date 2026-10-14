package com.atakan.adm.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.atakan.adm.R

/** Bottom navigation destinations. */
enum class AdmTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.Download),
    BROWSER("browser", R.string.nav_browser, Icons.Filled.Public),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

object Routes {
    const val DETAIL = "detail/{downloadId}"
    fun detail(downloadId: Long) = "detail/$downloadId"
    const val ARG_DOWNLOAD_ID = "downloadId"
}

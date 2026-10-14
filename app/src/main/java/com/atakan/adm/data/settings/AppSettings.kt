package com.atakan.adm.data.settings

import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer

/** Snapshot of every user setting, read from DataStore. */
data class AppSettings(
    val maxConcurrentDownloads: Int = 3,
    val connectionsPerDownload: Int = 4,
    /** Aggregate cap over every transfer, in KB/s. 0 means unlimited. */
    val speedLimitKbps: Int = 0,
    val useCategorySubfolders: Boolean = false,
    /** SAF tree uri, null means Downloads/A Download Manager via MediaStore. */
    val targetDirUri: String? = null,
    val autoRetryCount: Int = 3,
    val autoRetryDelaySeconds: Int = 30,
    val watchClipboard: Boolean = true,
    val wifiOnly: Boolean = false,
    val defaultContainer: VideoContainer = VideoContainer.MP4,
    val subtitleMode: SubtitleMode = SubtitleMode.NONE,
    val subtitleLanguages: String = "tr,en",
    val includeAutoSubs: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val blurEffects: Boolean = true,
    val ytdlpLastUpdateCheck: Long = 0L,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

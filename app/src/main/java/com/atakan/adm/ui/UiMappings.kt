package com.atakan.adm.ui

import androidx.annotation.StringRes
import com.atakan.adm.R
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SortOrder
import com.atakan.adm.core.model.SubtitleMode
import com.atakan.adm.core.model.VideoContainer
import com.atakan.adm.data.settings.ThemeMode

@StringRes
fun DownloadStatus.labelRes(): Int = when (this) {
    DownloadStatus.QUEUED -> R.string.status_queued
    DownloadStatus.PROBING -> R.string.status_probing
    DownloadStatus.RUNNING -> R.string.status_running
    DownloadStatus.PAUSED -> R.string.status_paused
    DownloadStatus.MERGING -> R.string.status_merging
    DownloadStatus.CONVERTING -> R.string.status_converting
    DownloadStatus.VERIFYING -> R.string.status_verifying
    DownloadStatus.COMPLETED -> R.string.status_completed
    DownloadStatus.FAILED -> R.string.status_failed
    DownloadStatus.CANCELLED -> R.string.status_cancelled
    DownloadStatus.SCHEDULED -> R.string.status_scheduled
}

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.IMAGE -> R.string.category_image
    Category.MUSIC -> R.string.category_music
    Category.VIDEO -> R.string.category_video
    Category.APPLICATION -> R.string.category_application
    Category.DOCUMENT -> R.string.category_document
    Category.ARCHIVE -> R.string.category_archive
    Category.OTHER -> R.string.category_other
}

@StringRes
fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.DATE -> R.string.sort_date
    SortOrder.NAME -> R.string.sort_name
    SortOrder.SIZE -> R.string.sort_size
    SortOrder.STATUS -> R.string.sort_status
    SortOrder.SPEED -> R.string.sort_speed
}

@StringRes
fun SubtitleMode.labelRes(): Int = when (this) {
    SubtitleMode.NONE -> R.string.video_subtitles_off
    SubtitleMode.EMBED -> R.string.video_subtitles_embed
    SubtitleMode.SEPARATE -> R.string.video_subtitles_separate
}

fun VideoContainer.label(): String = when (this) {
    VideoContainer.MP4 -> "MP4"
    VideoContainer.MKV -> "MKV"
    VideoContainer.WEBM -> "WebM"
    VideoContainer.NONE -> ""
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

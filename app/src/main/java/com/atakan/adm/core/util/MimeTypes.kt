package com.atakan.adm.core.util

import android.webkit.MimeTypeMap

/** Thin wrapper over [MimeTypeMap] with offline fallbacks for the common cases. */
object MimeTypes {

    private val EXTRA_BY_MIME = mapOf(
        "application/vnd.android.package-archive" to "apk",
        "application/x-apple-diskimage" to "dmg",
        "application/x-7z-compressed" to "7z",
        "application/x-rar-compressed" to "rar",
        "video/x-matroska" to "mkv",
        "audio/mp4" to "m4a",
        "application/vnd.apple.mpegurl" to "m3u8",
        "application/dash+xml" to "mpd",
    )

    private val EXTRA_BY_EXTENSION = EXTRA_BY_MIME.entries.associate { (k, v) -> v to k }

    fun extensionFor(mimeType: String?): String {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.isEmpty()) return ""
        EXTRA_BY_MIME[mime]?.let { return it }
        return runCatching { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) }
            .getOrNull().orEmpty()
    }

    fun mimeFor(fileName: String): String? {
        val ext = FileNames.extensionOf(fileName)
        if (ext.isEmpty()) return null
        EXTRA_BY_EXTENSION[ext]?.let { return it }
        return runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }.getOrNull()
    }
}

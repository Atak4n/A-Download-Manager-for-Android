package com.atakan.adm.capture

/** Pulls a downloadable URL out of shared text or an incoming intent. */
object UrlExtractor {

    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Shared text often carries a title plus the link; take the first link. */
    fun firstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val direct = text.trim()
        if (direct.startsWith("http://", true) || direct.startsWith("https://", true)) {
            return direct.substringBefore(' ')
        }
        return URL_PATTERN.find(text)?.value
    }

    /** File extensions worth intercepting from a plain link tap. */
    val FILE_EXTENSIONS = listOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "img", "dmg", "pkg",
        "apk", "apks", "xapk", "aab", "exe", "msi", "deb", "rpm", "appimage", "bin", "jar",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "mobi", "csv",
        "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac",
        "mp4", "mkv", "webm", "avi", "mov", "flv", "m4v", "ts", "3gp",
        "jpg", "jpeg", "png", "gif", "webp", "svg", "heic", "avif",
        "torrent",
    )

    /** Streaming manifests that mean "this is a video page", not a plain file. */
    val STREAM_EXTENSIONS = listOf("m3u8", "mpd")

    fun looksLikeStream(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) || url.contains(".mpd", ignoreCase = true)

    fun looksLikeFile(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return FILE_EXTENSIONS.any { path.endsWith(".$it") }
    }
}

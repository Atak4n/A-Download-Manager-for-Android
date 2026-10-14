package com.atakan.adm.core.util

import com.atakan.adm.core.model.Category
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Filename and category helpers. Pure functions so they are unit testable. */
object FileNames {

    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
    private const val MAX_LENGTH = 180

    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "tif", "tiff", "ico", "avif")
    private val MUSIC = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff", "alac", "mid", "midi")
    private val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "mpg", "mpeg", "ts", "m2ts", "3gp", "m3u8", "mpd")
    private val APPLICATION = setOf("apk", "apks", "apkm", "xapk", "aab", "exe", "msi", "dmg", "pkg", "deb", "rpm", "appimage", "bin", "jar")
    private val DOCUMENT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "epub", "mobi", "csv", "md", "json", "xml")
    private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "img", "tgz", "zst", "lz4", "cab")

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..8 } ?: ""

    fun categoryFor(name: String): Category = when (extensionOf(name)) {
        in IMAGE -> Category.IMAGE
        in MUSIC -> Category.MUSIC
        in VIDEO -> Category.VIDEO
        in APPLICATION -> Category.APPLICATION
        in DOCUMENT -> Category.DOCUMENT
        in ARCHIVE -> Category.ARCHIVE
        else -> Category.OTHER
    }

    /** Strips path separators and control characters, keeps the extension. */
    fun sanitize(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = ILLEGAL.replace(trimmed, "_").trim('.', ' ')
        if (cleaned.isEmpty()) return "download"
        if (cleaned.length <= MAX_LENGTH) return cleaned
        val ext = extensionOf(cleaned)
        val stem = cleaned.removeSuffix(".$ext")
        val keep = (MAX_LENGTH - ext.length - 1).coerceAtLeast(1)
        return if (ext.isEmpty()) stem.take(MAX_LENGTH) else stem.take(keep) + "." + ext
    }

    /**
     * Picks a filename from the Content-Disposition header, falling back to the URL path
     * and finally to a generic name with an extension guessed from the mime type.
     */
    fun resolve(url: String, contentDisposition: String?, mimeType: String?): String {
        fromContentDisposition(contentDisposition)?.let { return sanitize(it) }
        fromUrl(url)?.let { return sanitize(it) }
        val ext = MimeTypes.extensionFor(mimeType)
        return sanitize(if (ext.isEmpty()) "download" else "download.$ext")
    }

    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // RFC 5987: filename*=UTF-8''name.ext wins over the plain filename.
        Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.let { match ->
                val charset = match.groupValues[1].trim().ifEmpty { "UTF-8" }
                val value = match.groupValues[2].trim()
                return runCatching { URLDecoder.decode(value, charset) }
                    .getOrElse { runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull() }
                    ?.takeIf { it.isNotBlank() }
            }
        Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(header)?.let { return it.groupValues[1].takeIf { n -> n.isNotBlank() } }
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.let { return it.groupValues[1].trim().takeIf { n -> n.isNotBlank() } }
        return null
    }

    fun fromUrl(url: String): String? {
        val withoutQuery = url.substringBefore('#').substringBefore('?')
        val last = withoutQuery.trimEnd('/').substringAfterLast('/')
        if (last.isBlank() || !last.contains('.')) return null
        val decoded = runCatching { URLDecoder.decode(last, StandardCharsets.UTF_8.name()) }
            .getOrDefault(last)
        return decoded.takeIf { it.isNotBlank() }
    }

    /**
     * Turns "file.zip" into "file (2).zip" when [exists] reports a collision,
     * incrementing until a free name is found.
     */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        if (!exists(desired)) return desired
        val ext = extensionOf(desired)
        val stem = if (ext.isEmpty()) desired else desired.removeSuffix(".$ext")
        var counter = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem ($counter)" else "$stem ($counter).$ext"
            if (!exists(candidate)) return candidate
            counter++
        }
    }
}

package com.atakan.adm.video

import java.io.File

/**
 * Writes a Netscape cookie file for yt-dlp from a "a=b; c=d" header. Cookies
 * are only ever written for the host they were captured on.
 */
object CookieFileWriter {

    fun write(target: File, host: String, cookieHeader: String?): File? {
        if (cookieHeader.isNullOrBlank() || host.isBlank()) return null
        val pairs = cookieHeader.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty() || !trimmed.contains('=')) return@mapNotNull null
                val name = trimmed.substringBefore('=').trim()
                val value = trimmed.substringAfter('=').trim()
                if (name.isEmpty()) null else name to value
            }
        if (pairs.isEmpty()) return null

        val domain = if (host.startsWith(".")) host else ".$host"
        target.parentFile?.mkdirs()
        target.bufferedWriter().use { writer ->
            writer.write("# Netscape HTTP Cookie File\n")
            pairs.forEach { (name, value) ->
                // domain, includeSubdomains, path, secure, expiry, name, value
                writer.write("$domain\tTRUE\t/\tTRUE\t0\t$name\t$value\n")
            }
        }
        // yt-dlp refuses world readable cookie files on some platforms.
        runCatching { target.setReadable(true, true) }
        return target
    }
}

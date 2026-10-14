package com.atakan.adm.download

import com.atakan.adm.core.util.FileNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves URLs, detects byte length and confirms Range support.
 */
@Singleton
class HttpProber @Inject constructor(
    private val client: OkHttpClient,
) {
    data class Credentials(
        val cookieHeader: String? = null,
        val cookieHost: String? = null,
        val referer: String? = null,
        val userAgent: String? = null,
    )

    data class Probe(
        val resolvedUrl: String,
        val totalBytes: Long,
        val supportsRange: Boolean,
        val etag: String?,
        val lastModified: String?,
        val fileName: String,
        val mimeType: String?,
        val credentialsKept: Boolean,
    )

    suspend fun probe(url: String, credentials: Credentials): Probe {
        val initialHost = hostOf(url)
        var totalBytes = -1L
        var supportsRange = false
        var etag: String? = null
        var lastModified: String? = null
        var disposition: String? = null
        var mimeType: String? = null
        var finalUrl = url
        var finalHost = initialHost

        // 1. HEAD probe (redirects are followed by SafeRedirectInterceptor)
        val headResult = runCatching {
            call(url, credentials, initialHost, range = null, head = true)
        }.getOrNull()

        headResult?.use { response ->
            if (response.isSuccessful) {
                finalUrl = response.request.url.toString()
                finalHost = response.request.url.host
                totalBytes = response.header("Content-Length")?.toLongOrNull() ?: -1L
                supportsRange = response.header("Accept-Ranges")
                    ?.contains("bytes", ignoreCase = true) == true
                etag = response.header("ETag")
                lastModified = response.header("Last-Modified")
                disposition = response.header("Content-Disposition")
                mimeType = response.header("Content-Type")
            }
        }

        // 2. If HEAD failed or did not prove Range/length: GET with Range: bytes=0-0
        if (!supportsRange || totalBytes <= 0) {
            val rangeResult = runCatching {
                call(finalUrl, credentials, initialHost, range = "bytes=0-0", head = false)
            }.getOrNull()

            rangeResult?.use { response ->
                if (response.isSuccessful) {
                    finalUrl = response.request.url.toString()
                    finalHost = response.request.url.host
                    if (response.code == 206) {
                        supportsRange = true
                        parseContentRangeTotal(response.header("Content-Range"))?.let { totalBytes = it }
                    } else if (response.code == 200) {
                        supportsRange = false
                        response.header("Content-Length")?.toLongOrNull()?.let { totalBytes = it }
                    }
                    etag = etag ?: response.header("ETag")
                    lastModified = lastModified ?: response.header("Last-Modified")
                    disposition = disposition ?: response.header("Content-Disposition")
                    mimeType = mimeType ?: response.header("Content-Type")
                }
            }
        }

        // 3. If totalBytes is still unknown, try normal GET (read response headers)
        if (totalBytes <= 0) {
            val getResult = runCatching {
                call(finalUrl, credentials, initialHost, range = null, head = false)
            }.getOrNull()

            getResult?.use { response ->
                if (response.isSuccessful) {
                    finalUrl = response.request.url.toString()
                    finalHost = response.request.url.host
                    response.header("Content-Length")?.toLongOrNull()?.let { totalBytes = it }
                    etag = etag ?: response.header("ETag")
                    lastModified = lastModified ?: response.header("Last-Modified")
                    disposition = disposition ?: response.header("Content-Disposition")
                    mimeType = mimeType ?: response.header("Content-Type")
                }
            }
        }

        val credentialsKept = initialHost != null && finalHost != null && hostMatches(finalHost, initialHost)

        return Probe(
            resolvedUrl = finalUrl,
            totalBytes = totalBytes,
            supportsRange = supportsRange && totalBytes > 0,
            etag = etag,
            lastModified = lastModified,
            fileName = FileNames.resolve(finalUrl, disposition, mimeType),
            mimeType = mimeType?.substringBefore(';')?.trim(),
            credentialsKept = credentialsKept,
        )
    }

    internal fun buildRequest(
        url: String,
        credentials: Credentials,
        allowedHost: String?,
        range: String?,
        head: Boolean,
    ): Request {
        val builder = Request.Builder().url(url)
        if (head) builder.head()
        range?.let { builder.header("Range", it) }
        credentials.userAgent?.let { builder.header("User-Agent", it) }
        val host = hostOf(url)
        if (allowedHost != null && host != null && hostMatches(host, allowedHost)) {
            credentials.cookieHeader?.let { builder.header("Cookie", it) }
            credentials.referer?.let { builder.header("Referer", it) }
        }
        builder.header("Accept-Encoding", "identity")
        return builder.build()
    }

    private fun call(
        url: String,
        credentials: Credentials,
        allowedHost: String?,
        range: String?,
        head: Boolean,
    ): Response = client.newCall(buildRequest(url, credentials, allowedHost, range, head)).execute()

    companion object {
        fun hostOf(url: String): String? =
            url.toHttpUrlOrNull()?.host

        fun hostMatches(host: String, allowed: String): Boolean =
            host.equals(allowed, ignoreCase = true) ||
                host.endsWith(".$allowed", ignoreCase = true)

        /** "bytes 0-0/12345" -> 12345 */
        fun parseContentRangeTotal(header: String?): Long? {
            val total = header?.substringAfterLast('/')?.trim() ?: return null
            if (total == "*" || total.isEmpty()) return null
            return total.toLongOrNull()
        }
    }
}

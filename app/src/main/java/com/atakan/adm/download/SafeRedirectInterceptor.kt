package com.atakan.adm.download

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Follows HTTP redirects safely while guaranteeing that user credentials
 * (Cookie, Authorization, Referer) are NEVER passed across different hosts.
 * Also provides a standard browser User-Agent when none was set.
 */
class SafeRedirectInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.header("User-Agent") == null) {
            request = request.newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
        }

        var response = chain.proceed(request)
        var redirects = 0

        while (response.isRedirect && redirects < MAX_REDIRECTS) {
            redirects++
            val location = response.header("Location") ?: break
            val nextUrl = response.request.url.resolve(location) ?: break
            val isCrossHost = !nextUrl.host.equals(request.url.host, ignoreCase = true)

            val nextRequestBuilder = request.newBuilder().url(nextUrl)
            if (isCrossHost) {
                nextRequestBuilder.removeHeader("Cookie")
                nextRequestBuilder.removeHeader("Authorization")
                nextRequestBuilder.removeHeader("Referer")
            }

            if (response.code == 303 && request.method != "GET" && request.method != "HEAD") {
                nextRequestBuilder.get()
            }

            response.close()
            request = nextRequestBuilder.build()
            response = chain.proceed(request)
        }

        return response
    }

    companion object {
        const val MAX_REDIRECTS = 10
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

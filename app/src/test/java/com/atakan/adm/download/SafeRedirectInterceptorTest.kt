package com.atakan.adm.download

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SafeRedirectInterceptorTest {

    private lateinit var server1: MockWebServer
    private lateinit var server2: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server1 = MockWebServer()
        server2 = MockWebServer()
        server1.start()
        server2.start()

        client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SafeRedirectInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server1.close()
        server2.close()
    }

    @Test
    fun `same host redirect keeps credentials`() {
        server1.enqueue(
            MockResponse.Builder()
                .code(302)
                .setHeader("Location", "/final")
                .build(),
        )
        server1.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("done")
                .build(),
        )

        val request = Request.Builder()
            .url(server1.url("/start"))
            .header("Cookie", "session=123")
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        server1.takeRequest() // /start
        val second = server1.takeRequest() // /final
        assertEquals("session=123", second.headers["Cookie"])
    }

    @Test
    fun `cross host redirect strips cookie and referer`() {
        val targetUrl = server2.url("/destination")
        server1.enqueue(
            MockResponse.Builder()
                .code(302)
                .setHeader("Location", targetUrl.toString())
                .build(),
        )
        server2.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("landing")
                .build(),
        )

        val request = Request.Builder()
            .url(server1.url("/landing-start"))
            .header("Cookie", "secret_token=abc")
            .header("Referer", "https://origin.com/secret")
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        server1.takeRequest() // initial
        val crossHostRequest = server2.takeRequest()
        assertNull(crossHostRequest.headers["Cookie"])
        assertNull(crossHostRequest.headers["Referer"])
    }
}

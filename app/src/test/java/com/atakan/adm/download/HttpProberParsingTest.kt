package com.atakan.adm.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpProberParsingTest {

    @Test
    fun `content range total is parsed`() {
        assertEquals(12_345L, HttpProber.parseContentRangeTotal("bytes 0-0/12345"))
        assertEquals(500L, HttpProber.parseContentRangeTotal("bytes 100-199/500"))
        assertNull(HttpProber.parseContentRangeTotal("bytes 0-0/*"))
        assertNull(HttpProber.parseContentRangeTotal(null))
    }

    @Test
    fun `credentials only travel to the capture host or its subdomains`() {
        assertTrue(HttpProber.hostMatches("example.com", "example.com"))
        assertTrue(HttpProber.hostMatches("cdn.example.com", "example.com"))
        assertFalse(HttpProber.hostMatches("example.com.evil.net", "example.com"))
        assertFalse(HttpProber.hostMatches("other.net", "example.com"))
    }

    @Test
    fun `retry after header is honoured within sane bounds`() {
        assertEquals(5_000L, DownloadTask.retryAfterMillis("5"))
        assertEquals(1_000L, DownloadTask.retryAfterMillis("0"))
        assertEquals(DownloadTask.MAX_BACKOFF_MILLIS, DownloadTask.retryAfterMillis("99999"))
        assertNull(DownloadTask.retryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(DownloadTask.retryAfterMillis(null))
    }
}

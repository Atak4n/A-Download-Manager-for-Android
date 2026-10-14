package com.atakan.adm.core

import com.atakan.adm.core.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes scale to readable units`() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.00 KB", Formatters.bytes(1024))
        assertEquals("1.50 MB", Formatters.bytes(1024 * 1024 * 3 / 2))
        assertEquals("—", Formatters.bytes(-1))
    }

    @Test
    fun `eta is null when it cannot be estimated`() {
        assertNull(Formatters.etaSeconds(remainingBytes = 100, bytesPerSecond = 0))
        assertNull(Formatters.etaSeconds(remainingBytes = 0, bytesPerSecond = 100))
        assertEquals(10L, Formatters.etaSeconds(remainingBytes = 1_000, bytesPerSecond = 100))
    }

    @Test
    fun `duration switches to hours when needed`() {
        assertEquals("0:45", Formatters.duration(45))
        assertEquals("2:05", Formatters.duration(125))
        assertEquals("1:00:00", Formatters.duration(3600))
        assertEquals("—", Formatters.duration(null))
    }

    @Test
    fun `percent is clamped`() {
        assertEquals(50, Formatters.percent(50, 100))
        assertEquals(0, Formatters.percent(10, 0))
        assertEquals(100, Formatters.percent(200, 100))
    }
}

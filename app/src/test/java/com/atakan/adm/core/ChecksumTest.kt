package com.atakan.adm.core

import com.atakan.adm.core.util.Checksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChecksumTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Known digests of the ASCII string "hello world".
    private val md5 = "5eb63bbbe01eeed093cb22bb8f5acdc3"
    private val sha1 = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"
    private val sha256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"

    @Test
    fun `hashes a real file and matches the published digest`() {
        val file = folder.newFile("payload.bin").apply { writeText("hello world") }

        assertEquals(md5, Checksum.hash(file, Checksum.Algorithm.MD5))
        assertEquals(sha1, Checksum.hash(file, Checksum.Algorithm.SHA1))
        assertEquals(sha256, Checksum.hash(file, Checksum.Algorithm.SHA256))
    }

    @Test
    fun `hashing works across multiple one megabyte chunks`() {
        val file = folder.newFile("big.bin")
        val chunk = ByteArray(1 shl 20) { (it % 251).toByte() }
        file.outputStream().use { out -> repeat(3) { out.write(chunk) } }

        val digest = Checksum.hash(file, Checksum.Algorithm.SHA256)
        val reference = java.security.MessageDigest.getInstance("SHA-256").apply {
            repeat(3) { update(chunk) }
        }.digest().joinToString("") { "%02x".format(it) }

        assertEquals(reference, digest)
    }

    @Test
    fun `algorithm is inferred from digest length`() {
        assertEquals(Checksum.Algorithm.MD5, Checksum.detect(md5))
        assertEquals(Checksum.Algorithm.SHA1, Checksum.detect(sha1))
        assertEquals(Checksum.Algorithm.SHA256, Checksum.detect(sha256))
        assertNull(Checksum.detect("abcdef"))
    }

    @Test
    fun `normalize strips prefixes whitespace and case`() {
        assertEquals(sha256, Checksum.normalize("sha256:$sha256"))
        assertEquals(sha256, Checksum.normalize("  ${sha256.uppercase()}  "))
        assertEquals(md5, Checksum.normalize("MD5: $md5"))
        assertEquals(
            sha1,
            Checksum.normalize(sha1.chunked(8).joinToString(" ")),
        )
    }

    @Test
    fun `normalize rejects non hex and wrong length input`() {
        assertNull(Checksum.normalize("not-a-digest"))
        assertNull(Checksum.normalize("abc123"))
        assertNull(Checksum.normalize(null))
        assertNull(Checksum.normalize(""))
        // 64 characters but with a non hex letter.
        assertNull(Checksum.normalize("z" + sha256.drop(1)))
    }

    @Test
    fun `comparison ignores case`() {
        assertTrue(Checksum.matches(sha256.uppercase(), sha256))
    }
}

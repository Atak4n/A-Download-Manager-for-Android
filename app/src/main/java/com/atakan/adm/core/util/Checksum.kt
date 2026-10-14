package com.atakan.adm.core.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** MD5 / SHA-1 / SHA-256 verification of finished files. */
object Checksum {

    private const val BUFFER_SIZE = 1 shl 20 // 1 MB, as specified

    enum class Algorithm(val digestName: String, val hexLength: Int) {
        MD5("MD5", 32),
        SHA1("SHA-1", 40),
        SHA256("SHA-256", 64),
    }

    /**
     * Normalises user input: drops an "sha256:" style prefix, whitespace and case.
     * Returns null when the value is not a hex digest of a supported length.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val stripped = raw.trim()
            .substringAfterLast(':')
            .replace(Regex("""\s+"""), "")
            .lowercase()
        if (stripped.isEmpty()) return null
        if (!stripped.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return stripped.takeIf { value -> Algorithm.entries.any { it.hexLength == value.length } }
    }

    /** Infers the algorithm from the digest length, as the macOS app does. */
    fun detect(normalized: String): Algorithm? =
        Algorithm.entries.firstOrNull { it.hexLength == normalized.length }

    fun hash(file: File, algorithm: Algorithm, onProgress: ((Long) -> Unit)? = null): String =
        file.inputStream().use { hash(it, algorithm, onProgress) }

    fun hash(stream: InputStream, algorithm: Algorithm, onProgress: ((Long) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance(algorithm.digestName)
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            total += read
            onProgress?.invoke(total)
        }
        return digest.digest().toHex()
    }

    fun matches(expected: String, actual: String): Boolean =
        expected.equals(actual, ignoreCase = true)

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}

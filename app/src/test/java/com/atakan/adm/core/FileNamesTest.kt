package com.atakan.adm.core

import com.atakan.adm.core.model.Category
import com.atakan.adm.core.util.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {

    @Test
    fun `collisions increment like the desktop app`() {
        val taken = mutableSetOf("file.zip", "file (2).zip")
        assertEquals("file (3).zip", FileNames.uniqueName("file.zip") { it in taken })
        assertEquals("fresh.zip", FileNames.uniqueName("fresh.zip") { it in taken })
    }

    @Test
    fun `collision handling works without an extension`() {
        val taken = setOf("README")
        assertEquals("README (2)", FileNames.uniqueName("README") { it in taken })
    }

    @Test
    fun `sanitize removes path separators and control characters`() {
        assertEquals("evil.sh", FileNames.sanitize("../../etc/evil.sh"))
        assertEquals("a_b.txt", FileNames.sanitize("a/b.txt".replace('/', ':')))
        assertEquals("download", FileNames.sanitize("   "))
    }

    @Test
    fun `sanitize keeps the extension when truncating long names`() {
        val long = "x".repeat(400) + ".zip"
        val result = FileNames.sanitize(long)
        assertEquals("zip", FileNames.extensionOf(result))
        assert(result.length <= 180)
    }

    @Test
    fun `category comes from the extension`() {
        assertEquals(Category.ARCHIVE, FileNames.categoryFor("backup.tar.gz"))
        assertEquals(Category.APPLICATION, FileNames.categoryFor("app.apk"))
        assertEquals(Category.VIDEO, FileNames.categoryFor("movie.MKV"))
        assertEquals(Category.MUSIC, FileNames.categoryFor("song.flac"))
        assertEquals(Category.IMAGE, FileNames.categoryFor("photo.jpeg"))
        assertEquals(Category.DOCUMENT, FileNames.categoryFor("sheet.xlsx"))
        assertEquals(Category.OTHER, FileNames.categoryFor("blob"))
    }

    @Test
    fun `content disposition is parsed including rfc 5987`() {
        assertEquals(
            "report.pdf",
            FileNames.fromContentDisposition("attachment; filename=\"report.pdf\""),
        )
        assertEquals(
            "rapor çıktı.pdf",
            FileNames.fromContentDisposition(
                "attachment; filename*=UTF-8''rapor%20%C3%A7%C4%B1kt%C4%B1.pdf",
            ),
        )
        assertEquals(
            "plain.bin",
            FileNames.fromContentDisposition("attachment; filename=plain.bin"),
        )
        assertNull(FileNames.fromContentDisposition(null))
        assertNull(FileNames.fromContentDisposition("attachment"))
    }

    @Test
    fun `url filename ignores query and fragment`() {
        assertEquals("setup.exe", FileNames.fromUrl("https://host/a/b/setup.exe?token=1#x"))
        assertEquals("a file.zip", FileNames.fromUrl("https://host/a%20file.zip"))
        assertNull(FileNames.fromUrl("https://host/path/"))
        assertNull(FileNames.fromUrl("https://host"))
    }

    @Test
    fun `resolve prefers the header then the url`() {
        assertEquals(
            "from-header.zip",
            FileNames.resolve(
                url = "https://host/from-url.zip",
                contentDisposition = "attachment; filename=\"from-header.zip\"",
                mimeType = "application/zip",
            ),
        )
        assertEquals(
            "from-url.zip",
            FileNames.resolve("https://host/from-url.zip", null, null),
        )
        assertEquals(
            "download",
            FileNames.resolve("https://host/stream", null, null),
        )
    }
}

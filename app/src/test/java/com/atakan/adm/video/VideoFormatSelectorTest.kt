package com.atakan.adm.video

import com.atakan.adm.video.VideoFormatSelector.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatSelectorTest {

    private val audioM4a = StreamFormat(
        formatId = "140", ext = "m4a", acodec = "mp4a.40.2", abr = 128, fileSize = 3_000_000,
    )
    private val audioOpus = StreamFormat(
        formatId = "251", ext = "webm", acodec = "opus", abr = 160, fileSize = 3_500_000,
    )
    private val video1080 = StreamFormat(
        formatId = "137", ext = "mp4", height = 1080, fps = 30,
        vcodec = "avc1.640028", acodec = "none", tbr = 4000, fileSize = 50_000_000,
    )
    private val video1080Vp9 = StreamFormat(
        formatId = "248", ext = "webm", height = 1080, fps = 30,
        vcodec = "vp9", acodec = "none", tbr = 3500, fileSize = 42_000_000,
    )
    private val video720Muxed = StreamFormat(
        formatId = "22", ext = "mp4", height = 720, fps = 30,
        vcodec = "avc1.64001F", acodec = "mp4a.40.2", tbr = 1500, fileSize = 20_000_000,
    )
    private val video2160 = StreamFormat(
        formatId = "313", ext = "webm", height = 2160, fps = 60,
        vcodec = "vp9", acodec = "none", tbr = 14000, fileSizeApproximate = 180_000_000,
    )

    private val all = listOf(audioM4a, audioOpus, video1080, video1080Vp9, video720Muxed, video2160)

    @Test
    fun `resolutions are listed tallest first with one entry each`() {
        val options = VideoFormatSelector.videoOptions(all)
        assertEquals(listOf(2160, 1080, 720), options.map { it.height })
    }

    @Test
    fun `h264 in mp4 wins over vp9 at the same height`() {
        val option = VideoFormatSelector.videoOptions(all).first { it.height == 1080 }
        assertEquals("137", option.videoFormatId)
    }

    @Test
    fun `m4a audio is preferred so mp4 merging needs no re-encode`() {
        assertEquals("140", VideoFormatSelector.bestAudio(all)?.formatId)
    }

    @Test
    fun `estimate adds the audio stream for video only tracks`() {
        val option = VideoFormatSelector.videoOptions(all).first { it.height == 1080 }
        assertEquals(50_000_000 + 3_000_000, option.estimatedBytes)
        assertEquals("137+140", option.formatSelector)
        assertFalse(option.isProgressive)
    }

    @Test
    fun `muxed stream keeps its own size and needs no merge`() {
        val option = VideoFormatSelector.videoOptions(all).first { it.height == 720 }
        assertEquals(20_000_000, option.estimatedBytes)
        assertEquals("22", option.formatSelector)
        assertTrue(option.isProgressive)
    }

    @Test
    fun `approximate size is used when exact size is missing`() {
        val option = VideoFormatSelector.videoOptions(all).first { it.height == 2160 }
        assertEquals(180_000_000 + 3_000_000, option.estimatedBytes)
        assertEquals("2160p60", option.label)
    }

    @Test
    fun `unknown sizes yield minus one instead of a wrong total`() {
        val noSize = video1080.copy(fileSize = 0, fileSizeApproximate = 0)
        val option = VideoFormatSelector.videoOptions(listOf(noSize, audioM4a)).first()
        assertEquals(-1L, option.estimatedBytes)
    }

    @Test
    fun `audio only options cover m4a and mp3`() {
        val options = VideoFormatSelector.audioOptions(all)
        assertEquals(listOf("m4a", "mp3"), options.map { it.ext })
        assertTrue(options.all { it.isAudioOnly })
        assertEquals("140", options.first().formatSelector)
    }

    @Test
    fun `audio only list is empty when the page has no audio track`() {
        assertTrue(VideoFormatSelector.audioOptions(listOf(video720Muxed)).isEmpty())
    }

    @Test
    fun `full option list keeps video first then audio`() {
        val options = VideoFormatSelector.options(all)
        assertEquals(5, options.size)
        assertFalse(options.first().isAudioOnly)
        assertTrue(options.last().isAudioOnly)
    }
}

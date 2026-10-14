package com.atakan.adm.video

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

/**
 * Turns the raw "yt-dlp -J" format list into the choices shown in the quality
 * picker. The logic works on [StreamFormat] so it stays unit testable without
 * the Python runtime or the Jackson mappers.
 */
object VideoFormatSelector {

    private const val NONE = "none"

    /** Subset of a yt-dlp format entry that matters for picking a quality. */
    data class StreamFormat(
        val formatId: String?,
        val ext: String?,
        val height: Int = 0,
        val fps: Int = 0,
        val vcodec: String? = null,
        val acodec: String? = null,
        val tbr: Int = 0,
        val abr: Int = 0,
        val fileSize: Long = 0,
        val fileSizeApproximate: Long = 0,
    ) {
        val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != NONE && height > 0
        val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != NONE
        val size: Long get() = when {
            fileSize > 0 -> fileSize
            fileSizeApproximate > 0 -> fileSizeApproximate
            else -> -1L
        }
    }

    data class Option(
        val label: String,
        val height: Int,
        val videoFormatId: String?,
        val audioFormatId: String?,
        /** Video plus audio stream, -1 when the server reports no sizes. */
        val estimatedBytes: Long,
        val ext: String,
        val fps: Int,
        val isAudioOnly: Boolean,
        /** True when the stream already carries audio and needs no merge. */
        val isProgressive: Boolean,
    ) {
        /** Value passed to "yt-dlp -f". */
        val formatSelector: String
            get() = when {
                isAudioOnly -> audioFormatId ?: "bestaudio/best"
                isProgressive || audioFormatId == null -> videoFormatId ?: "best"
                else -> "$videoFormatId+$audioFormatId"
            }
    }

    fun from(format: VideoFormat): StreamFormat = StreamFormat(
        formatId = format.formatId,
        ext = format.ext,
        height = format.height,
        fps = format.fps,
        vcodec = format.vcodec,
        acodec = format.acodec,
        tbr = format.tbr,
        abr = format.abr,
        fileSize = format.fileSize,
        fileSizeApproximate = format.fileSizeApproximate,
    )

    /** Audio stream used to complete a video-only track. */
    fun bestAudio(formats: List<StreamFormat>): StreamFormat? =
        formats.filter { !it.hasVideo && it.hasAudio }
            // AAC in m4a merges into MP4 without re-encoding, so prefer it.
            .maxWithOrNull(
                compareBy<StreamFormat> { if (it.ext == "m4a") 1 else 0 }
                    .thenBy { if (it.acodec?.startsWith("mp4a") == true) 1 else 0 }
                    .thenBy { maxOf(it.abr, it.tbr) },
            )

    fun audioOptions(formats: List<StreamFormat>): List<Option> {
        val audio = bestAudio(formats) ?: return emptyList()
        return listOf("m4a", "mp3").map { container ->
            Option(
                label = container,
                height = 0,
                videoFormatId = null,
                audioFormatId = audio.formatId,
                estimatedBytes = audio.size,
                ext = container,
                fps = 0,
                isAudioOnly = true,
                isProgressive = false,
            )
        }
    }

    /** One entry per available resolution, best stream of each, tallest first. */
    fun videoOptions(formats: List<StreamFormat>): List<Option> {
        val audio = bestAudio(formats)
        val audioSize = audio?.size ?: -1L

        return formats.asSequence()
            .filter { it.hasVideo }
            .groupBy { it.height }
            .mapNotNull { (height, group) ->
                val best = group.maxWithOrNull(
                    // Video-only streams merge with the best audio track, which
                    // beats a muxed stream of the same height.
                    compareBy<StreamFormat> { if (it.hasAudio) 0 else 1 }
                        .thenBy { if (it.vcodec?.startsWith("avc1") == true) 1 else 0 }
                        .thenBy { if (it.ext == "mp4") 1 else 0 }
                        .thenBy { it.tbr }
                        .thenBy { it.fps },
                ) ?: return@mapNotNull null

                val progressive = best.hasAudio
                val videoSize = best.size
                val estimated = when {
                    videoSize < 0 -> -1L
                    progressive -> videoSize
                    audioSize < 0 -> videoSize
                    else -> videoSize + audioSize
                }
                Option(
                    label = "${height}p" + if (best.fps >= 50) "${best.fps}" else "",
                    height = height,
                    videoFormatId = best.formatId,
                    audioFormatId = if (progressive) null else audio?.formatId,
                    estimatedBytes = estimated,
                    ext = best.ext ?: "mp4",
                    fps = best.fps,
                    isAudioOnly = false,
                    isProgressive = progressive,
                )
            }
            .sortedByDescending { it.height }
            .toList()
    }

    fun options(formats: List<StreamFormat>): List<Option> =
        videoOptions(formats) + audioOptions(formats)

    fun options(info: VideoInfo): List<Option> {
        val formats = info.formats?.map { from(it) }.orEmpty()
        if (formats.isEmpty()) {
            // Some extractors only return a single muxed URL.
            return listOf(
                Option(
                    label = info.resolution ?: "best",
                    height = info.height,
                    videoFormatId = info.formatId,
                    audioFormatId = null,
                    estimatedBytes = when {
                        info.fileSize > 0 -> info.fileSize
                        info.fileSizeApproximate > 0 -> info.fileSizeApproximate
                        else -> -1L
                    },
                    ext = info.ext ?: "mp4",
                    fps = 0,
                    isAudioOnly = false,
                    isProgressive = true,
                ),
            )
        }
        return options(formats)
    }
}

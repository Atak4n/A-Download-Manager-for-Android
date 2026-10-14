package com.atakan.adm.core.util

import android.os.StatFs
import java.io.File

object StorageSpace {

    /** Usable bytes on the volume that holds [directory]. */
    fun usableBytes(directory: File): Long = runCatching {
        val stat = StatFs(directory.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(-1L)

    /** Keeps a margin so the device never ends up completely full. */
    const val SAFETY_MARGIN_BYTES = 32L * 1024L * 1024L

    fun hasRoomFor(directory: File, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        val usable = usableBytes(directory)
        if (usable < 0) return true // unknown, let the write fail loudly instead
        return usable - SAFETY_MARGIN_BYTES >= requiredBytes
    }
}

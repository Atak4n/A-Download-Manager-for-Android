package com.atakan.adm.core.util

import java.util.Locale
import kotlin.math.abs

/** Byte / speed / duration formatting shared by the UI and notifications. */
object Formatters {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long): String {
        if (value < 0) return "—"
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < UNITS.lastIndex) {
            size /= 1024
            unit++
        }
        val digits = if (unit == 0 || size >= 100) 0 else if (size >= 10) 1 else 2
        return String.format(Locale.US, "%.${digits}f %s", size, UNITS[unit])
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    /** Remaining time, or null when it cannot be estimated. */
    fun etaSeconds(remainingBytes: Long, bytesPerSecond: Long): Long? =
        if (remainingBytes <= 0 || bytesPerSecond <= 0) null
        else remainingBytes / bytesPerSecond

    fun duration(seconds: Long?): String {
        if (seconds == null || seconds < 0) return "—"
        val s = abs(seconds)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
            else -> String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    fun percent(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done.toDouble() / total) * 100).toInt().coerceIn(0, 100)
}

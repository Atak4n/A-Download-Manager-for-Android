package com.atakan.adm.download

import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One virtual wire shared by every transfer. A caller reserves bytes under the
 * lock, which hands back the instant those bytes may move; the waiting itself
 * happens outside the lock so segments never serialise on each other.
 */
@Singleton
class GlobalRateLimiter @Inject constructor(
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** 0 means unlimited. */
    @Volatile
    private var bytesPerSecond: Long = 0L

    /** Virtual clock: the wire is busy until this instant. */
    private var wireFreeAtNanos: Long = System.nanoTime()

    init {
        scope.launch {
            settings.settings
                .map { it.speedLimitKbps }
                .distinctUntilChanged()
                .collect { kbps -> setLimitKbps(kbps) }
        }
    }

    suspend fun setLimitKbps(kbps: Int) {
        val target = if (kbps <= 0) 0L else kbps.toLong() * 1024L
        mutex.withLock {
            bytesPerSecond = target
            // Dropping the limit must not keep old reservations in effect.
            wireFreeAtNanos = System.nanoTime()
        }
    }

    fun limitKbps(): Int = (bytesPerSecond / 1024L).toInt()

    val isLimited: Boolean get() = bytesPerSecond > 0

    /**
     * Reserves [bytes] on the wire and suspends until they are allowed through.
     * Returns immediately when no limit is configured.
     */
    suspend fun acquire(bytes: Int) {
        if (bytes <= 0) return
        val rate = bytesPerSecond
        if (rate <= 0L) return

        val startAtNanos = mutex.withLock {
            val rateNow = bytesPerSecond
            if (rateNow <= 0L) return
            val now = System.nanoTime()
            val start = maxOf(now, wireFreeAtNanos)
            val costNanos = (bytes.toDouble() / rateNow * 1_000_000_000.0).toLong()
            wireFreeAtNanos = start + costNanos
            start
        }

        val waitNanos = startAtNanos - System.nanoTime()
        if (waitNanos > 0) delay(waitNanos / 1_000_000L + 1)
    }

    /** Chunk size that keeps throttling smooth without starving fast links. */
    fun chunkSize(): Int = if (isLimited) {
        (bytesPerSecond / 16).coerceIn(4 * 1024L, 64 * 1024L).toInt()
    } else {
        DEFAULT_CHUNK
    }

    companion object {
        const val DEFAULT_CHUNK = 64 * 1024
    }
}

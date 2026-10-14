package com.atakan.adm.download

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/**
 * Limits how many segments of one download may be in flight, and lets that
 * limit shrink when the server pushes back (429/503/500) and grow again once
 * things settle. Shrinking parks permits instead of recreating the semaphore,
 * so running segments are never interrupted.
 */
class ConnectionGate(private val maxConnections: Int, initialConnections: Int) {

    private val semaphore = Semaphore(maxConnections, acquiredPermits = maxConnections - initialConnections.coerceIn(1, maxConnections))
    private val mutex = Mutex()

    private var parked: Int = maxConnections - initialConnections.coerceIn(1, maxConnections)
    private var consecutiveSuccesses: Int = 0

    val current: Int get() = maxConnections - parked

    suspend fun <T> withConnection(block: suspend () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }

    /** Halves the allowance after server side throttling, never below one. */
    suspend fun onThrottled(): Int = mutex.withLock {
        consecutiveSuccesses = 0
        val active = maxConnections - parked
        val target = (active / 2).coerceAtLeast(1)
        val toPark = active - target
        repeat(toPark) {
            semaphore.acquire()
            parked++
        }
        maxConnections - parked
    }

    /** Opens one connection back up after a streak of clean transfers. */
    suspend fun onSuccess(): Int = mutex.withLock {
        consecutiveSuccesses++
        if (consecutiveSuccesses >= SUCCESS_STREAK && parked > 0) {
            consecutiveSuccesses = 0
            parked--
            semaphore.release()
        }
        maxConnections - parked
    }

    companion object {
        /** Clean chunk transfers needed before widening again. */
        const val SUCCESS_STREAK = 8
    }
}

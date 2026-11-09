package com.nearer.app

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fair, bounded admission gate for Fast Share downloads.
 * Two receivers can transfer at once; additional requests wait in arrival order
 * rather than racing or immediately receiving 429 while both slots are occupied.
 */
internal class FastShareDownloadGate {
    companion object {
        // First group-sharing milestone: five simultaneous file streams.
        // Keep this aligned with FastShareController's five core workers.
        const val MAX_CONCURRENT_DOWNLOADS = 5
    }

    private val permits = Semaphore(MAX_CONCURRENT_DOWNLOADS, true)

    @Throws(InterruptedException::class)
    fun tryAcquire(timeout: Long, unit: TimeUnit): Boolean {
        require(timeout >= 0L) { "Timeout must be non-negative" }
        return permits.tryAcquire(timeout, unit)
    }

    fun release() {
        permits.release()
    }
}

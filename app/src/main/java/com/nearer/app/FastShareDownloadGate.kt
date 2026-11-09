package com.nearer.app

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fair, bounded admission gate for Fast Share downloads.
 * Five receivers can transfer at once; additional requests wait in arrival order
 * rather than racing or immediately receiving 429 while all slots are occupied.
 */
internal class FastShareDownloadGate {
    companion object {
        // First group-sharing milestone: five simultaneous file streams.
        // Keep this aligned with FastShareController's five core workers.
        const val MAX_CONCURRENT_DOWNLOADS = 5
        // Keep a small bounded group of additional receivers waiting for a slot.
        const val MAX_WAITING_DOWNLOADS = 7
        const val MAX_HANDLED_DOWNLOAD_REQUESTS = MAX_CONCURRENT_DOWNLOADS + MAX_WAITING_DOWNLOADS
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

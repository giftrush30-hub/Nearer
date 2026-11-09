package com.nearer.app

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fair, bounded admission gate for Fast Share's single active file stream.
 * Requests wait in arrival order rather than racing or immediately receiving
 * 429 while another receiver is downloading.
 */
internal class FastShareDownloadGate {
    private val permits = Semaphore(1, true)

    @Throws(InterruptedException::class)
    fun tryAcquire(timeout: Long, unit: TimeUnit): Boolean {
        require(timeout >= 0L) { "Timeout must be non-negative" }
        return permits.tryAcquire(timeout, unit)
    }

    fun release() {
        permits.release()
    }
}

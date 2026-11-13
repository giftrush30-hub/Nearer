package com.nearer.app

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Runs expensive receive finalization (provider fallback copy and SHA-256
 * verification) away from Nearby callbacks and independently for two completed
 * transfers. A single worker made every large completed file wait behind the
 * previous file's full-disk hash/copy, even when transfers arrived concurrently.
 *
 * Two workers deliberately cap simultaneous full-file disk/CPU work on budget
 * Android devices; this improves multi-transfer responsiveness without spawning
 * an unbounded number of hashing threads.
 */
internal object TransferIoPolicy {
    const val RECEIVE_WORKERS = 2

    fun createReceiveExecutor(): ExecutorService =
        Executors.newFixedThreadPool(RECEIVE_WORKERS, ThreadFactory { task ->
            Thread(task, "NeaRer-receive-io").apply { isDaemon = true }
        })
}

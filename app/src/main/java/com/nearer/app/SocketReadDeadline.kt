package com.nearer.app

import java.net.Socket
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Bounds the total time allowed to send one HTTP request, not just the idle
 * time between individual bytes. Socket SO_TIMEOUT alone can be defeated by a
 * peer that trickles one byte before each read timeout expires.
 */
internal class SocketReadDeadline(
    private val socket: Socket,
    scheduler: ScheduledExecutorService,
    timeoutMillis: Long
) : AutoCloseable {
    private val expiry: ScheduledFuture<*>?

    init {
        require(timeoutMillis > 0L) { "Request deadline must be positive" }
        expiry = runCatching {
            scheduler.schedule(
                { runCatching { socket.close() } },
                timeoutMillis,
                TimeUnit.MILLISECONDS
            )
        }.getOrNull()
    }

    override fun close() {
        expiry?.cancel(false)
    }
}

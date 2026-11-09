package com.nearer.app

import java.net.Socket
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounds the total time allowed to send one HTTP request, not just the idle
 * time between individual bytes. Socket SO_TIMEOUT alone can be defeated by a
 * peer that trickles one byte before each read timeout expires.
 *
 * If the watchdog cannot schedule the deadline, fail closed by closing the
 * socket. A Fast Share worker must never silently proceed without its bound.
 */
internal class SocketReadDeadline(
    private val socket: Socket,
    scheduler: ScheduledExecutorService,
    timeoutMillis: Long
) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val expiry: ScheduledFuture<*>?

    init {
        require(timeoutMillis > 0L) { "Request deadline must be positive" }
        expiry = try {
            scheduler.schedule(
                {
                    if (active.compareAndSet(true, false)) {
                        runCatching { socket.close() }
                    }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS
            )
        } catch (_: RuntimeException) {
            // RejectedExecutionException or a shutting-down watchdog must not
            // turn a bounded request into an unbounded one.
            active.set(false)
            runCatching { socket.close() }
            null
        }
    }

    override fun close() {
        active.set(false)
        expiry?.cancel(false)
    }
}

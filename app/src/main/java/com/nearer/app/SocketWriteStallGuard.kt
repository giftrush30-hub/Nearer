package com.nearer.app

import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Closes a receiver socket when one of its active writes remains blocked too long.
 *
 * Socket.setSoTimeout only limits reads. It does not limit writes, so a receiver
 * that stops reading can otherwise hold one of the five transfer slots forever.
 * The guard is armed only while a file response is being streamed; time spent
 * waiting in the bounded queue is deliberately excluded.
 */
internal class SocketWriteStallGuard(
    private val socket: Socket,
    scheduler: ScheduledExecutorService,
    private val timeoutMillis: Long
) : AutoCloseable {
    private var armed = false
    private var timedOut = false
    private var lastSuccessfulWriteNanos = 0L
    private val watchdog: ScheduledFuture<*>?

    init {
        require(timeoutMillis > 0L) { "Write timeout must be positive" }
        val checkEveryMillis = minOf(1_000L, maxOf(1L, timeoutMillis / 4L))
        // Shutdown can race with a worker that has just been accepted. In
        // that case the session is already closing its sockets, so a missing
        // watchdog is safe and must not turn cleanup into a rejected task error.
        watchdog = runCatching {
            scheduler.scheduleAtFixedRate(
                { checkForStall() },
                checkEveryMillis,
                checkEveryMillis,
                TimeUnit.MILLISECONDS
            )
        }.getOrNull()
    }

    fun wrap(output: OutputStream): OutputStream = object : OutputStream() {
        override fun write(value: Int) {
            output.write(value)
            markWriteCompleted()
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            output.write(buffer, offset, length)
            markWriteCompleted()
        }

        override fun flush() {
            output.flush()
            markWriteCompleted()
        }

        override fun close() {
            output.close()
        }
    }

    @Synchronized
    fun arm() {
        if (!timedOut) {
            lastSuccessfulWriteNanos = System.nanoTime()
            armed = true
        }
    }

    @Synchronized
    fun disarm() {
        armed = false
    }

    @Synchronized
    private fun markWriteCompleted() {
        if (armed && !timedOut) lastSuccessfulWriteNanos = System.nanoTime()
    }

    private fun checkForStall() {
        // Claim the timeout under the same monitor used by disarm(). This
        // prevents a watchdog that observed an old armed state from closing
        // a socket after the transfer has already released its slot.
        val shouldClose = synchronized(this) {
            if (!armed || timedOut) {
                false
            } else {
                val idleNanos = System.nanoTime() - lastSuccessfulWriteNanos
                if (idleNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
                    timedOut = true
                    armed = false
                    true
                } else {
                    false
                }
            }
        }
        if (shouldClose) runCatching { socket.close() }
    }

    override fun close() {
        disarm()
        watchdog?.cancel(false)
    }
}

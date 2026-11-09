package com.nearer.app

import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private val armed = AtomicBoolean(false)
    private val timedOut = AtomicBoolean(false)
    private val lastSuccessfulWriteNanos = AtomicLong(0L)
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

    fun arm() {
        if (!timedOut.get()) {
            lastSuccessfulWriteNanos.set(System.nanoTime())
            armed.set(true)
        }
    }

    fun disarm() {
        armed.set(false)
    }

    private fun markWriteCompleted() {
        if (armed.get()) lastSuccessfulWriteNanos.set(System.nanoTime())
    }

    private fun checkForStall() {
        if (!armed.get() || timedOut.get()) return
        val idleNanos = System.nanoTime() - lastSuccessfulWriteNanos.get()
        if (idleNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis) &&
            timedOut.compareAndSet(false, true)
        ) {
            armed.set(false)
            runCatching { socket.close() }
        }
    }

    override fun close() {
        disarm()
        watchdog?.cancel(false)
    }
}

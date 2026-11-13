package com.nearer.app

import java.io.IOException
import java.io.InputStream

/**
 * An InputStream whose source is supplied only after the Nearby stream payload
 * has been created and its payload ID has been exchanged in the resume handshake.
 * Reads block until the authenticated sender decision supplies the exact source.
 */
internal class DeferredTransferInputStream : InputStream() {
    private val lock = Object()
    private var source: InputStream? = null
    private var failure: IOException? = null
    private var closed = false

    fun provide(input: InputStream) {
        synchronized(lock) {
            if (closed || failure != null || source != null) {
                runCatching { input.close() }
                throw IllegalStateException("The deferred transfer source is no longer available.")
            }
            source = input
            lock.notifyAll()
        }
    }

    fun fail(error: Throwable) {
        val active: InputStream?
        synchronized(lock) {
            if (closed || failure != null) return
            failure = IOException("The transfer source was not approved.", error)
            active = source
            lock.notifyAll()
        }
        runCatching { active?.close() }
    }

    override fun read(): Int = awaitSource().read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > buffer.size - offset) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) return 0
        return awaitSource().read(buffer, offset, length)
    }

    override fun skip(count: Long): Long {
        if (count <= 0L) return 0L
        return awaitSource().skip(count)
    }

    override fun available(): Int = synchronized(lock) {
        if (closed) throw IOException("The transfer stream is closed.")
        failure?.let { throw it }
        source?.available() ?: 0
    }

    override fun close() {
        val active: InputStream?
        synchronized(lock) {
            if (closed) return
            closed = true
            active = source
            source = null
            lock.notifyAll()
        }
        runCatching { active?.close() }
    }

    private fun awaitSource(): InputStream = synchronized(lock) {
        while (source == null && !closed && failure == null) {
            try {
                lock.wait()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for the approved transfer source.", interrupted)
            }
        }
        if (closed) throw IOException("The transfer stream is closed.")
        failure?.let { throw it }
        source ?: throw IOException("The transfer source was not supplied.")
    }
}

package com.nearer.app

import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

/** Copies one HTTP upload body to disk without buffering the whole file. */
internal object FastShareUploadReceiver {
    const val BUFFER_SIZE = 256 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        shouldContinue: () -> Boolean = { true },
        onBytesCopied: (Long) -> Unit = {}
    ): Long {
        require(expectedBytes >= 0L) { "Upload length must be non-negative." }
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (copied < expectedBytes) {
            if (!shouldContinue()) throw InterruptedIOException("Upload cancelled.")
            val requested = minOf(buffer.size.toLong(), expectedBytes - copied).toInt()
            val count = input.read(buffer, 0, requested)
            if (count < 0) throw EOFException("Upload ended before its declared Content-Length.")
            val bytesRead = if (count == 0) {
                val single = input.read()
                if (single < 0) throw EOFException("Upload ended before its declared Content-Length.")
                buffer[0] = single.toByte()
                1
            } else count
            output.write(buffer, 0, bytesRead)
            copied += bytesRead
            onBytesCopied(copied)
        }
        output.flush()
        return copied
    }
}

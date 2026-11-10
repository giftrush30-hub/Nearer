package com.nearer.app

import java.io.InputStream
import java.io.OutputStream

internal data class ChunkedCopyResult(
    val bytesCopied: Long,
    val complete: Boolean
)

/**
 * Writes an unknown-length input as HTTP/1.1 chunked transfer coding.
 * A terminating zero-sized chunk is emitted only after a clean EOF while
 * the transfer is still allowed to continue.
 */
internal object ChunkedStreamCopy {
    // Match known-length transfers so both paths use the same bounded memory profile.
    const val DEFAULT_BUFFER_SIZE = 512 * 1024
    const val PROGRESS_INTERVAL_BYTES = 4L * 1024L * 1024L

    fun copy(
        input: InputStream,
        output: OutputStream,
        shouldContinue: () -> Boolean,
        onBytesCopied: (Long) -> Unit = {},
        onProgress: (Long) -> Unit = {},
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): ChunkedCopyResult {
        require(bufferSize > 0) { "Buffer size must be positive" }
        val buffer = ByteArray(bufferSize)
        var sent = 0L
        var nextProgressAt = PROGRESS_INTERVAL_BYTES

        while (shouldContinue()) {
            val read = input.read(buffer)
            if (read < 0) break
            // Be defensive with ContentProvider streams that return zero rather
            // than blocking or producing EOF for a non-empty read request.
            val count = if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[0] = single.toByte()
                1
            } else read

            output.write(Integer.toHexString(count).toByteArray(Charsets.US_ASCII))
            output.write("\r\n".toByteArray(Charsets.US_ASCII))
            output.write(buffer, 0, count)
            output.write("\r\n".toByteArray(Charsets.US_ASCII))
            sent += count
            onBytesCopied(sent)

            if (sent >= nextProgressAt) {
                output.flush()
                onProgress(sent)
                while (nextProgressAt <= sent && nextProgressAt <= Long.MAX_VALUE - PROGRESS_INTERVAL_BYTES) {
                    nextProgressAt += PROGRESS_INTERVAL_BYTES
                }
            }
        }

        val complete = shouldContinue()
        if (complete) {
            output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
            if (sent > 0L) onProgress(sent)
        }
        return ChunkedCopyResult(sent, complete)
    }
}

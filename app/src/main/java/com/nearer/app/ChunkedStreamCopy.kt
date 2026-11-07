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
    // Keep unknown-length streams efficient without ever buffering the whole file.
    const val DEFAULT_BUFFER_SIZE = 256 * 1024

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
        var lastReported = 0L

        while (shouldContinue()) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue

            output.write(Integer.toHexString(read).toByteArray(Charsets.US_ASCII))
            output.write("\r\n".toByteArray(Charsets.US_ASCII))
            output.write(buffer, 0, read)
            output.write("\r\n".toByteArray(Charsets.US_ASCII))
            sent += read
            onBytesCopied(sent)

            if (sent - lastReported >= 1024L * 1024L) {
                output.flush()
                onProgress(sent)
                lastReported = sent
            }
        }

        val complete = shouldContinue()
        if (complete) {
            output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
            if (sent != lastReported) onProgress(sent)
        }
        return ChunkedCopyResult(sent, complete)
    }
}

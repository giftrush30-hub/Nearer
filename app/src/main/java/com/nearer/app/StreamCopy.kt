package com.nearer.app

import java.io.InputStream
import java.io.OutputStream

internal data class StreamCopyResult(
    val bytesCopied: Long,
    val complete: Boolean
)

/**
 * Copies at most the declared number of bytes without buffering the whole file.
 * A short source or a stopped transfer is reported as incomplete, never success.
 */
internal object StreamCopy {
    // 512 KiB bounded reads reduce provider and socket write overhead on fast local Wi-Fi.
    // At five simultaneous transfers this consumes at most 2.5 MiB for source buffers.
    const val DEFAULT_BUFFER_SIZE = 512 * 1024
    const val PROGRESS_INTERVAL_BYTES = 4L * 1024L * 1024L

    fun copy(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        shouldContinue: () -> Boolean = { true },
        onBytesCopied: (Long) -> Unit = {},
        onProgress: (Long) -> Unit = {},
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): StreamCopyResult {
        require(expectedBytes >= 0L) { "Expected byte count cannot be negative" }
        require(bufferSize > 0) { "Buffer size must be positive" }
        val buffer = ByteArray(bufferSize)
        var copied = 0L
        var nextProgressAt = PROGRESS_INTERVAL_BYTES
        while (copied < expectedBytes && shouldContinue()) {
            val requested = minOf(buffer.size.toLong(), expectedBytes - copied).toInt()
            val read = input.read(buffer, 0, requested)
            if (read < 0) break
            // Some ContentProvider streams incorrectly return zero for a non-empty
            // read request. Fall back to a one-byte read so the transfer cannot spin.
            val count = if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[0] = single.toByte()
                1
            } else read
            output.write(buffer, 0, count)
            copied += count
            onBytesCopied(copied)
            if (copied >= nextProgressAt || copied == expectedBytes) {
                output.flush()
                onProgress(copied)
                while (nextProgressAt <= copied && nextProgressAt <= Long.MAX_VALUE - PROGRESS_INTERVAL_BYTES) {
                    nextProgressAt += PROGRESS_INTERVAL_BYTES
                }
            }
        }
        output.flush()
        return StreamCopyResult(copied, copied == expectedBytes && shouldContinue())
    }
}

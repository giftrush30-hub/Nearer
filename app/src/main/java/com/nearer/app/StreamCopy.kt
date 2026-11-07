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
    fun copy(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        shouldContinue: () -> Boolean = { true },
        onBytesCopied: (Long) -> Unit = {},
        onProgress: (Long) -> Unit = {}
    ): StreamCopyResult {
        require(expectedBytes >= 0L) { "Expected byte count cannot be negative" }
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (copied < expectedBytes && shouldContinue()) {
            val requested = minOf(buffer.size.toLong(), expectedBytes - copied).toInt()
            val read = input.read(buffer, 0, requested)
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read
            onBytesCopied(copied)
            if (copied == expectedBytes || copied % (1024L * 1024L) < read) {
                output.flush()
                onProgress(copied)
            }
        }
        output.flush()
        return StreamCopyResult(copied, copied == expectedBytes && shouldContinue())
    }
}

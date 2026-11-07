package com.nearer.app

import java.io.InputStream

/**
 * Positions a fresh file stream at the requested HTTP byte-range offset.
 * Some ContentResolver streams do not implement skip efficiently (or at all),
 * so a zero-byte skip falls back to consuming one byte at a time.
 */
internal object StreamRangeCopy {
    fun skipFully(stream: InputStream, count: Long) {
        require(count >= 0L) { "Skip count cannot be negative" }
        var remaining = count
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (stream.read() < 0) {
                throw IllegalStateException("Cannot seek to requested file section")
            } else {
                remaining--
            }
        }
    }
}

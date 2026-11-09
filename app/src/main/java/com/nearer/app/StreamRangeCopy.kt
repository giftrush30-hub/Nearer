package com.nearer.app

import java.io.InputStream

/**
 * Positions a fresh file stream at the requested HTTP byte-range offset.
 * Some ContentResolver streams do not implement skip efficiently (or at all),
 * so a zero-byte skip falls back to bounded buffered reads.
 */
internal object StreamRangeCopy {
    private const val DISCARD_BUFFER_SIZE = 32 * 1024

    fun skipFully(stream: InputStream, count: Long) {
        require(count >= 0L) { "Skip count cannot be negative" }
        var remaining = count
        val discard = ByteArray(DISCARD_BUFFER_SIZE)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped.coerceAtMost(remaining)
            } else {
                // A number of provider-backed streams return zero from skip().
                // Consume larger blocks instead of issuing one read() per byte;
                // this keeps resuming far into large files practical.
                val requested = minOf(discard.size.toLong(), remaining).toInt()
                val read = stream.read(discard, 0, requested)
                when {
                    read < 0 -> throw IllegalStateException("Cannot seek to requested file section")
                    read > 0 -> remaining -= read.toLong()
                    else -> {
                        // Defensive progress for unusual streams that return zero
                        // from a non-empty bulk read.
                        if (stream.read() < 0) {
                            throw IllegalStateException("Cannot seek to requested file section")
                        }
                        remaining--
                    }
                }
            }
        }
    }
}

package com.nearer.app

import java.util.Locale

/**
 * Separates time-to-first-byte from sustained payload throughput.
 * The start timestamp should be captured before checksum preparation so this
 * metric includes any sender-side work that delays the first transmitted byte.
 */
internal object TransferLatencyMetrics {
    fun milliseconds(elapsedNanos: Long): Double {
        require(elapsedNanos >= 0L) { "Elapsed time cannot be negative" }
        return elapsedNanos.toDouble() / 1_000_000.0
    }

    fun firstByteLabel(elapsedNanos: Long): String =
        String.format(Locale.US, "%.0f ms to first byte", milliseconds(elapsedNanos))
}

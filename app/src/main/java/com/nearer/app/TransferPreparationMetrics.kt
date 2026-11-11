package com.nearer.app

import java.util.Locale

/**
 * Describes the sender-side integrity pre-read separately from radio throughput.
 * This makes the extra source I/O visible in end-to-end performance investigations.
 */
internal object TransferPreparationMetrics {
    fun mebibytesPerSecond(bytes: Long, elapsedNanos: Long): Double {
        require(bytes >= 0L) { "Prepared byte count cannot be negative" }
        require(elapsedNanos >= 0L) { "Elapsed time cannot be negative" }
        if (bytes == 0L) return 0.0
        return bytes.toDouble() * 1_000_000_000.0 /
            elapsedNanos.coerceAtLeast(1L).toDouble() / (1024.0 * 1024.0)
    }

    fun describe(bytes: Long, elapsedNanos: Long): String {
        require(bytes >= 0L) { "Prepared byte count cannot be negative" }
        require(elapsedNanos >= 0L) { "Elapsed time cannot be negative" }
        val seconds = elapsedNanos.toDouble() / 1_000_000_000.0
        val rate = mebibytesPerSecond(bytes, elapsedNanos)
        return String.format(
            Locale.US,
            "Bundle integrity pre-check: %.2f s, %.1f MiB/s source-read rate (before sending).",
            seconds,
            rate
        )
    }
}

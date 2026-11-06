package com.nearer.app

import java.util.Locale

/**
 * Converts completed payload measurements into a stable, locale-independent
 * MiB/s label. Timing starts at the first bulk-payload progress callback.
 */
internal object TransferRateMetrics {
    fun averageMebibytesPerSecond(bytes: Long, elapsedNanos: Long): Double {
        require(bytes >= 0L) { "Transferred byte count cannot be negative" }
        require(elapsedNanos >= 0L) { "Elapsed time cannot be negative" }
        if (bytes == 0L) return 0.0
        // A zero-duration callback sequence is possible on tiny/test payloads;
        // clamp only the denominator, never fabricate transferred bytes.
        return bytes.toDouble() * 1_000_000_000.0 /
            elapsedNanos.coerceAtLeast(1L).toDouble() / (1024.0 * 1024.0)
    }

    fun label(bytes: Long, elapsedNanos: Long): String =
        String.format(Locale.US, "%.1f", averageMebibytesPerSecond(bytes, elapsedNanos))
}

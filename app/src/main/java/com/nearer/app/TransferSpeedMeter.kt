package com.nearer.app

import android.os.SystemClock
import java.util.ArrayDeque

/**
 * Measures application-level transfer throughput from monotonic payload progress.
 *
 * Rates use decimal MB/s (1 MB = 1,000,000 bytes). This deliberately measures
 * bytes reported as transferred by the transport, not the Wi-Fi PHY/link rate.
 */
internal class TransferSpeedMeter(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clockMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    init {
        require(windowMillis > 0L) { "windowMillis must be positive" }
    }

    private data class Sample(val timeMillis: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var startedAtMillis: Long? = null
    private var transferredBytes = 0L
    private var baselineBytes = 0L
    private var totalBytes = -1L
    private var peakWindowBytesPerSecond = 0.0

    @Synchronized
    fun record(bytesTransferred: Long, expectedTotalBytes: Long = -1L): TransferSpeedSnapshot {
        val now = clockMillis()
        // SDK progress should be monotonic, but ignore regressions rather than
        // producing negative speeds if a callback is duplicated or reordered.
        transferredBytes = maxOf(transferredBytes, bytesTransferred.coerceAtLeast(0L))
        if (startedAtMillis == null) {
            startedAtMillis = now
            baselineBytes = transferredBytes
        }
        if (expectedTotalBytes >= 0L) totalBytes = expectedTotalBytes

        samples.addLast(Sample(now, transferredBytes))
        prune(now)
        updatePeakWindowRate(now)
        return snapshotAt(now)
    }

    @Synchronized
    fun snapshot(): TransferSpeedSnapshot = snapshotAt(clockMillis())

    @Synchronized
    fun reset() {
        samples.clear()
        startedAtMillis = null
        transferredBytes = 0L
        baselineBytes = 0L
        totalBytes = -1L
        peakWindowBytesPerSecond = 0.0
    }

    private fun prune(now: Long) {
        val cutoff = now - windowMillis
        while (samples.isNotEmpty() && samples.peekFirst().timeMillis < cutoff) {
            samples.removeFirst()
        }
    }

    private fun updatePeakWindowRate(now: Long) {
        val rate = rateWithinWindow(now)
        peakWindowBytesPerSecond = maxOf(peakWindowBytesPerSecond, rate)
    }

    private fun rateWithinWindow(now: Long): Double {
        val cutoff = now - windowMillis
        val recentSamples = samples.filter { it.timeMillis >= cutoff }
        if (recentSamples.size < 2) return 0.0
        val first = recentSamples.first()
        val last = recentSamples.last()
        val elapsed = last.timeMillis - first.timeMillis
        if (elapsed <= 0L) return 0.0
        return (last.bytes - first.bytes).coerceAtLeast(0L) * 1000.0 / elapsed
    }

    private fun snapshotAt(now: Long): TransferSpeedSnapshot {
        val started = startedAtMillis
        val elapsed = if (started == null) 0L else (now - started).coerceAtLeast(0L)
        val bytesDuringMeasurement = (transferredBytes - baselineBytes).coerceAtLeast(0L)
        val averageRate = if (elapsed > 0L) bytesDuringMeasurement * 1000.0 / elapsed else 0.0

        val recentRate = rateWithinWindow(now)

        return TransferSpeedSnapshot(
            transferredBytes = transferredBytes,
            totalBytes = totalBytes,
            elapsedMillis = elapsed,
            averageBytesPerSecond = averageRate,
            recentBytesPerSecond = recentRate,
            peakWindowBytesPerSecond = peakWindowBytesPerSecond
        )
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 5_000L
    }
}

internal data class TransferSpeedSnapshot(
    val transferredBytes: Long,
    val totalBytes: Long,
    val elapsedMillis: Long,
    val averageBytesPerSecond: Double,
    val recentBytesPerSecond: Double,
    val peakWindowBytesPerSecond: Double
) {
    val averageMegabytesPerSecond: Double get() = averageBytesPerSecond / 1_000_000.0
    val recentMegabytesPerSecond: Double get() = recentBytesPerSecond / 1_000_000.0
    val peakWindowMegabytesPerSecond: Double get() = peakWindowBytesPerSecond / 1_000_000.0
    val progressFraction: Float?
        get() = if (totalBytes > 0L) {
            (transferredBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
        } else null
}

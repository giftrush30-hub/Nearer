package com.nearer.app

/**
 * Coalesces per-record progress into UI-friendly updates.
 *
 * The transfer loop may process hundreds of encrypted records per second.
 * Posting every record through the service/UI adds avoidable synchronization
 * and callback work to the data plane. Report at most once per interval, while
 * always delivering the final byte count before integrity/finalization.
 */
internal class HyperTransferProgressReporter(
    private val minimumIntervalNanos: Long = DEFAULT_INTERVAL_NANOS
) {
    init {
        require(minimumIntervalNanos > 0L)
    }

    private var lastReportedNanos = Long.MIN_VALUE
    private var lastReportedBytes = -1L

    fun report(
        transferredBytes: Long,
        totalBytes: Long,
        listener: WifiDirectTcpTransferEngine.ProgressListener,
        force: Boolean = false,
        nowNanos: Long = System.nanoTime()
    ) {
        require(transferredBytes >= 0L && totalBytes >= 0L)
        val first = lastReportedNanos == Long.MIN_VALUE
        val intervalElapsed = !first && nowNanos - lastReportedNanos >= minimumIntervalNanos
        if (!first && !force && !intervalElapsed) return
        if (transferredBytes == lastReportedBytes) return

        listener.onProgress(WifiDirectTcpTransferEngine.Progress(transferredBytes, totalBytes))
        lastReportedNanos = nowNanos
        lastReportedBytes = transferredBytes
    }

    companion object {
        const val DEFAULT_INTERVAL_NANOS = 100_000_000L
    }
}

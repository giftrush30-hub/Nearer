package com.nearer.app

/**
 * Evidence gate for the HyperTransfer stretch goal.
 *
 * 280 MB/s is decimal application throughput (280,000,000 bytes/s), not
 * MiB/s and not the Wi-Fi PHY link rate. A result only qualifies when the
 * same physical device pair, same file, same direction and same test
 * conditions have at least five successful runs for both apps.
 */
internal object HyperTransferPerformanceGate {
    const val TARGET_MEGABYTES_PER_SECOND = 280.0
    const val REQUIRED_SPEEDUP_OVER_XENDER = 7.0
    const val MINIMUM_SUCCESSFUL_RUNS = 5

    // TransferBenchmarkMetrics reports MiB/s (bytes / 1,048,576).
    val TARGET_MEBIBYTES_PER_SECOND: Double
        get() = TARGET_MEGABYTES_PER_SECOND * 1_000_000.0 / (1024.0 * 1024.0)

    data class Result(
        val nearerMedianMiBPerSecond: Double,
        val xenderMedianMiBPerSecond: Double,
        val speedup: Double,
        val meetsAbsoluteTarget: Boolean,
        val meetsSevenTimesTarget: Boolean,
        val hasEnoughNeaRerRuns: Boolean,
        val hasEnoughXenderRuns: Boolean
    ) {
        val goalAchieved: Boolean
            get() = meetsAbsoluteTarget && meetsSevenTimesTarget &&
                hasEnoughNeaRerRuns && hasEnoughXenderRuns
    }

    fun evaluate(
        nearer: TransferBenchmarkSummary,
        xender: TransferBenchmarkSummary
    ): Result {
        require(nearer.medianMibPerSecond.isFinite() && nearer.medianMibPerSecond >= 0.0) {
            "Invalid NeaRer benchmark throughput"
        }
        require(xender.medianMibPerSecond.isFinite() && xender.medianMibPerSecond > 0.0) {
            "Xender baseline must have positive measured throughput"
        }
        val speedup = nearer.medianMibPerSecond / xender.medianMibPerSecond
        return Result(
            nearerMedianMiBPerSecond = nearer.medianMibPerSecond,
            xenderMedianMiBPerSecond = xender.medianMibPerSecond,
            speedup = speedup,
            meetsAbsoluteTarget = nearer.medianMibPerSecond >= TARGET_MEBIBYTES_PER_SECOND,
            meetsSevenTimesTarget = speedup >= REQUIRED_SPEEDUP_OVER_XENDER,
            hasEnoughNeaRerRuns = nearer.successfulRuns >= MINIMUM_SUCCESSFUL_RUNS,
            hasEnoughXenderRuns = xender.successfulRuns >= MINIMUM_SUCCESSFUL_RUNS
        )
    }
}

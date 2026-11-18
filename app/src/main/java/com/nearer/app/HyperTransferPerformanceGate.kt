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

    data class ControlledEvidence(
        val devicePairKey: String,
        val direction: HyperTransferTransportPolicy.Direction,
        val fileSha256: String,
        val fileBytes: Long,
        val conditionsKey: String,
        val runs: List<TransferBenchmarkRun>
    ) {
        init {
            require(devicePairKey.isNotBlank())
            require(fileSha256.matches(Regex("^[a-fA-F0-9]{64}$")))
            require(fileBytes > 0L)
            require(conditionsKey.isNotBlank())
            require(runs.isNotEmpty())
            require(runs.all { !it.succeeded || it.bytesTransferred == fileBytes }) {
                "Successful benchmark runs must transfer the exact comparison file size"
            }
        }
    }

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

    /**
     * Strict comparison entry point. It prevents an apparently impressive
     * speedup from being assembled from different phones, files, directions,
     * or test environments.
     */
    fun evaluateControlled(
        nearer: ControlledEvidence,
        xender: ControlledEvidence
    ): Result {
        require(nearer.devicePairKey == xender.devicePairKey) { "Device pair mismatch" }
        require(nearer.direction == xender.direction) { "Transfer direction mismatch" }
        require(nearer.fileSha256.equals(xender.fileSha256, ignoreCase = true)) { "Test file SHA-256 mismatch" }
        require(nearer.fileBytes == xender.fileBytes) { "Test file size mismatch" }
        require(nearer.conditionsKey == xender.conditionsKey) { "Benchmark conditions mismatch" }
        val nearerSummary = TransferBenchmarkMetrics.summarize(nearer.runs)
        val xenderSummary = TransferBenchmarkMetrics.summarize(xender.runs)
        require(nearerSummary.successfulRuns >= MINIMUM_SUCCESSFUL_RUNS) {
            "NeaRer needs at least five successful controlled runs"
        }
        require(xenderSummary.successfulRuns >= MINIMUM_SUCCESSFUL_RUNS) {
            "Xender needs at least five successful controlled runs"
        }
        return evaluate(nearerSummary, xenderSummary)
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

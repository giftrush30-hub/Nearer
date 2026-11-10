package com.nearer.app

/** One completed run from a controlled, same-device-pair transfer benchmark. */
internal data class TransferBenchmarkRun(
    val bytesTransferred: Long,
    val elapsedMillis: Long,
    val firstByteMillis: Long,
    val succeeded: Boolean
) {
    init {
        require(bytesTransferred >= 0L) { "Transferred bytes cannot be negative" }
        require(elapsedMillis >= 0L) { "Elapsed time cannot be negative" }
        require(firstByteMillis >= 0L) { "First-byte latency cannot be negative" }
        require(firstByteMillis <= elapsedMillis || elapsedMillis == 0L) {
            "First-byte latency cannot exceed elapsed time"
        }
    }
}

internal data class TransferBenchmarkSummary(
    val successfulRuns: Int,
    val failedRuns: Int,
    val medianElapsedMillis: Long,
    val medianFirstByteMillis: Long,
    val medianMibPerSecond: Double,
    val fastestElapsedMillis: Long,
    val slowestElapsedMillis: Long
) {
    /** Values above 1.0 mean this summary was faster than the comparison summary. */
    fun speedupOver(other: TransferBenchmarkSummary): Double {
        require(medianElapsedMillis > 0L && other.medianElapsedMillis > 0L) {
            "Speed comparison requires non-zero median durations"
        }
        return other.medianElapsedMillis.toDouble() / medianElapsedMillis.toDouble()
    }
}

/**
 * Summarizes repeated runs using medians, not the single best result.
 * Failed runs remain visible and are never silently counted as fast successes.
 */
internal object TransferBenchmarkMetrics {
    fun summarize(runs: List<TransferBenchmarkRun>): TransferBenchmarkSummary {
        require(runs.isNotEmpty()) { "At least one benchmark run is required" }
        val successful = runs.filter { it.succeeded && it.elapsedMillis > 0L }
        require(successful.isNotEmpty()) { "No successful non-zero-duration benchmark runs" }
        val elapsed = successful.map { it.elapsedMillis }.sorted()
        val firstByte = successful.map { it.firstByteMillis }.sorted()
        val rates = successful.map {
            it.bytesTransferred.toDouble() / (1024.0 * 1024.0) / (it.elapsedMillis / 1000.0)
        }.sorted()
        return TransferBenchmarkSummary(
            successfulRuns = successful.size,
            failedRuns = runs.size - successful.size,
            medianElapsedMillis = median(elapsed),
            medianFirstByteMillis = median(firstByte),
            medianMibPerSecond = medianDouble(rates),
            fastestElapsedMillis = elapsed.first(),
            slowestElapsedMillis = elapsed.last()
        )
    }

    private fun median(values: List<Long>): Long {
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle]
        else values[middle - 1] / 2L + values[middle] / 2L +
            ((values[middle - 1] % 2L + values[middle] % 2L) / 2L)
    }

    private fun medianDouble(values: List<Double>): Double {
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle]
        else (values[middle - 1] + values[middle]) / 2.0
    }
}

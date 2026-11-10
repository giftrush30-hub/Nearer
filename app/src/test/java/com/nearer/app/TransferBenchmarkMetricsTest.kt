package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferBenchmarkMetricsTest {
    @Test fun usesMedianRatherThanBestCaseAndKeepsFailuresVisible() {
        val summary = TransferBenchmarkMetrics.summarize(listOf(
            TransferBenchmarkRun(100L * 1024 * 1024, 10_000L, 100L, true),
            TransferBenchmarkRun(100L * 1024 * 1024, 20_000L, 200L, true),
            TransferBenchmarkRun(100L * 1024 * 1024, 30_000L, 300L, true),
            TransferBenchmarkRun(100L * 1024 * 1024, 1L, 0L, false)
        ))
        assertEquals(3, summary.successfulRuns)
        assertEquals(1, summary.failedRuns)
        assertEquals(20_000L, summary.medianElapsedMillis)
        assertEquals(200L, summary.medianFirstByteMillis)
        assertEquals(5.0, summary.medianMibPerSecond, 0.0001)
        assertEquals(10_000L, summary.fastestElapsedMillis)
        assertEquals(30_000L, summary.slowestElapsedMillis)
    }

    @Test fun speedupUsesMedianDurations() {
        val faster = TransferBenchmarkMetrics.summarize(listOf(
            TransferBenchmarkRun(1_000L, 5_000L, 100L, true)
        ))
        val slower = TransferBenchmarkMetrics.summarize(listOf(
            TransferBenchmarkRun(1_000L, 10_000L, 200L, true)
        ))
        assertEquals(2.0, faster.speedupOver(slower), 0.0001)
    }

    @Test fun rejectsEmptyOrAllFailedSamples() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkMetrics.summarize(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkMetrics.summarize(listOf(TransferBenchmarkRun(100L, 0L, 0L, true)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkMetrics.summarize(listOf(TransferBenchmarkRun(100L, 10L, 0L, false)))
        }
    }

    @Test fun rejectsInvalidTimingMeasurements() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkRun(100L, 10L, 11L, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkRun(-1L, 10L, 1L, true)
        }
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferRateMetricsTest {
    @Test
    fun computesAverageMebibytesPerSecond() {
        assertEquals(2.0, TransferRateMetrics.averageMebibytesPerSecond(2L * 1024L * 1024L, 1_000_000_000L), 0.000001)
    }

    @Test
    fun zeroDurationIsSafelyClampedForTinyTransfers() {
        assertEquals(
            1_000_000_000.0 / (1024.0 * 1024.0),
            TransferRateMetrics.averageMebibytesPerSecond(1L, 0L),
            0.000001
        )
    }

    @Test
    fun zeroBytesProducesZeroRate() {
        assertEquals(0.0, TransferRateMetrics.averageMebibytesPerSecond(0L, 10L), 0.0)
        assertEquals("0.0", TransferRateMetrics.label(0L, 10L))
    }

    @Test
    fun labelUsesOneDecimalAndStableDecimalSeparator() {
        assertEquals("1.5", TransferRateMetrics.label(3L * 1024L * 1024L, 2_000_000_000L))
    }

    @Test
    fun rejectsNegativeMeasurements() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferRateMetrics.averageMebibytesPerSecond(-1L, 10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferRateMetrics.averageMebibytesPerSecond(1L, -1L)
        }
    }
}

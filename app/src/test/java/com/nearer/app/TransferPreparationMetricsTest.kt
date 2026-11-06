package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferPreparationMetricsTest {
    @Test
    fun computesSourceReadRateSeparatelyFromRadioRate() {
        assertEquals(
            4.0,
            TransferPreparationMetrics.mebibytesPerSecond(8L * 1024L * 1024L, 2_000_000_000L),
            0.000001
        )
    }

    @Test
    fun reportsZeroBytePreparationWithoutDivisionFailure() {
        assertEquals(0.0, TransferPreparationMetrics.mebibytesPerSecond(0L, 0L), 0.0)
        assertTrue(TransferPreparationMetrics.describe(0L, 0L).contains("0.00 s"))
    }

    @Test
    fun clampsOnlyTheRateDenominatorForTinyFiles() {
        assertEquals(
            1_000_000_000.0 / (1024.0 * 1024.0),
            TransferPreparationMetrics.mebibytesPerSecond(1L, 0L),
            0.000001
        )
    }

    @Test
    fun rejectsNegativeMeasurements() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferPreparationMetrics.describe(-1L, 10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferPreparationMetrics.mebibytesPerSecond(1L, -1L)
        }
    }
}

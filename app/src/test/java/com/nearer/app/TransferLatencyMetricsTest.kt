package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferLatencyMetricsTest {
    @Test fun convertsNanosecondsToMilliseconds() {
        assertEquals(125.0, TransferLatencyMetrics.milliseconds(125_000_000L), 0.0001)
    }

    @Test fun formatsFirstByteLatency() {
        assertEquals("250 ms to first byte", TransferLatencyMetrics.firstByteLabel(250_000_000L))
    }

    @Test fun rejectsNegativeElapsedTime() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferLatencyMetrics.milliseconds(-1L)
        }
    }
}

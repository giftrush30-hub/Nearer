package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSpeedMeterTest {
    private class FakeClock(var now: Long = 0L) {
        fun advance(millis: Long) { now += millis }
    }

    @Test
    fun reportsAverageAndRecentApplicationThroughput() {
        val clock = FakeClock()
        val meter = TransferSpeedMeter(clockMillis = { clock.now })

        meter.record(0L, 10_000_000L)
        clock.advance(1_000L)
        val snapshot = meter.record(10_000_000L)

        assertEquals(10_000_000.0, snapshot.averageBytesPerSecond, 0.001)
        assertEquals(10_000_000.0, snapshot.recentBytesPerSecond, 0.001)
        assertEquals(10.0, snapshot.averageMegabytesPerSecond, 0.0001)
        assertEquals(1.0f, snapshot.progressFraction!!)
    }

    @Test
    fun ignoresRegressiveProgressCallbacks() {
        val clock = FakeClock()
        val meter = TransferSpeedMeter(clockMillis = { clock.now })

        meter.record(500L, 1_000L)
        clock.advance(500L)
        val snapshot = meter.record(200L)

        assertEquals(500L, snapshot.transferredBytes)
        assertEquals(1_000L, snapshot.totalBytes)
        assertTrue(snapshot.averageBytesPerSecond >= 0.0)
    }

    @Test
    fun recentRateUsesRollingWindowRatherThanWholeTransferAverage() {
        val clock = FakeClock()
        val meter = TransferSpeedMeter(windowMillis = 2_000L, clockMillis = { clock.now })

        meter.record(0L)
        clock.advance(1_000L)
        meter.record(1_000_000L)
        clock.advance(1_000L)
        meter.record(1_000_000L)
        clock.advance(1_000L)
        val snapshot = meter.record(5_000_000L)

        assertEquals(1_666_666.667, snapshot.averageBytesPerSecond, 0.01)
        assertEquals(2_000_000.0, snapshot.recentBytesPerSecond, 0.001)
        assertEquals(2_000_000.0, snapshot.peakWindowBytesPerSecond, 0.001)
    }

    @Test
    fun unknownTotalDoesNotInventProgressPercentage() {
        val meter = TransferSpeedMeter(clockMillis = { 100L })
        val snapshot = meter.record(123L)

        assertNull(snapshot.progressFraction)
    }

    @Test
    fun resetClearsAllMeasurementState() {
        val clock = FakeClock()
        val meter = TransferSpeedMeter(clockMillis = { clock.now })
        meter.record(100L, 200L)
        clock.advance(1_000L)
        meter.record(200L)

        meter.reset()
        val snapshot = meter.snapshot()

        assertEquals(0L, snapshot.transferredBytes)
        assertEquals(-1L, snapshot.totalBytes)
        assertEquals(0L, snapshot.elapsedMillis)
        assertEquals(0.0, snapshot.averageBytesPerSecond, 0.0)
    }
}

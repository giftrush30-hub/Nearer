package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HyperTransferProgressReporterTest {
    @Test
    fun coalescesRapidRecordProgressIntoOneHundredMillisecondUpdates() {
        val reporter = HyperTransferProgressReporter()
        val updates = mutableListOf<WifiDirectTcpTransferEngine.Progress>()
        val listener = WifiDirectTcpTransferEngine.ProgressListener { updates += it }

        reporter.report(256 * 1024L, 32 * 1024 * 1024L, listener, nowNanos = 1L)
        reporter.report(512 * 1024L, 32 * 1024 * 1024L, listener, nowNanos = 50_000_000L)
        reporter.report(768 * 1024L, 32 * 1024 * 1024L, listener, nowNanos = 99_999_999L)
        assertEquals(1, updates.size)

        reporter.report(1024 * 1024L, 32 * 1024 * 1024L, listener, nowNanos = 100_000_001L)
        assertEquals(2, updates.size)
        assertEquals(1024 * 1024L, updates.last().transferredBytes)
    }

    @Test
    fun forcedFinalUpdateIsDeliveredEvenInsideThrottleWindow() {
        val reporter = HyperTransferProgressReporter()
        val updates = mutableListOf<WifiDirectTcpTransferEngine.Progress>()
        val listener = WifiDirectTcpTransferEngine.ProgressListener { updates += it }

        reporter.report(256 * 1024L, 1024 * 1024L, listener, nowNanos = 1L)
        reporter.report(1024 * 1024L, 1024 * 1024L, listener, force = true, nowNanos = 2L)

        assertEquals(2, updates.size)
        assertEquals(1024 * 1024L, updates.last().transferredBytes)
        assertEquals(1024 * 1024L, updates.last().totalBytes)
    }

    @Test
    fun duplicateFinalByteCountDoesNotEmitAnExtraCallback() {
        val reporter = HyperTransferProgressReporter()
        val updates = mutableListOf<WifiDirectTcpTransferEngine.Progress>()
        val listener = WifiDirectTcpTransferEngine.ProgressListener { updates += it }

        reporter.report(1024L, 1024L, listener, nowNanos = 1L)
        reporter.report(1024L, 1024L, listener, force = true, nowNanos = 2L)

        assertEquals(1, updates.size)
    }
}

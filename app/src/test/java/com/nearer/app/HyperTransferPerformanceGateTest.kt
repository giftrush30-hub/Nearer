package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferPerformanceGateTest {
    private fun summary(rateMiBPerSecond: Double, successful: Int = 5) =
        TransferBenchmarkSummary(
            successfulRuns = successful,
            failedRuns = 0,
            medianElapsedMillis = 1_000L,
            medianFirstByteMillis = 10L,
            medianMibPerSecond = rateMiBPerSecond,
            fastestElapsedMillis = 900L,
            slowestElapsedMillis = 1_100L
        )

    @Test
    fun convertsDecimal280MegabytesPerSecondToMebibytesPerSecond() {
        assertEquals(
            267.02880859375,
            HyperTransferPerformanceGate.TARGET_MEBIBYTES_PER_SECOND,
            0.0000001
        )
    }

    @Test
    fun goalRequiresBoth280MegabytesPerSecondAndSevenTimesBaseline() {
        val nearer = summary(HyperTransferPerformanceGate.TARGET_MEBIBYTES_PER_SECOND)
        val xender = summary(nearer.medianMibPerSecond / 7.0)
        val result = HyperTransferPerformanceGate.evaluate(nearer, xender)

        assertTrue(result.meetsAbsoluteTarget)
        assertTrue(result.meetsSevenTimesTarget)
        assertTrue(result.goalAchieved)
    }

    @Test
    fun highSpeedAloneDoesNotPassWhenSevenTimesBaselineIsNotMet() {
        val nearer = summary(300.0)
        val xender = summary(50.0)
        val result = HyperTransferPerformanceGate.evaluate(nearer, xender)

        assertTrue(result.meetsAbsoluteTarget)
        assertFalse(result.meetsSevenTimesTarget)
        assertFalse(result.goalAchieved)
    }

    @Test
    fun sevenTimesAloneDoesNotPassWhenAbsoluteTargetIsNotMet() {
        val nearer = summary(100.0)
        val xender = summary(10.0)
        val result = HyperTransferPerformanceGate.evaluate(nearer, xender)

        assertFalse(result.meetsAbsoluteTarget)
        assertTrue(result.meetsSevenTimesTarget)
        assertFalse(result.goalAchieved)
    }

    @Test
    fun fewerThanFiveSuccessfulRunsCannotPass() {
        val nearer = summary(400.0, successful = 4)
        val xender = summary(40.0, successful = 5)
        val result = HyperTransferPerformanceGate.evaluate(nearer, xender)

        assertFalse(result.hasEnoughNeaRerRuns)
        assertTrue(result.hasEnoughXenderRuns)
        assertFalse(result.goalAchieved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroXenderBaseline() {
        HyperTransferPerformanceGate.evaluate(summary(300.0), summary(0.0))
    }
}

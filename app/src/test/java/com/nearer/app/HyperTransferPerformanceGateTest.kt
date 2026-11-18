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


    private fun evidence(bytesPerSecond: Long, devicePair: String = "pair-A",
                         direction: HyperTransferTransportPolicy.Direction = HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
                         hash: String = "a".repeat(64), conditions: String = "same-room-same-settings") =
        HyperTransferPerformanceGate.ControlledEvidence(
            devicePairKey = devicePair,
            direction = direction,
            fileSha256 = hash,
            fileBytes = 280_000_000L,
            conditionsKey = conditions,
            runs = List(5) {
                TransferBenchmarkRun(280_000_000L, 280_000_000_000L / bytesPerSecond,
                    25L, true)
            }
        )

    @Test
    fun controlledEvidencePassesOnlyWhenBothAppsMeetBothTargets() {
        val result = HyperTransferPerformanceGate.evaluateControlled(
            evidence(280_000_000L),
            evidence(40_000_000L)
        )
        assertTrue(result.goalAchieved)
        assertEquals(7.0, result.speedup, 0.0001)
    }

    @Test
    fun controlledEvidenceRejectsDifferentPhonePairs() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferPerformanceGate.evaluateControlled(
                evidence(280_000_000L),
                evidence(40_000_000L, devicePair = "pair-B")
            )
        }
    }

    @Test
    fun controlledEvidenceRejectsDifferentFilesDirectionsAndConditions() {
        val base = evidence(280_000_000L)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferPerformanceGate.evaluateControlled(base, evidence(40_000_000L, hash = "b".repeat(64)))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferPerformanceGate.evaluateControlled(
                base,
                evidence(40_000_000L, direction = HyperTransferTransportPolicy.Direction.REMOTE_TO_LOCAL)
            )
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferPerformanceGate.evaluateControlled(
                base,
                evidence(40_000_000L, conditions = "different-room")
            )
        }
    }

    @Test
    fun controlledEvidenceRequiresFiveSuccessfulRunsForEachApp() {
        val insufficient = evidence(280_000_000L).copy(
            runs = List(4) { TransferBenchmarkRun(280_000_000L, 1_000L, 10L, true) }
        )
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferPerformanceGate.evaluateControlled(insufficient, evidence(40_000_000L))
        }
    }

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

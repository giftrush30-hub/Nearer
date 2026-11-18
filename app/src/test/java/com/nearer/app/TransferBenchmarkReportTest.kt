package com.nearer.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferBenchmarkReportTest {
    @Test fun exportsEveryAttemptAndBothThroughputUnits() {
        val evidence = TransferBenchmarkReport.Evidence(
            app = "NeaRer",
            devicePairKey = "phone-A__phone-B",
            direction = HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
            fileSha256 = "A".repeat(64),
            fileBytes = 10_000_000L,
            conditionsKey = "same room, screen on",
            runs = listOf(
                TransferBenchmarkRun(10_000_000L, 1_000L, 25L, true),
                TransferBenchmarkRun(0L, 500L, 0L, false)
            )
        )
        val csv = TransferBenchmarkReport.toCsv(listOf(evidence))
        assertTrue(csv.contains("\"same room, screen on\""))
        assertTrue(csv.contains("NeaRer,phone-A__phone-B,LOCAL_TO_REMOTE,"))
        assertTrue(csv.contains(",1,true,10000000,1000,25,10.000000,9.536743"))
        assertTrue(csv.contains(",2,false,0,500,0,,"))
        assertEquals(3, csv.trimEnd().lines().size)
    }

    @Test fun escapesQuotesAndRejectsMismatchedSuccessfulFileSize() {
        val evidence = TransferBenchmarkReport.Evidence(
            app = "Xender \"test\"",
            devicePairKey = "A__B",
            direction = HyperTransferTransportPolicy.Direction.REMOTE_TO_LOCAL,
            fileSha256 = "b".repeat(64),
            fileBytes = 1L,
            conditionsKey = "baseline",
            runs = listOf(TransferBenchmarkRun(1L, 1L, 0L, true))
        )
        assertTrue(TransferBenchmarkReport.toCsv(listOf(evidence)).contains("\"Xender \"\"test\"\"\""))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TransferBenchmarkReport.Evidence(
                "NeaRer", "A__B", HyperTransferTransportPolicy.Direction.LOCAL_TO_REMOTE,
                "c".repeat(64), 10L, "same", listOf(TransferBenchmarkRun(9L, 1L, 0L, true))
            )
        }
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferConcurrencyPolicyTest {
    @Test fun capableCoolPhoneUsesTwoFileWindow() {
        assertEquals(
            TransferConcurrencyPolicy.NORMAL_WINDOW,
            TransferConcurrencyPolicy.maxParallelFiles(
                availableMemoryBytes = 1024L * 1024L * 1024L,
                lowMemory = false,
                lowRamDevice = false,
                thermalStatus = 0
            )
        )
    }

    @Test fun lowMemoryOrLowRamUsesSingleFileWindow() {
        assertEquals(1, TransferConcurrencyPolicy.maxParallelFiles(128L * 1024L * 1024L, false, false, 0))
        assertEquals(1, TransferConcurrencyPolicy.maxParallelFiles(1024L * 1024L * 1024L, true, false, 0))
        assertEquals(1, TransferConcurrencyPolicy.maxParallelFiles(1024L * 1024L * 1024L, false, true, 0))
    }

    @Test fun severeThermalPressureReducesConcurrency() {
        assertEquals(1, TransferConcurrencyPolicy.maxParallelFiles(1024L * 1024L * 1024L, false, false, 3))
        assertEquals(1, TransferConcurrencyPolicy.maxParallelFiles(1024L * 1024L * 1024L, false, false, 6))
    }

    @Test fun moderateThermalPressureKeepsConservativeTwoFileWindow() {
        assertEquals(2, TransferConcurrencyPolicy.maxParallelFiles(1024L * 1024L * 1024L, false, false, 2))
    }

    @Test fun invalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferConcurrencyPolicy.maxParallelFiles(-1L, false, false, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferConcurrencyPolicy.maxParallelFiles(1L, false, false, 7)
        }
    }
}

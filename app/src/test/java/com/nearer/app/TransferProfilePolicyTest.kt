package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferProfilePolicyTest {
    private val strongPhone = TransferDeviceSnapshot(
        availableMemoryBytes = 3L * 1024L * 1024L * 1024L,
        sustainedWriteBytesPerSecond = 120L * 1024L * 1024L,
        thermalStatus = 0
    )

    @Test
    fun bothStrongPhonesAndStableFastLinkSelectTurbo() {
        assertEquals(
            TransferProfile.TURBO,
            TransferProfilePolicy.choose(strongPhone, strongPhone, 100L * 1024L * 1024L, true)
        )
    }

    @Test
    fun weakestPhoneControlsProfile() {
        val constrained = strongPhone.copy(
            availableMemoryBytes = 500L * 1024L * 1024L,
            sustainedWriteBytesPerSecond = 25L * 1024L * 1024L
        )
        assertEquals(
            TransferProfile.FAST,
            TransferProfilePolicy.choose(strongPhone, constrained, 100L * 1024L * 1024L, true)
        )
    }

    @Test
    fun lowRamReceiverForcesLiteEvenWithPowerfulSender() {
        val lowRam = strongPhone.copy(lowRamDevice = true)
        assertEquals(
            TransferProfile.LITE,
            TransferProfilePolicy.choose(strongPhone, lowRam, 100L * 1024L * 1024L, true)
        )
    }

    @Test
    fun unstableOrUnmeasuredLinkNeverSelectsTurbo() {
        assertEquals(
            TransferProfile.LITE,
            TransferProfilePolicy.choose(strongPhone, strongPhone, 100L * 1024L * 1024L, false)
        )
        assertEquals(
            TransferProfile.LITE,
            TransferProfilePolicy.choose(strongPhone, strongPhone, null, true)
        )
    }

    @Test
    fun severeThermalPressureOnEitherPhoneForcesLite() {
        val hotReceiver = strongPhone.copy(thermalStatus = 4)
        assertEquals(
            TransferProfile.LITE,
            TransferProfilePolicy.choose(strongPhone, hotReceiver, 100L * 1024L * 1024L, true)
        )
    }

    @Test
    fun moderateThermalPressureSelectsFastNotTurbo() {
        val warmSender = strongPhone.copy(thermalStatus = 2)
        assertEquals(
            TransferProfile.FAST,
            TransferProfilePolicy.choose(warmSender, strongPhone, 100L * 1024L * 1024L, true)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeMeasuredLinkRateIsRejected() {
        TransferProfilePolicy.choose(strongPhone, strongPhone, -1L, true)
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HyperTransferModePolicyTest {
    private fun profile(
        memory: Long = 2L * GB,
        storage: Long = 4L * GB,
        thermal: Int = 0,
        lowMemory: Boolean = false,
        lowRam: Boolean = false,
        stream: Boolean = true,
        throughput: Long = 12L * MB
    ) = HyperTransferModePolicy.DeviceProfile(
        availableMemoryBytes = memory,
        availableStorageBytes = storage,
        thermalStatus = thermal,
        lowMemory = lowMemory,
        lowRamDevice = lowRam,
        streamCapable = stream,
        measuredBytesPerSecond = throughput
    )

    @Test fun capableCoolPeersWithMeasuredHeadroomCanUseTurbo() {
        assertEquals(
            HyperTransferModePolicy.Mode.TURBO,
            HyperTransferModePolicy.negotiate(profile(), profile())
        )
    }

    @Test fun oneConstrainedPhoneCapsBothPeersAtItsSafeMode() {
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(lowRam = true))
        )
    }

    @Test fun severeThermalPressureForcesLite() {
        assertEquals(
            HyperTransferModePolicy.Mode.LITE,
            HyperTransferModePolicy.negotiate(profile(), profile(thermal = 3))
        )
    }

    @Test fun moderateThermalPressureCapsAtFastButLightPressureDoesNot() {
        assertEquals(
            HyperTransferModePolicy.Mode.TURBO,
            HyperTransferModePolicy.negotiate(profile(), profile(thermal = 1))
        )
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(thermal = 2))
        )
    }

    @Test fun lowMemorySignalCapsModeEvenWhenRawAvailableMemoryLooksHigh() {
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(lowMemory = true, memory = 512L * MB))
        )
    }

    @Test fun lowAvailableMemoryForcesLite() {
        assertEquals(
            HyperTransferModePolicy.Mode.LITE,
            HyperTransferModePolicy.negotiate(profile(), profile(memory = 64L * MB))
        )
    }

    @Test fun lowStorageAndUnprovenThroughputPreventTurbo() {
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(storage = 256L * MB))
        )
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(throughput = 2L * MB))
        )
    }

    @Test fun oldPeerWithoutStreamSupportNeverGetsTurbo() {
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), profile(stream = false))
        )
    }

    @Test fun missingPeerProfileFallsBackToFast() {
        assertEquals(
            HyperTransferModePolicy.Mode.FAST,
            HyperTransferModePolicy.negotiate(profile(), null)
        )
    }

    @Test fun missingPeerProfileNeverOverridesLocalLiteSafetyCap() {
        assertEquals(
            HyperTransferModePolicy.Mode.LITE,
            HyperTransferModePolicy.negotiate(profile(thermal = 3), null)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeMemoryIsRejected() {
        profile(memory = -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedThermalStatusIsRejected() {
        profile(thermal = 7)
    }

    @Test fun negotiationIsSymmetricRegardlessOfWhichPeerStarts() {
        val constrained = profile(lowRam = true)
        val capable = profile()
        assertEquals(
            HyperTransferModePolicy.negotiate(constrained, capable),
            HyperTransferModePolicy.negotiate(capable, constrained)
        )
    }

    @Test fun minimumModeIsCommutativeAndNeverExceedsEitherPeer() {
        for (first in HyperTransferModePolicy.Mode.values()) {
            for (second in HyperTransferModePolicy.Mode.values()) {
                val forward = HyperTransferModePolicy.minMode(first, second)
                val reverse = HyperTransferModePolicy.minMode(second, first)
                assertEquals(forward, reverse)
                assertEquals(if (first.rank <= second.rank) first else second, forward)
            }
        }
    }

    @Test fun receiveBuffersAreStrictlyBoundedByMode() {
        assertEquals(32 * 1024, HyperTransferModePolicy.receiveBufferBytes(HyperTransferModePolicy.Mode.LITE))
        assertEquals(128 * 1024, HyperTransferModePolicy.receiveBufferBytes(HyperTransferModePolicy.Mode.FAST))
        assertEquals(256 * 1024, HyperTransferModePolicy.receiveBufferBytes(HyperTransferModePolicy.Mode.TURBO))
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val GB = 1024L * MB
    }
}

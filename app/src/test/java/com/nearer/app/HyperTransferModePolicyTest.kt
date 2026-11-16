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
            HyperTransferModePolicy.negotiate(profile(), profile(thermal = 4))
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

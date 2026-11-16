package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferPeerProfileCodecTest {
    private val profile = HyperTransferModePolicy.DeviceProfile(
        availableMemoryBytes = 768L * 1024L * 1024L,
        availableStorageBytes = 3L * 1024L * 1024L * 1024L,
        thermalStatus = 1,
        lowMemory = false,
        lowRamDevice = true,
        streamCapable = true,
        measuredBytesPerSecond = 5_000_000L
    )

    @Test fun roundTripPreservesEveryNegotiatedField() {
        val encoded = HyperTransferPeerProfileCodec.encode(profile)
        assertTrue(HyperTransferPeerProfileCodec.isEnvelope(encoded))
        assertEquals(profile, HyperTransferPeerProfileCodec.decode(encoded))
    }

    @Test fun malformedAndOversizedPayloadsAreNotRecognized() {
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope(byteArrayOf()))
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope(ByteArray(2_049)))
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope("not-json".toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeRemoteMemoryIsRejected() {
        val invalid = """{"kind":"hypertransfer_resource_profile_v1","version":1,"availableMemoryBytes":-1,"availableStorageBytes":1,"thermalStatus":0,"lowMemory":false,"lowRamDevice":false,"streamCapable":true,"measuredBytesPerSecond":0}"""
        HyperTransferPeerProfileCodec.decode(invalid.toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedVersionIsRejected() {
        val invalid = """{"kind":"hypertransfer_resource_profile_v1","version":99,"availableMemoryBytes":1,"availableStorageBytes":1,"thermalStatus":0,"lowMemory":false,"lowRamDevice":false,"streamCapable":true,"measuredBytesPerSecond":0}"""
        HyperTransferPeerProfileCodec.decode(invalid.toByteArray())
    }
}

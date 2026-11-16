package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    @Test fun malformedAndWrongSizedPayloadsAreNotRecognized() {
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope(byteArrayOf()))
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope(ByteArray(31)))
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope(ByteArray(33)))
        assertFalse(HyperTransferPeerProfileCodec.isEnvelope("not-a-profile".toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeRemoteMemoryIsRejected() {
        val encoded = HyperTransferPeerProfileCodec.encode(profile)
        ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN).putLong(8, -1L)
        HyperTransferPeerProfileCodec.decode(encoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedVersionIsRejected() {
        val encoded = HyperTransferPeerProfileCodec.encode(profile)
        ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN).putShort(4, 99)
        HyperTransferPeerProfileCodec.decode(encoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownFlagsAreRejected() {
        val encoded = HyperTransferPeerProfileCodec.encode(profile)
        encoded[6] = 0x40
        HyperTransferPeerProfileCodec.decode(encoded)
    }
}

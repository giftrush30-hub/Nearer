package com.nearer.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fixed-size resource profile exchanged only inside the authenticated,
 * encrypted HyperTransfer control channel. The surrounding session binds the
 * packet to the verified stable peer identity.
 */
internal object HyperTransferPeerProfileCodec {
    private const val MAGIC = 0x4E525048 // "NRPH"
    private const val VERSION: Short = 1
    private const val FLAG_LOW_MEMORY = 1
    private const val FLAG_LOW_RAM = 1 shl 1
    private const val FLAG_STREAM_CAPABLE = 1 shl 2
    private const val KNOWN_FLAGS = FLAG_LOW_MEMORY or FLAG_LOW_RAM or FLAG_STREAM_CAPABLE
    private const val ENCODED_BYTES = 4 + 2 + 1 + 1 + 8 + 8 + 8

    fun encode(profile: HyperTransferModePolicy.DeviceProfile): ByteArray {
        val flags = (if (profile.lowMemory) FLAG_LOW_MEMORY else 0) or
            (if (profile.lowRamDevice) FLAG_LOW_RAM else 0) or
            (if (profile.streamCapable) FLAG_STREAM_CAPABLE else 0)
        return ByteBuffer.allocate(ENCODED_BYTES).order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC)
            .putShort(VERSION)
            .put(flags.toByte())
            .put(profile.thermalStatus.toByte())
            .putLong(profile.availableMemoryBytes)
            .putLong(profile.availableStorageBytes)
            .putLong(profile.measuredBytesPerSecond)
            .array()
    }

    fun isEnvelope(bytes: ByteArray): Boolean =
        bytes.size == ENCODED_BYTES &&
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int == MAGIC

    fun decode(bytes: ByteArray): HyperTransferModePolicy.DeviceProfile {
        require(bytes.size == ENCODED_BYTES) { "Invalid resource profile size" }
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(input.int == MAGIC) { "Unexpected resource profile kind" }
        require(input.short == VERSION) { "Unsupported resource profile version" }
        val flags = input.get().toInt() and 0xff
        require(flags and KNOWN_FLAGS.inv() == 0) { "Unknown resource profile flags" }
        val thermal = input.get().toInt() and 0xff
        val memory = input.long
        val storage = input.long
        val throughput = input.long
        return HyperTransferModePolicy.DeviceProfile(
            availableMemoryBytes = memory,
            availableStorageBytes = storage,
            thermalStatus = thermal,
            lowMemory = flags and FLAG_LOW_MEMORY != 0,
            lowRamDevice = flags and FLAG_LOW_RAM != 0,
            streamCapable = flags and FLAG_STREAM_CAPABLE != 0,
            measuredBytesPerSecond = throughput
        )
    }
}

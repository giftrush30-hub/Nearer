package com.nearer.app

import org.json.JSONObject

/**
 * Resource profile exchanged only inside the authenticated, encrypted
 * HyperTransfer control channel. The surrounding session binds the packet to
 * the stable peer identity; this codec validates the profile fields.
 */
internal object HyperTransferPeerProfileCodec {
    private const val KIND = "hypertransfer_resource_profile_v1"
    private const val VERSION = 1
    private const val MAX_ENCODED_BYTES = 2_048

    fun encode(profile: HyperTransferModePolicy.DeviceProfile): ByteArray =
        JSONObject()
            .put("kind", KIND)
            .put("version", VERSION)
            .put("availableMemoryBytes", profile.availableMemoryBytes)
            .put("availableStorageBytes", profile.availableStorageBytes)
            .put("thermalStatus", profile.thermalStatus)
            .put("lowMemory", profile.lowMemory)
            .put("lowRamDevice", profile.lowRamDevice)
            .put("streamCapable", profile.streamCapable)
            .put("measuredBytesPerSecond", profile.measuredBytesPerSecond)
            .toString()
            .toByteArray(Charsets.UTF_8)
            .also { require(it.size <= MAX_ENCODED_BYTES) }

    fun isEnvelope(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_ENCODED_BYTES) return false
        return runCatching {
            JSONObject(bytes.toString(Charsets.UTF_8)).optString("kind") == KIND
        }.getOrDefault(false)
    }

    fun decode(bytes: ByteArray): HyperTransferModePolicy.DeviceProfile {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENCODED_BYTES) {
            "Invalid resource profile size"
        }
        val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
        require(envelope.getString("kind") == KIND) { "Unexpected resource profile kind" }
        require(envelope.getInt("version") == VERSION) { "Unsupported resource profile version" }
        val memory = envelope.getLong("availableMemoryBytes")
        val storage = envelope.getLong("availableStorageBytes")
        val thermal = envelope.getInt("thermalStatus")
        val lowMemory = envelope.getBoolean("lowMemory")
        val lowRam = envelope.getBoolean("lowRamDevice")
        val stream = envelope.getBoolean("streamCapable")
        val throughput = envelope.getLong("measuredBytesPerSecond")
        return HyperTransferModePolicy.DeviceProfile(
            availableMemoryBytes = memory,
            availableStorageBytes = storage,
            thermalStatus = thermal,
            lowMemory = lowMemory,
            lowRamDevice = lowRam,
            streamCapable = stream,
            measuredBytesPerSecond = throughput
        )
    }
}

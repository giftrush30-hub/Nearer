package com.nearer.app

/**
 * Conservative capability negotiation for a two-phone transfer.
 *
 * The weakest endpoint and the measured link determine the profile. Thresholds
 * are starting heuristics, not device guarantees; tune them only from benchmark
 * data. This policy does not itself create parallel transfers or change radios.
 */
internal enum class TransferProfile {
    LITE,
    FAST,
    TURBO
}

internal data class TransferDeviceSnapshot(
    val availableMemoryBytes: Long,
    val sustainedWriteBytesPerSecond: Long,
    val thermalStatus: Int,
    val lowRamDevice: Boolean = false
) {
    init {
        require(availableMemoryBytes >= 0L) { "Available memory cannot be negative" }
        require(sustainedWriteBytesPerSecond >= 0L) { "Write rate cannot be negative" }
        require(thermalStatus in 0..6) { "Thermal status must be in Android's 0..6 range" }
    }
}

internal object TransferProfilePolicy {
    private const val MIB = 1024L * 1024L
    private const val LITE_MEMORY = 256L * MIB
    private const val FAST_MEMORY = 768L * MIB
    private const val LITE_WRITE_RATE = 8L * MIB
    private const val FAST_WRITE_RATE = 40L * MIB
    private const val LITE_LINK_RATE = 8L * MIB
    private const val FAST_LINK_RATE = 40L * MIB

    fun choose(
        sender: TransferDeviceSnapshot,
        receiver: TransferDeviceSnapshot,
        measuredLinkBytesPerSecond: Long?,
        linkStable: Boolean
    ): TransferProfile {
        require(measuredLinkBytesPerSecond == null || measuredLinkBytesPerSecond >= 0L) {
            "Measured link rate cannot be negative"
        }

        val weakestMemory = minOf(sender.availableMemoryBytes, receiver.availableMemoryBytes)
        val weakestWriteRate = minOf(
            sender.sustainedWriteBytesPerSecond,
            receiver.sustainedWriteBytesPerSecond
        )
        val worstThermalStatus = maxOf(sender.thermalStatus, receiver.thermalStatus)

        if (
            sender.lowRamDevice || receiver.lowRamDevice ||
            weakestMemory < LITE_MEMORY ||
            weakestWriteRate < LITE_WRITE_RATE ||
            worstThermalStatus >= 3 ||
            !linkStable ||
            measuredLinkBytesPerSecond == null ||
            measuredLinkBytesPerSecond < LITE_LINK_RATE
        ) return TransferProfile.LITE

        if (
            weakestMemory < FAST_MEMORY ||
            weakestWriteRate < FAST_WRITE_RATE ||
            worstThermalStatus >= 2 ||
            measuredLinkBytesPerSecond < FAST_LINK_RATE
        ) return TransferProfile.FAST

        return TransferProfile.TURBO
    }
}

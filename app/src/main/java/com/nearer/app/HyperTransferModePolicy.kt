package com.nearer.app

/**
 * Conservative, symmetric HyperTransfer mode negotiation.
 *
 * A phone reports only local resource facts and its measured application-level
 * throughput. The effective mode is the lower of the two phones' safe modes:
 * one fast phone can never force a constrained receiver into TURBO.
 *
 * This policy is intentionally separate from Nearby's radio strategy. It does
 * not switch radios or promise a throughput multiplier.
 */
internal object HyperTransferModePolicy {
    enum class Mode(val rank: Int) {
        LITE(0),
        FAST(1),
        TURBO(2)
    }

    data class DeviceProfile(
        val availableMemoryBytes: Long,
        val availableStorageBytes: Long,
        val thermalStatus: Int,
        val lowMemory: Boolean,
        val lowRamDevice: Boolean,
        val streamCapable: Boolean,
        val measuredBytesPerSecond: Long
    ) {
        init {
            require(availableMemoryBytes >= 0L)
            require(availableStorageBytes >= 0L)
            require(thermalStatus in 0..6)
            require(measuredBytesPerSecond >= 0L)
        }
    }

    fun localSafeMode(profile: DeviceProfile): Mode = when {
        profile.thermalStatus >= 3 -> Mode.LITE
        profile.lowMemory && profile.availableMemoryBytes < MIN_FAST_MEMORY_BYTES -> Mode.LITE
        profile.availableMemoryBytes < MIN_LITE_MEMORY_BYTES -> Mode.LITE
        profile.lowRamDevice || profile.lowMemory -> Mode.FAST
        profile.thermalStatus >= 2 -> Mode.FAST
        profile.availableMemoryBytes < MIN_TURBO_MEMORY_BYTES -> Mode.FAST
        profile.availableStorageBytes < MIN_TURBO_STORAGE_BYTES -> Mode.FAST
        !profile.streamCapable -> Mode.FAST
        profile.measuredBytesPerSecond < MIN_TURBO_THROUGHPUT_BYTES_PER_SECOND -> Mode.FAST
        else -> Mode.TURBO
    }

    /**
     * Both peers must support the stream path before TURBO can be selected.
     * A missing peer profile safely falls back to FAST rather than guessing.
     */
    fun negotiate(local: DeviceProfile, remote: DeviceProfile?): Mode {
        if (remote == null) return minMode(localSafeMode(local), Mode.FAST)
        val localMode = localSafeMode(local)
        val remoteMode = localSafeMode(remote)
        return minMode(localMode, remoteMode)
    }

    fun minMode(first: Mode, second: Mode): Mode =
        if (first.rank <= second.rank) first else second

    /** Buffer sizes are bounded and never allocate based on file size. */
    fun receiveBufferBytes(mode: Mode): Int = when (mode) {
        Mode.LITE -> 32 * 1024
        Mode.FAST -> 128 * 1024
        Mode.TURBO -> 256 * 1024
    }

    private const val MIN_LITE_MEMORY_BYTES = 96L * 1024L * 1024L
    private const val MIN_FAST_MEMORY_BYTES = 192L * 1024L * 1024L
    private const val MIN_TURBO_MEMORY_BYTES = 1024L * 1024L * 1024L
    private const val MIN_TURBO_STORAGE_BYTES = 1024L * 1024L * 1024L
    private const val MIN_TURBO_THROUGHPUT_BYTES_PER_SECOND = 8L * 1024L * 1024L
}

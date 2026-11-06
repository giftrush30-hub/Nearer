package com.nearer.app

/**
 * Conservative sender-side concurrency cap for independent Nearby FILE payloads.
 *
 * Two is the current experimental ceiling. We deliberately do not raise it to
 * three or more until repeatable device benchmarks show a benefit without
 * increasing thermal throttling, failures, or receiver-side storage contention.
 */
internal object TransferConcurrencyPolicy {
    const val NORMAL_WINDOW = 2
    const val CONSTRAINED_WINDOW = 1
    private const val MIN_AVAILABLE_MEMORY_BYTES = 256L * 1024L * 1024L

    fun maxParallelFiles(
        availableMemoryBytes: Long,
        lowMemory: Boolean,
        lowRamDevice: Boolean,
        thermalStatus: Int
    ): Int {
        require(availableMemoryBytes >= 0L) { "Available memory cannot be negative" }
        require(thermalStatus in 0..6) { "Thermal status must be in Android's 0..6 range" }
        return if (
            lowMemory || lowRamDevice ||
            availableMemoryBytes < MIN_AVAILABLE_MEMORY_BYTES ||
            thermalStatus >= 3
        ) CONSTRAINED_WINDOW else NORMAL_WINDOW
    }
}

package com.nearer.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Conservative tuning for NeaRer's local file-save stage.
 *
 * This does not change the radio transport or promise a network speed increase.
 * It reduces per-read/write overhead on capable phones while backing off on
 * low-memory and thermally constrained devices.
 */
internal object HyperTransferTuning {
    const val BUFFER_SMALL = 32 * 1024
    const val BUFFER_BALANCED = 128 * 1024
    const val BUFFER_FAST = 256 * 1024

    private const val LOW_MEMORY_BYTES = 192L * 1024L * 1024L

    fun receiveBufferBytes(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val lowRamDevice = activityManager?.isLowRamDevice == true
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.currentThermalStatus ?: 0
        } else {
            0
        }
        return chooseReceiveBufferBytes(
            availableMemoryBytes = memory.availMem,
            lowRamDevice = lowRamDevice,
            thermalStatus = thermalStatus
        )
    }

    /**
     * Android thermal status values: 0 none, 1 light, 2 moderate, 3 severe,
     * 4 critical, 5 emergency, 6 shutdown.
     */
    internal fun chooseReceiveBufferBytes(
        availableMemoryBytes: Long,
        lowRamDevice: Boolean,
        thermalStatus: Int
    ): Int = when {
        thermalStatus >= 3 -> BUFFER_SMALL
        lowRamDevice || availableMemoryBytes < LOW_MEMORY_BYTES -> BUFFER_BALANCED
        thermalStatus >= 2 -> BUFFER_BALANCED
        else -> BUFFER_FAST
    }
}

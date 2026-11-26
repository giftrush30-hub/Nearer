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
    const val BUFFER_FAST = 1024 * 1024

    const val STREAM_BUFFER_SMALL = 64 * 1024
    const val STREAM_BUFFER_BALANCED = 256 * 1024
    const val STREAM_BUFFER_FAST = 1024 * 1024

    const val SOCKET_BUFFER_SMALL = 1024 * 1024
    const val SOCKET_BUFFER_BALANCED = 4 * 1024 * 1024
    const val SOCKET_BUFFER_FAST = 8 * 1024 * 1024

    private const val LOW_MEMORY_BYTES = 192L * 1024L * 1024L

    /**
     * A 1 MiB userspace buffer batches four 256 KiB AEAD records into fewer
     * buffered I/O writes. Back off on low-memory and thermally constrained phones.
     */
    fun transferBufferBytes(context: Context): Int = chooseTransferBufferBytes(
        availableMemoryBytes(context), isLowMemory(context), isLowRamDevice(context), thermalStatus(context)
    )

    /** Kernel socket-window hints are clamped by the OS; inspect effective values on real devices. */
    fun socketBufferBytes(context: Context): Int = chooseSocketBufferBytes(
        availableMemoryBytes(context), isLowMemory(context), isLowRamDevice(context), thermalStatus(context)
    )

    internal fun chooseTransferBufferBytes(
        availableMemoryBytes: Long, lowMemory: Boolean, lowRamDevice: Boolean, thermalStatus: Int
    ): Int = when {
        thermalStatus >= 3 -> STREAM_BUFFER_SMALL
        lowMemory || lowRamDevice || availableMemoryBytes < LOW_MEMORY_BYTES -> STREAM_BUFFER_BALANCED
        thermalStatus >= 2 -> STREAM_BUFFER_BALANCED
        else -> STREAM_BUFFER_FAST
    }

    internal fun chooseSocketBufferBytes(
        availableMemoryBytes: Long, lowMemory: Boolean, lowRamDevice: Boolean, thermalStatus: Int
    ): Int = when {
        thermalStatus >= 3 -> SOCKET_BUFFER_SMALL
        lowMemory || lowRamDevice || availableMemoryBytes < LOW_MEMORY_BYTES -> SOCKET_BUFFER_BALANCED
        thermalStatus >= 2 -> SOCKET_BUFFER_BALANCED
        else -> SOCKET_BUFFER_FAST
    }

    private fun availableMemoryBytes(context: Context): Long = memoryInfo(context).availMem
    private fun isLowMemory(context: Context): Boolean = memoryInfo(context).lowMemory
    private fun isLowRamDevice(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    private fun thermalStatus(context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: 0
        } else 0
    private fun memoryInfo(context: Context): ActivityManager.MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
        return info
    }

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
            lowMemory = memory.lowMemory,
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
        lowMemory: Boolean,
        lowRamDevice: Boolean,
        thermalStatus: Int
    ): Int = when {
        thermalStatus >= 3 -> BUFFER_SMALL
        lowMemory || lowRamDevice || availableMemoryBytes < LOW_MEMORY_BYTES -> BUFFER_BALANCED
        thermalStatus >= 2 -> BUFFER_BALANCED
        else -> BUFFER_FAST
    }
}

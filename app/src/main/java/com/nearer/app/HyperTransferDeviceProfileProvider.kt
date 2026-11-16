package com.nearer.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/**
 * Captures only local, transient device headroom for a transfer session.
 *
 * The caller supplies the latest application-level throughput sample and
 * negotiated STREAM capability. No device profile is persisted or transmitted
 * by this class; a future authenticated handshake must explicitly encode only
 * the fields it needs.
 */
internal object HyperTransferDeviceProfileProvider {
    fun capture(
        context: Context,
        streamCapable: Boolean,
        measuredBytesPerSecond: Long
    ): HyperTransferModePolicy.DeviceProfile {
        val appContext = context.applicationContext
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val availableStorage = runCatching {
            StatFs(appContext.filesDir.absolutePath).availableBytes
        }.getOrDefault(0L)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            }.getOrDefault(0)
        } else {
            // Thermal APIs are unavailable on older Android releases; policy
            // still uses memory, storage and measured throughput conservatively.
            0
        }

        return HyperTransferModePolicy.DeviceProfile(
            availableMemoryBytes = memory.availMem.coerceAtLeast(0L),
            availableStorageBytes = availableStorage.coerceAtLeast(0L),
            thermalStatus = thermalStatus.coerceIn(0, 6),
            lowMemory = memory.lowMemory,
            lowRamDevice = activityManager.isLowRamDevice,
            streamCapable = streamCapable,
            measuredBytesPerSecond = measuredBytesPerSecond.coerceAtLeast(0L)
        )
    }
}

package com.nearer.app

internal object StudioOboeEngine {
    private val available: Boolean = runCatching {
        System.loadLibrary("nearer_audio")
    }.isSuccess

    external fun nativeStart(inputDeviceId: Int, preferUnprocessedInput: Boolean): Boolean
    external fun nativeStop()
    external fun nativeReadAnalysis(destination: FloatArray): Int
    external fun nativeGetStats(): LongArray
    external fun nativeIsRunning(): Boolean

    fun start(inputDeviceId: Int?, preferUnprocessedInput: Boolean): Boolean =
        available && runCatching { nativeStart(inputDeviceId ?: -1, preferUnprocessedInput) }.getOrDefault(false)

    fun stop() {
        if (available) runCatching { nativeStop() }
    }

    fun readAnalysis(destination: FloatArray): Int =
        if (available) runCatching { nativeReadAnalysis(destination) }.getOrDefault(0) else 0

    fun diagnostics(): LongArray =
        if (available) runCatching { nativeGetStats() }.getOrDefault(longArrayOf()) else longArrayOf()

    fun isRunning(): Boolean =
        available && runCatching { nativeIsRunning() }.getOrDefault(false)
}

internal object StudioAudioEnginePolicy {
    /** AAudio begins at API 27; USB device-id routing is only reliable from API 28. */
    fun useOboe(apiLevel: Int, preferredInputId: Int?): Boolean =
        apiLevel >= 27 && (preferredInputId == null || apiLevel >= 28)
}

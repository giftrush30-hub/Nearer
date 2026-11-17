package com.nearer.app

internal object StudioOboeEngine {
    private val available: Boolean = runCatching {
        System.loadLibrary("nearer_audio")
    }.isSuccess

    external fun nativeStart(inputDeviceId: Int, preferUnprocessedInput: Boolean, privacySensitiveCapture: Boolean): Boolean
    external fun nativeStop()
    external fun nativeReadAnalysis(destination: FloatArray): Int
    external fun nativeGetStats(): LongArray
    external fun nativeIsRunning(): Boolean

    fun start(inputDeviceId: Int?, preferUnprocessedInput: Boolean): Boolean =
        available && runCatching { nativeStart(inputDeviceId ?: -1, preferUnprocessedInput, StudioAudioEnginePolicy.usePrivacySensitiveCapture(android.os.Build.VERSION.SDK_INT)) }.getOrDefault(false)

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
    /** Oboe uses AAudio from API 27; AAudio device-ID routing is available there. */
    fun useOboe(apiLevel: Int, preferredInputId: Int?): Boolean =
        apiLevel >= 27

    /** A preferred input is accepted only when Android reports that it is actually routed. */
    fun matchesPreferredInput(preferredInputId: Int?, routedInputId: Int?): Boolean =
        preferredInputId == null || preferredInputId == routedInputId

    /** AAudio input-preset selection is only applied on API 28+ by this integration. */
    fun useUnprocessedInput(apiLevel: Int, requested: Boolean): Boolean =
        requested && apiLevel >= 28

    /** Explicit native capture privacy is supported by AAudio from Android 11 / API 30. */
    fun usePrivacySensitiveCapture(apiLevel: Int): Boolean = apiLevel >= 30
}

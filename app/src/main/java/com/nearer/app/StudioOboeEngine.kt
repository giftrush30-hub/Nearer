package com.nearer.app

internal object StudioOboeEngine {
    private val available: Boolean = runCatching {
        System.loadLibrary("nearer_audio")
    }.isSuccess

    external fun nativeStart(inputDeviceId: Int, outputDeviceId: Int, preferUnprocessedInput: Boolean, privacySensitiveCapture: Boolean): Boolean
    external fun nativeStop()
    external fun nativeReadAnalysis(destination: FloatArray): Int
    external fun nativeGetStats(): LongArray
    external fun nativeIsRunning(): Boolean

    fun start(inputDeviceId: Int?, outputDeviceId: Int, preferUnprocessedInput: Boolean): Boolean =
        available && runCatching { nativeStart(inputDeviceId ?: -1, outputDeviceId, preferUnprocessedInput, StudioAudioEnginePolicy.usePrivacySensitiveCapture(android.os.Build.VERSION.SDK_INT)) }.getOrDefault(false)

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

    /** Live microphone playback is allowed only on a selected external headphone output. */
    fun isHeadphoneOutputType(type: Int): Boolean = type in setOf(
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
    )

    /** Generic USB/A2DP outputs may be speakers, so require explicit user confirmation. */
    fun requiresOutputConfirmation(type: Int): Boolean = type in setOf(
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    )

    /** Keep the user's chosen output when other devices appear or disappear. */
    fun selectAvailableOutputId(preferredOutputId: Int?, availableOutputIds: List<Int>): Int? =
        preferredOutputId?.takeIf { it in availableOutputIds } ?: availableOutputIds.firstOrNull()

    /** The active output must match the exact external headphone route selected by Android. */
    fun matchesPreferredOutput(preferredOutputId: Int?, routedOutputId: Int?): Boolean =
        preferredOutputId != null && preferredOutputId == routedOutputId

    /** AAudio input-preset selection is only applied on API 28+ by this integration. */
    fun useUnprocessedInput(apiLevel: Int, requested: Boolean): Boolean =
        requested && apiLevel >= 28

    /** Explicit native capture privacy is supported by AAudio from Android 11 / API 30. */
    fun usePrivacySensitiveCapture(apiLevel: Int): Boolean = apiLevel >= 30
}

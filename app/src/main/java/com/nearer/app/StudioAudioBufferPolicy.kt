package com.nearer.app

internal object StudioAudioBufferPolicy {
    fun chooseSampleRate(outputNativeRate: Int?, supportedInputRates: List<Int>): Int {
        // Keep playback on the sink's native rate for the low-latency output path.
        if (outputNativeRate != null && outputNativeRate in 8_000..192_000) return outputNativeRate
        return listOf(48_000, 44_100, 22_050).firstOrNull { it in supportedInputRates }
            ?: supportedInputRates.filter { it in 8_000..192_000 }.minByOrNull { kotlin.math.abs(it - 48_000) }
            ?: 48_000
    }

    /** Conservative minimum: Android's estimate, three output bursts, or the processing block. */
    fun bufferFrames(minBufferBytes: Int, framesPerBurst: Int, processingBlockFrames: Int): Int {
        val minimumFrames = ((minBufferBytes.toLong().coerceAtLeast(0L) + 1L) / 2L).coerceAtMost(32_768L).toInt()
        return maxOf(minimumFrames, framesPerBurst.coerceAtLeast(1) * 3, processingBlockFrames.coerceAtLeast(1))
            .coerceAtMost(32_768)
    }
}

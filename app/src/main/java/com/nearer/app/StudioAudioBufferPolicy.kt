package com.nearer.app

internal object StudioAudioBufferPolicy {
    fun chooseSampleRate(outputNativeRate: Int?, supportedInputRates: List<Int>): Int {
        val output = outputNativeRate?.takeIf { it in 8_000..192_000 } ?: 48_000
        if (supportedInputRates.isEmpty()) return output
        return if (output in supportedInputRates) output
        else listOf(48_000, 44_100, 22_050).firstOrNull { it in supportedInputRates }
            ?: supportedInputRates.filter { it in 8_000..192_000 }.minByOrNull { kotlin.math.abs(it - output) }
            ?: output
    }

    /** Conservative minimum: Android's estimate, three output bursts, or the processing block. */
    fun bufferFrames(minBufferBytes: Int, framesPerBurst: Int, processingBlockFrames: Int): Int {
        val minimumFrames = ((minBufferBytes.toLong().coerceAtLeast(0L) + 1L) / 2L).coerceAtMost(32_768L).toInt()
        return maxOf(minimumFrames, framesPerBurst.coerceAtLeast(1) * 3, processingBlockFrames.coerceAtLeast(1))
            .coerceAtMost(32_768)
    }
}

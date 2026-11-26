package com.nearer.app

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Converts timestamped decoded PCM frames into a continuous target-rate stream.
 *
 * MediaCodec output buffers retain the input presentation timestamp. This aligner anchors the
 * decoded samples to that media clock, pads real timestamp gaps with silence, ignores overlapping
 * duplicate frames, and resamples by interpolation without accumulating per-buffer rounding.
 */
internal class StudioPcmTimestampAligner(
    private val sourceSampleRate: Int,
    private val targetSampleRate: Int
) {
    private var previousSourceFramePosition: Double? = null
    private var previousLeft = 0f
    private var previousRight = 0f
    private var nextOutputSourceFramePosition = 0.0

    var outputFrameCount: Long = 0L
        private set

    private val sourceFramesPerOutputFrame: Double

    init {
        require(sourceSampleRate in 8_000..384_000) { "Unsupported decoded PCM sample rate." }
        require(targetSampleRate in 8_000..192_000) { "Unsupported target PCM sample rate." }
        sourceFramesPerOutputFrame = sourceSampleRate.toDouble() / targetSampleRate
    }

    fun accept(
        sourceFramePosition: Double,
        left: Float,
        right: Float,
        emit: (Float, Float) -> Unit
    ) {
        require(sourceFramePosition.isFinite()) { "Decoded PCM timestamp is not finite." }
        val safeLeft = if (left.isFinite()) left.coerceIn(-1f, 1f) else 0f
        val safeRight = if (right.isFinite()) right.coerceIn(-1f, 1f) else 0f
        val previousPosition = previousSourceFramePosition

        if (previousPosition == null) {
            beginAt(sourceFramePosition, safeLeft, safeRight, emit)
            return
        }

        if (sourceFramePosition <= previousPosition + 1e-6) {
            // Overlapping codec output buffers can repeat their boundary sample. Do not
            // duplicate it in the PCM cache or shift every later source trim.
            return
        }

        if (sourceFramePosition - previousPosition > 1.5) {
            // A real PTS discontinuity is silence, not a reason to stretch the previous sample.
            beginAt(sourceFramePosition, safeLeft, safeRight, emit)
            return
        }

        while (nextOutputSourceFramePosition <= sourceFramePosition + 1e-9) {
            if (nextOutputSourceFramePosition >= 0.0) {
                val fraction = ((nextOutputSourceFramePosition - previousPosition) /
                    (sourceFramePosition - previousPosition)).coerceIn(0.0, 1.0).toFloat()
                emitFrame(
                    previousLeft + (safeLeft - previousLeft) * fraction,
                    previousRight + (safeRight - previousRight) * fraction,
                    emit
                )
            }
            nextOutputSourceFramePosition += sourceFramesPerOutputFrame
        }

        previousSourceFramePosition = sourceFramePosition
        previousLeft = safeLeft
        previousRight = safeRight
    }

    private fun beginAt(
        sourceFramePosition: Double,
        left: Float,
        right: Float,
        emit: (Float, Float) -> Unit
    ) {
        if (sourceFramePosition >= 0.0) {
            val targetStartFrame = (sourceFramePosition * targetSampleRate / sourceSampleRate)
                .roundToLong().coerceAtLeast(0L)
            require(targetStartFrame <= targetSampleRate.toLong() * 60L * 60L * 24L) {
                "Decoded audio timestamp exceeds the 24-hour safety limit."
            }
            while (outputFrameCount < targetStartFrame) emitFrame(0f, 0f, emit)
            emitFrame(left, right, emit)
        }
        previousSourceFramePosition = sourceFramePosition
        previousLeft = left
        previousRight = right
        nextOutputSourceFramePosition = sourceFramePosition + sourceFramesPerOutputFrame
    }

    private fun emitFrame(left: Float, right: Float, emit: (Float, Float) -> Unit) {
        require(outputFrameCount < targetSampleRate.toLong() * 60L * 60L * 24L) {
            "Decoded audio exceeds the 24-hour safety limit."
        }
        emit(left, right)
        outputFrameCount++
    }
}

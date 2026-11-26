package com.nearer.app

/**
 * Random-access, local PCM source used by the bounded offline mix pipeline.
 * Samples are interleaved stereo Float PCM in the normalized -1..1 range.
 * Return the number of frames actually read; the destination is pre-cleared.
 */
internal fun interface StudioPcmStereoSource {
    fun readStereoFrames(sourceStartFrame: Long, frameCount: Int, destination: FloatArray, destinationOffset: Int): Int
}

internal data class StudioPcmMixBlockResult(
    val framesRequested: Int,
    val activeCueCount: Int,
    val peakBeforeLimiter: Float,
    val peakAfterLimiter: Float
)

/**
 * Bounded-memory deterministic stereo PCM mixer. Mixes one block at a time, seeks local
 * sources to trimmed frames, applies gain/fades/pan, sums overlaps and soft-limits output.
 * The caller owns decoding, URI permissions and final encoding/muxing.
 */
internal object StudioPcmBlockMixer {
    const val CHANNELS = 2
    const val MAX_BLOCK_FRAMES = 4_096

    fun mixBlock(
        cues: List<StudioAudioMixCue>,
        sources: Map<String, StudioPcmStereoSource>,
        timelineStartFrame: Long,
        frameCount: Int,
        sampleRate: Int,
        output: FloatArray,
        outputOffset: Int = 0,
        limiterCeiling: Float = 0.98f
    ): StudioPcmMixBlockResult {
        val validation = StudioAudioMixPlanner.validate(cues, sampleRate)
        require(validation.isValid) { validation.errors.joinToString() }
        require(timelineStartFrame >= 0L) { "Timeline frame cannot be negative." }
        require(frameCount in 1..MAX_BLOCK_FRAMES) { "PCM block must contain 1 to $MAX_BLOCK_FRAMES frames." }
        require(outputOffset >= 0 && outputOffset <= output.size - frameCount * CHANNELS) {
            "Output buffer is too small for the requested PCM block."
        }
        require(limiterCeiling.isFinite() && limiterCeiling in 0.1f..1f)

        val outputEndFrame = timelineStartFrame + frameCount.toLong()
        require(outputEndFrame >= timelineStartFrame) { "PCM block frame range overflowed." }
        val sampleOffset = outputOffset
        output.fill(0f, sampleOffset, sampleOffset + frameCount * CHANNELS)

        // Reuse one scratch block sequentially so memory does not grow with track count.
        val scratch = FloatArray(frameCount * CHANNELS)
        var activeCueCount = 0
        var peakBefore = 0f

        cues.forEach { cue ->
            if (cue.muted) return@forEach
            val cueStartFrame = studioAudioMillisecondsToFrames(cue.timelineStartMs, sampleRate)
            val cueEndFrame = studioAudioMillisecondsToFrames(cue.timelineEndMs, sampleRate)
            val overlapStart = maxOf(timelineStartFrame, cueStartFrame)
            val overlapEnd = minOf(outputEndFrame, cueEndFrame)
            if (overlapStart >= overlapEnd) return@forEach
            val source = sources[cue.id] ?: error("Missing local PCM source for active cue " + cue.id + ".")

            val blockOffsetFrames = (overlapStart - timelineStartFrame).toInt()
            val cueLocalStart = overlapStart - cueStartFrame
            val overlapFrames = (overlapEnd - overlapStart).toInt()
            val sourceStartFrame = studioAudioMillisecondsToFrames(cue.sourceInMs, sampleRate) + cueLocalStart
            scratch.fill(0f)
            val framesRead = source.readStereoFrames(sourceStartFrame, overlapFrames, scratch, 0)
            require(framesRead in 0..overlapFrames) { "PCM source returned an invalid frame count." }
            if (framesRead == 0) return@forEach
            activeCueCount++

            val totalCueFrames = cueEndFrame - cueStartFrame
            val fadeInFrames = studioAudioMillisecondsToFrames(cue.fadeInMs, sampleRate)
            val fadeOutFrames = studioAudioMillisecondsToFrames(cue.fadeOutMs, sampleRate)
            val (panLeft, panRight) = studioAudioPanFactors(cue.pan)

            for (frame in 0 until framesRead) {
                val cueLocalFrame = cueLocalStart + frame
                val gain = studioAudioGainAtFrame(cueLocalFrame, totalCueFrames, cue.gain, fadeInFrames, fadeOutFrames)
                val destinationIndex = sampleOffset + (blockOffsetFrames + frame) * CHANNELS
                val sourceIndex = frame * CHANNELS
                output[destinationIndex] += scratch[sourceIndex] * gain * panLeft
                output[destinationIndex + 1] += scratch[sourceIndex + 1] * gain * panRight
                peakBefore = maxOf(
                    peakBefore,
                    kotlin.math.abs(output[destinationIndex]),
                    kotlin.math.abs(output[destinationIndex + 1])
                )
            }
        }

        var peakAfter = 0f
        val outputLimit = sampleOffset + frameCount * CHANNELS
        for (index in sampleOffset until outputLimit) {
            val limited = StudioAudioMixPlanner.limitMixedSample(output[index], limiterCeiling)
            output[index] = limited
            peakAfter = maxOf(peakAfter, kotlin.math.abs(limited))
        }
        return StudioPcmMixBlockResult(frameCount, activeCueCount, peakBefore, peakAfter)
    }
}

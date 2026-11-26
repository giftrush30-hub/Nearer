package com.nearer.app

internal data class StudioAudioMixCue(
    val id: String,
    val sourceUri: String,
    val timelineStartMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val gain: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val pan: Float = 0f,
    val muted: Boolean = false
) {
    val durationMs: Long get() = sourceOutMs - sourceInMs
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

internal data class StudioAudioMixSample(
    val cueId: String,
    val sourceFrame: Long,
    val gain: Float,
    val leftFactor: Float,
    val rightFactor: Float
)

internal data class StudioAudioMixValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Deterministic per-track PCM mix schedule for an offline renderer. */
internal object StudioAudioMixPlanner {
    const val MAX_TRACKS = 128
    const val MIN_SAMPLE_RATE = 8_000
    const val MAX_SAMPLE_RATE = 192_000

    fun validate(cues: List<StudioAudioMixCue>, sampleRate: Int): StudioAudioMixValidation {
        val errors = mutableListOf<String>()
        if (sampleRate !in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) errors += "Audio sample rate must be between 8 kHz and 192 kHz."
        if (cues.size > MAX_TRACKS) errors += "Audio mix exceeds the 128-track limit."
        if (cues.map { it.id }.toSet().size != cues.size || cues.any { it.id.isBlank() }) errors += "Audio cue IDs must be non-empty and unique."
        cues.forEach { cue ->
            val scheme = runCatching { java.net.URI(cue.sourceUri).scheme?.lowercase() }.getOrNull()
            if (scheme !in setOf("content", "file", "android.resource")) errors += cue.id + ": audio source must be local and offline."
            if (cue.timelineStartMs < 0L || cue.sourceInMs < 0L || cue.sourceOutMs <= cue.sourceInMs || cue.sourceOutMs > StudioRenderPlanBuilder.MAX_DURATION_MS ||
                cue.timelineStartMs > StudioRenderPlanBuilder.MAX_DURATION_MS ||
                cue.durationMs > StudioRenderPlanBuilder.MAX_DURATION_MS - cue.timelineStartMs) errors += cue.id + ": audio time range is invalid."
            if (!cue.gain.isFinite() || cue.gain !in 0f..1f) errors += cue.id + ": gain must be between 0 and 1."
            if (cue.fadeInMs < 0L || cue.fadeOutMs < 0L || cue.fadeInMs > cue.durationMs || cue.fadeOutMs > cue.durationMs) {
                errors += cue.id + ": fade durations must fit inside the clip."
            }
            if (!cue.pan.isFinite() || cue.pan !in -1f..1f) errors += cue.id + ": pan must be between -1 and 1."
        }
        return StudioAudioMixValidation(errors.distinct())
    }

    fun samplesAt(cues: List<StudioAudioMixCue>, timelineMs: Long, sampleRate: Int): List<StudioAudioMixSample> {
        require(validate(cues, sampleRate).isValid) { validate(cues, sampleRate).errors.joinToString() }
        require(timelineMs >= 0L)
        return cues.asSequence().filter { !it.muted && timelineMs >= it.timelineStartMs && timelineMs < it.timelineEndMs }
            .map { cue ->
                val localMs = timelineMs - cue.timelineStartMs
                val totalFrames = studioAudioMillisecondsToFrames(cue.durationMs, sampleRate)
                val localFrame = studioAudioMillisecondsToFrames(localMs, sampleRate)
                val fadeInFrames = studioAudioMillisecondsToFrames(cue.fadeInMs, sampleRate)
                val fadeOutFrames = studioAudioMillisecondsToFrames(cue.fadeOutMs, sampleRate)
                val gain = studioAudioGainAtFrame(localFrame, totalFrames, cue.gain, fadeInFrames, fadeOutFrames)
                val (left, right) = studioAudioPanFactors(cue.pan)
                StudioAudioMixSample(
                    cue.id,
                    studioAudioMillisecondsToFrames(cue.sourceInMs, sampleRate) + localFrame,
                    gain,
                    left,
                    right
                )
            }.toList()
    }

    /** Soft-knee limiter for summing overlapping tracks; avoids hard clipping at 0 dBFS. */
    fun limitMixedSample(sample: Float, ceiling: Float = 0.98f): Float {
        require(sample.isFinite() && ceiling.isFinite() && ceiling in 0.1f..1f)
        val magnitude = kotlin.math.abs(sample)
        if (magnitude <= ceiling) return sample
        val excess = magnitude - ceiling
        val compressed = ceiling + (1f - ceiling) * (excess / (excess + (1f - ceiling)))
        return kotlin.math.sign(sample) * compressed.coerceAtMost(1f)
    }
}

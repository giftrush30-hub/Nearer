package com.nearer.app

/**
 * Audio-bearing video segment presented to the movie export planner.
 * The source duration is supplied by the local media probe; source audio may be absent.
 */
internal data class StudioMovieAudioClipInput(
    val id: String,
    val sourceUri: String,
    val timelineStartMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val sourceDurationMs: Long,
    val hasAudioTrack: Boolean,
    val gain: Float = 1f,
    val muted: Boolean = false
) {
    val durationMs: Long get() = sourceOutMs - sourceInMs
    val timelineEndMs: Long get() = runCatching { Math.addExact(timelineStartMs, durationMs) }.getOrDefault(Long.MAX_VALUE)
}

internal data class StudioMovieMusicInput(
    val sourceUri: String,
    val sourceDurationMs: Long,
    val gain: Float = 0.7f,
    val muted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

internal data class StudioMovieAudioCuePlan(
    val cues: List<StudioAudioMixCue>,
    val durationMs: Long,
    val warnings: List<String>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Converts the movie's ordered video clips and optional looping music bed into the
 * source-relative cues consumed by the bounded offline PCM mixer.
 *
 * Video source audio keeps the same source trim and timeline placement as its picture.
 * Music is split into bounded source ranges and repeated until the visual timeline ends.
 * This planner does not decode media or assume that every video file contains audio.
 */
internal object StudioMovieAudioCuePlanner {
    const val MAX_CUES = 512

    fun build(
        clips: List<StudioMovieAudioClipInput>,
        music: StudioMovieMusicInput?,
        musicMuted: Boolean = false
    ): StudioMovieAudioCuePlan {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val cues = mutableListOf<StudioAudioMixCue>()
        val ordered = clips.sortedWith(compareBy<StudioMovieAudioClipInput> { it.timelineStartMs }.thenBy { it.id })

        if (ordered.isEmpty()) errors += "Add at least one video clip before planning movie audio."
        if (ordered.size > MAX_CUES) errors += "The movie contains too many video clips for safe audio planning."

        val ids = mutableSetOf<String>()
        ordered.forEach { clip ->
            if (clip.id.isBlank() || !ids.add(clip.id)) {
                errors += "Video audio clip IDs must be non-empty and unique."
            }
            if (!isOfflineUri(clip.sourceUri)) errors += "${clip.id}: video audio source must be local."
            if (clip.timelineStartMs < 0L || clip.sourceInMs < 0L ||
                clip.sourceOutMs <= clip.sourceInMs || clip.sourceDurationMs <= 0L ||
                clip.sourceOutMs > clip.sourceDurationMs ||
                clip.timelineEndMs > StudioRenderPlanBuilder.MAX_DURATION_MS) {
                errors += "${clip.id}: video audio trim or timeline range is invalid."
            }
            if (!clip.gain.isFinite() || clip.gain !in 0f..1f) {
                errors += "${clip.id}: source audio gain must be between 0 and 1."
            }
            if (!clip.hasAudioTrack) {
                warnings += "${clip.id}: source video has no audio track; its audio layer is skipped."
            } else if (!clip.muted && clip.id.isNotBlank() && clip.sourceOutMs > clip.sourceInMs &&
                clip.timelineStartMs >= 0L && clip.sourceOutMs <= clip.sourceDurationMs &&
                clip.timelineEndMs <= StudioRenderPlanBuilder.MAX_DURATION_MS
            ) {
                cues += StudioAudioMixCue(
                    id = "video-audio:${clip.id}",
                    sourceUri = clip.sourceUri,
                    timelineStartMs = clip.timelineStartMs,
                    sourceInMs = clip.sourceInMs,
                    sourceOutMs = clip.sourceOutMs,
                    gain = clip.gain
                )
            }
        }

        val movieDurationMs = ordered.maxOfOrNull { it.timelineEndMs.coerceAtLeast(0L) } ?: 0L
        if (music != null && !music.muted && !musicMuted && movieDurationMs > 0L) {
            if (!isOfflineUri(music.sourceUri)) errors += "Background music must use a local offline source."
            if (music.sourceDurationMs <= 0L) errors += "Background music duration could not be determined."
            if (!music.gain.isFinite() || music.gain !in 0f..1f) errors += "Background music gain must be between 0 and 1."
            if (music.fadeInMs < 0L || music.fadeOutMs < 0L ||
                music.fadeInMs > movieDurationMs || music.fadeOutMs > movieDurationMs) {
                errors += "Background music fades must fit inside the movie."
            }
            if (music.sourceDurationMs > 0L && music.gain.isFinite() && music.gain in 0f..1f &&
                isOfflineUri(music.sourceUri) && music.fadeInMs >= 0L && music.fadeOutMs >= 0L
            ) {
                var timelineStart = 0L
                var segment = 0
                while (timelineStart < movieDurationMs && segment < MAX_CUES) {
                    val segmentDuration = minOf(music.sourceDurationMs, movieDurationMs - timelineStart)
                    val fadeIn = if (timelineStart == 0L) minOf(music.fadeInMs, segmentDuration) else 0L
                    val fadeOut = if (timelineStart + segmentDuration == movieDurationMs) {
                        minOf(music.fadeOutMs, segmentDuration)
                    } else 0L
                    cues += StudioAudioMixCue(
                        id = "music-bed:$segment",
                        sourceUri = music.sourceUri,
                        timelineStartMs = timelineStart,
                        sourceInMs = 0L,
                        sourceOutMs = segmentDuration,
                        gain = music.gain,
                        fadeInMs = fadeIn,
                        fadeOutMs = fadeOut
                    )
                    timelineStart += segmentDuration
                    segment++
                }
                if (timelineStart < movieDurationMs) {
                    errors += "Background music would require more than $MAX_CUES loop segments."
                }
            }
        }

        if (cues.size > MAX_CUES) errors += "The combined movie audio exceeds the $MAX_CUES cue safety limit."
        val cueValidation = StudioAudioMixPlanner.validate(cues, 48_000)
        errors += cueValidation.errors

        return StudioMovieAudioCuePlan(
            cues = cues,
            durationMs = movieDurationMs.coerceIn(0L, StudioRenderPlanBuilder.MAX_DURATION_MS),
            warnings = warnings.distinct(),
            errors = errors.distinct()
        )
    }

    private fun isOfflineUri(value: String): Boolean {
        val scheme = runCatching { java.net.URI(value).scheme?.lowercase() }.getOrNull()
        return scheme in setOf("content", "file", "android.resource")
    }
}

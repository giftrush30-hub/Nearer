package com.nearer.app

/** Time-normalized adapter between the editable timeline and Media3 composition builders. */
internal data class StudioCompositionClipTiming(
    val trackId: String,
    val itemId: String,
    val sourceUri: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val opacity: Float,
    val volume: Float
) {
    val durationUs: Long get() = timelineEndUs - timelineStartUs
}

internal data class StudioCompositionSequenceTiming(
    val trackId: String,
    val trackName: String,
    val type: StudioRenderTrackType,
    val clips: List<StudioCompositionClipTiming>
)

internal data class StudioCompositionTimelinePlan(
    val durationUs: Long,
    val videoSequences: List<StudioCompositionSequenceTiming>,
    val audioSequences: List<StudioCompositionSequenceTiming>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
    val sequenceCount: Int get() = videoSequences.size + audioSequences.size
    val clipCount: Int get() = (videoSequences + audioSequences).sumOf { it.clips.size }
}

/**
 * Converts validated millisecond render decisions into a stable microsecond schedule.
 *
 * It deliberately keeps each track as its own sequence: clips on different tracks may overlap,
 * while clips within one sequence retain their timeline positions and source trim ranges.
 * This is a planning adapter only; it does not decode or render media.
 */
internal object StudioCompositionTimelinePlanBuilder {
    private const val MICROS_PER_MILLI = 1_000L

    fun build(renderPlan: StudioRenderPlan): StudioCompositionTimelinePlan {
        val errors = renderPlan.errors.toMutableList()
        fun toUs(valueMs: Long, label: String): Long? {
            if (valueMs < 0L || valueMs > Long.MAX_VALUE / MICROS_PER_MILLI) {
                errors += "$label is outside the supported microsecond range."
                return null
            }
            return valueMs * MICROS_PER_MILLI
        }

        fun convert(track: StudioRenderTrack): StudioCompositionSequenceTiming? {
            val clips = mutableListOf<StudioCompositionClipTiming>()
            track.clips.forEach { clip ->
                val start = toUs(clip.timelineStartMs, "${track.name}/${clip.itemId} timeline start")
                val end = toUs(clip.timelineEndMs, "${track.name}/${clip.itemId} timeline end")
                val sourceIn = toUs(clip.sourceInMs, "${track.name}/${clip.itemId} source in")
                val sourceOut = toUs(clip.sourceOutMs, "${track.name}/${clip.itemId} source out")
                if (start == null || end == null || sourceIn == null || sourceOut == null) return@forEach
                if (end <= start || sourceOut <= sourceIn || end - start != sourceOut - sourceIn) {
                    errors += "${track.name}/${clip.itemId} has inconsistent timeline and source durations."
                    return@forEach
                }
                clips += StudioCompositionClipTiming(
                    trackId = clip.trackId,
                    itemId = clip.itemId,
                    sourceUri = clip.sourceUri,
                    timelineStartUs = start,
                    timelineEndUs = end,
                    sourceInUs = sourceIn,
                    sourceOutUs = sourceOut,
                    opacity = clip.opacity,
                    volume = clip.volume
                )
            }
            return if (clips.isEmpty()) null else StudioCompositionSequenceTiming(
                track.id, track.name, track.type, clips.sortedWith(
                    compareBy<StudioCompositionClipTiming> { it.timelineStartUs }.thenBy { it.itemId }
                )
            )
        }

        val video = renderPlan.videoTracks.mapNotNull(::convert)
        val audio = renderPlan.audioTracks.mapNotNull(::convert)
        val durationUs = toUs(renderPlan.durationMs, "Project duration") ?: 0L
        val actualEndUs = (video + audio).flatMap { it.clips }.maxOfOrNull { it.timelineEndUs } ?: 0L
        if (actualEndUs > durationUs) errors += "Composition duration ends before its final clip."
        return StudioCompositionTimelinePlan(
            durationUs = maxOf(durationUs, actualEndUs),
            videoSequences = video,
            audioSequences = audio,
            errors = errors.distinct()
        )
    }
}

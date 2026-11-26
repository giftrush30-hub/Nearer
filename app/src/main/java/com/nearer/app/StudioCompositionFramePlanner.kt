package com.nearer.app

/** Animation binding for a clip/layer. Keyframe timestamps are clip-local milliseconds. */
internal data class StudioLayerAnimationBinding(
    val layerId: String,
    val animations: List<StudioPropertyAnimation>
)

internal data class StudioFrameLayerState(
    val trackId: String,
    val itemId: String,
    val sourceUri: String,
    val sourceTimeUs: Long,
    val localTimeUs: Long,
    val opacity: Float,
    val volume: Float,
    val animatedValues: Map<StudioAnimatedProperty, Float>
)

internal data class StudioCompositionFrameState(
    val timestampUs: Long,
    val videoLayers: List<StudioFrameLayerState>,
    val audioLayers: List<StudioFrameLayerState>
)

internal enum class StudioTransitionKind { CUT, FADE_TO_BLACK }

internal data class StudioTransitionWindow(
    val trackId: String,
    val outgoingItemId: String,
    val incomingItemId: String,
    val kind: StudioTransitionKind,
    val outgoingStartUs: Long,
    val outgoingEndUs: Long,
    val incomingStartUs: Long,
    val incomingEndUs: Long
)

internal data class StudioCompositionFramePlan(
    val timeline: StudioCompositionTimelinePlan,
    val transitions: List<StudioTransitionWindow>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun frameAt(timestampUs: Long, animations: List<StudioLayerAnimationBinding> = emptyList()): StudioCompositionFrameState {
        require(timestampUs >= 0L) { "Frame timestamp cannot be negative." }
        val animationByLayer = animations.associateBy { it.layerId }
        fun state(clip: StudioCompositionClipTiming, isAudio: Boolean): StudioFrameLayerState {
            val localTimeUs = (timestampUs - clip.timelineStartUs).coerceAtLeast(0L)
            val localTimeMs = localTimeUs / 1_000L
            val values = animationByLayer[clip.itemId]?.animations.orEmpty().associate { animation ->
                animation.property to animation.valueAt(localTimeMs, Float.NaN)
            }.filterValues { it.isFinite() }
            return StudioFrameLayerState(
                trackId = clip.trackId,
                itemId = clip.itemId,
                sourceUri = clip.sourceUri,
                sourceTimeUs = clip.sourceInUs + localTimeUs,
                localTimeUs = localTimeUs,
                opacity = clip.opacity,
                volume = if (isAudio) clip.volume else 1f,
                animatedValues = values
            )
        }
        val video = timeline.videoSequences.flatMap { sequence ->
            sequence.clips.filter { timestampUs >= it.timelineStartUs && timestampUs < it.timelineEndUs }
                .map { state(it, false) }
        }
        val audio = timeline.audioSequences.flatMap { sequence ->
            sequence.clips.filter { timestampUs >= it.timelineStartUs && timestampUs < it.timelineEndUs }
                .map { state(it, true) }
        }
        return StudioCompositionFrameState(timestampUs, video, audio)
    }
}

/**
 * Creates a deterministic frame schedule shared by preview and export.
 * It describes which source frames and layer parameters are active at each output timestamp;
 * a renderer consumes this plan to composite pixels and mix PCM.
 */
internal object StudioCompositionFramePlanner {
    fun build(
        timeline: StudioCompositionTimelinePlan,
        transitionKind: StudioTransitionKind = StudioTransitionKind.CUT,
        transitionDurationMs: Int = 400
    ): StudioCompositionFramePlan {
        val errors = timeline.errors.toMutableList()
        val durationUs = transitionDurationMs.coerceIn(0, 2_000).toLong() * 1_000L
        val transitions = mutableListOf<StudioTransitionWindow>()
        timeline.videoSequences.forEach { sequence ->
            sequence.clips.zipWithNext().forEach { (outgoing, incoming) ->
                if (incoming.timelineStartUs < outgoing.timelineEndUs) {
                    // Overlaps are real layered content, not an implicit transition.
                    return@forEach
                }
                if (incoming.timelineStartUs > outgoing.timelineEndUs || durationUs == 0L ||
                    transitionKind == StudioTransitionKind.CUT) return@forEach
                val safeDuration = minOf(
                    durationUs,
                    outgoing.durationUs / 2L,
                    incoming.durationUs / 2L
                )
                if (safeDuration <= 0L) return@forEach
                transitions += StudioTransitionWindow(
                    trackId = sequence.trackId,
                    outgoingItemId = outgoing.itemId,
                    incomingItemId = incoming.itemId,
                    kind = transitionKind,
                    outgoingStartUs = outgoing.timelineEndUs - safeDuration,
                    outgoingEndUs = outgoing.timelineEndUs,
                    incomingStartUs = incoming.timelineStartUs,
                    incomingEndUs = incoming.timelineStartUs + safeDuration
                )
            }
        }
        return StudioCompositionFramePlan(timeline, transitions, errors.distinct())
    }
}

package com.nearer.app

/** One actor animation scheduled on the movie timeline, using milliseconds for editor-facing values. */
internal data class Studio3DAnimationExportTrack(
    val actorId: String,
    val animationName: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val animationDurationMs: Long,
    val playbackRate: Float = 1f,
    val loop: Boolean = true
)

internal data class Studio3DAnimationExportSample(
    val actorId: String,
    val animationName: String,
    /** Animation-local time in microseconds, ready for Filament's seconds-based animator API. */
    val animationTimeUs: Long
)

internal data class Studio3DAnimationExportFrame(
    val frameIndex: Int,
    val presentationTimeUs: Long,
    val activeAnimations: List<Studio3DAnimationExportSample>
)

internal data class Studio3DAnimationExportCompilation(
    val plan: Studio3DAnimationExportPlan?,
    val errors: List<String>
) {
    val isValid: Boolean get() = plan != null && errors.isEmpty()
}

/**
 * Deterministic, allocation-bounded frame schedule for rendering animated GLB actors into a movie.
 * This is the timeline contract for the future Filament off-screen renderer. It deliberately does
 * not create a Filament Engine, GL context, Surface, or encoded video on the caller's thread.
 */
internal class Studio3DAnimationExportPlan private constructor(
    val durationMs: Long,
    val frameRate: Int,
    private val tracks: List<Studio3DAnimationExportTrack>
) {
    val frameCount: Int = ((durationMs * frameRate + 999L) / 1_000L).toInt()

    /** Returns one exact, presentation-time-based frame without allocating a full movie schedule. */
    fun frameAt(frameIndex: Int): Studio3DAnimationExportFrame {
        require(frameIndex in 0 until frameCount) { "Frame index is outside the export range." }
        val presentationTimeUs = frameIndex.toLong() * 1_000_000L / frameRate
        val active = tracks.asSequence()
            .filter { presentationTimeUs >= it.timelineStartMs * 1_000L &&
                presentationTimeUs < it.timelineEndMs * 1_000L }
            .map { track ->
                val elapsedUs = presentationTimeUs - track.timelineStartMs * 1_000L
                val rawAnimationTimeUs = (elapsedUs.toDouble() * track.playbackRate.toDouble()).toLong()
                val animationDurationUs = track.animationDurationMs * 1_000L
                val animationTimeUs = if (track.loop) {
                    rawAnimationTimeUs % animationDurationUs
                } else {
                    rawAnimationTimeUs.coerceAtMost(animationDurationUs)
                }
                Studio3DAnimationExportSample(track.actorId, track.animationName, animationTimeUs)
            }
            .toList()
        return Studio3DAnimationExportFrame(frameIndex, presentationTimeUs, active)
    }

    companion object {
        const val MAX_DURATION_MS = 30L * 60L * 1_000L
        const val MAX_TRACKS = 100
        const val MAX_FRAME_RATE = 60
        const val MIN_FRAME_RATE = 24

        fun compile(
            durationMs: Long,
            frameRate: Int,
            tracks: List<Studio3DAnimationExportTrack>
        ): Studio3DAnimationExportCompilation {
            val errors = mutableListOf<String>()
            if (durationMs !in 1L..MAX_DURATION_MS) {
                errors += "3D movie duration must be within 1 ms and 30 minutes."
            }
            if (frameRate !in MIN_FRAME_RATE..MAX_FRAME_RATE) {
                errors += "3D movie frame rate must be between 24 and 60 fps."
            }
            if (tracks.size > MAX_TRACKS) errors += "A 3D scene cannot schedule more than 100 animation tracks."

            tracks.forEachIndexed { index, track ->
                val label = "Animation track " + index
                if (track.actorId.isBlank() || track.actorId.length > 128) {
                    errors += label + ": actor ID must contain 1–128 characters."
                }
                if (track.animationName.isBlank() || track.animationName.length > 80) {
                    errors += label + ": animation name must contain 1–80 characters."
                }
                if (track.timelineStartMs < 0L || track.timelineEndMs <= track.timelineStartMs ||
                    track.timelineEndMs > durationMs) {
                    errors += label + ": timeline range must be positive and fit inside the movie."
                }
                if (track.animationDurationMs !in 1L..MAX_DURATION_MS) {
                    errors += label + ": source animation duration must be within 1 ms and 30 minutes."
                }
                if (!track.playbackRate.isFinite() || track.playbackRate !in 0.05f..8f) {
                    errors += label + ": playback rate must be within 0.05x..8x."
                }
            }

            tracks.groupBy { it.actorId }.forEach { (actorId, actorTracks) ->
                val ordered = actorTracks.sortedBy { it.timelineStartMs }
                ordered.zipWithNext().forEach { (first, second) ->
                    if (second.timelineStartMs < first.timelineEndMs) {
                        errors += "Actor " + actorId + " has overlapping animation tracks; blend or transition them explicitly first."
                    }
                }
            }

            if (errors.isNotEmpty()) return Studio3DAnimationExportCompilation(null, errors.distinct())
            return Studio3DAnimationExportCompilation(
                Studio3DAnimationExportPlan(durationMs, frameRate, tracks.toList()),
                emptyList()
            )
        }
    }
}

package com.nearer.app

/** Validated offline-only description of the work required to render a Studio project. */
internal enum class StudioRenderTrackType { VIDEO, AUDIO }

internal data class StudioRenderClip(
    val trackId: String, val itemId: String, val sourceUri: String,
    val timelineStartMs: Long, val sourceInMs: Long, val sourceOutMs: Long,
    val opacity: Float, val volume: Float
) {
    val durationMs: Long get() = sourceOutMs - sourceInMs
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

internal data class StudioRenderTrack(
    val id: String, val name: String, val type: StudioRenderTrackType,
    val clips: List<StudioRenderClip>
)

internal data class StudioRenderPlan(
    val durationMs: Long,
    val videoTracks: List<StudioRenderTrack>,
    val audioTracks: List<StudioRenderTrack>,
    val warnings: List<String>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
    val totalClipCount: Int get() = videoTracks.sumOf { it.clips.size } + audioTracks.sumOf { it.clips.size }
}

/**
 * Builds a bounded edit plan before media decoders/encoders are opened.
 * This is a validation/planning layer, not a claim that the plan itself renders video.
 */
internal object StudioRenderPlanBuilder {
    const val MAX_DURATION_MS = 86_400_000L
    const val MAX_TRACKS = 32
    const val MAX_ITEMS = 500

    fun build(project: StudioMultiTrackProject): StudioRenderPlan {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val video = mutableListOf<StudioRenderTrack>()
        val audio = mutableListOf<StudioRenderTrack>()

        if (project.tracks.size > MAX_TRACKS) errors += "Project exceeds the 32-track limit."
        if (project.tracks.sumOf { it.items.size } > MAX_ITEMS) errors += "Project exceeds the 500-item limit."
        if (project.durationMs !in 0L..MAX_DURATION_MS) errors += "Project duration is outside the supported 24-hour range."

        project.tracks.forEach { track ->
            if (track.kind == StudioTrackKind.AUDIO && track.muted) return@forEach
            val renderType = when (track.kind) {
                StudioTrackKind.VIDEO -> StudioRenderTrackType.VIDEO
                StudioTrackKind.AUDIO -> StudioRenderTrackType.AUDIO
                StudioTrackKind.TEXT, StudioTrackKind.EFFECT -> {
                    if (track.items.isNotEmpty()) {
                        warnings += track.name + ": procedural text/effect items need a renderer asset before export."
                    }
                    return@forEach
                }
            }

            val clips = mutableListOf<StudioRenderClip>()
            val seenIds = mutableSetOf<String>()
            track.items.sortedWith(compareBy<StudioTimelineItem> { it.timelineStartMs }.thenBy { it.id })
                .forEach itemLoop@{ item ->
                    if (item.id.isBlank() || !seenIds.add(item.id)) {
                        errors += track.name + ": item IDs must be non-empty and unique within a track."
                        return@itemLoop
                    }
                    val uri = item.sourceUri
                    if (uri.isNullOrBlank()) {
                        errors += track.name + "/" + item.id + ": media source is missing."
                        return@itemLoop
                    }
                    if (!isOfflineUri(uri)) {
                        errors += track.name + "/" + item.id + ": only local content, file or app-resource media can be rendered offline."
                        return@itemLoop
                    }
                    if (item.timelineStartMs !in 0L..MAX_DURATION_MS || item.sourceInMs < 0L ||
                        item.sourceOutMs <= item.sourceInMs || item.sourceOutMs > MAX_DURATION_MS ||
                        item.timelineEndMs > MAX_DURATION_MS
                    ) {
                        errors += track.name + "/" + item.id + ": invalid source range or timeline bounds."
                        return@itemLoop
                    }
                    if (!item.opacity.isFinite() || item.opacity !in 0f..1f) {
                        errors += track.name + "/" + item.id + ": opacity must be between 0 and 1."
                        return@itemLoop
                    }
                    if (!item.volume.isFinite() || item.volume !in 0f..2f) {
                        errors += track.name + "/" + item.id + ": volume must be between 0 and 2."
                        return@itemLoop
                    }
                    if (renderType == StudioRenderTrackType.AUDIO && item.opacity != 1f) {
                        warnings += track.name + "/" + item.id + ": opacity is ignored on audio."
                    }
                    if (renderType == StudioRenderTrackType.VIDEO && item.volume != 1f) {
                        warnings += track.name + "/" + item.id + ": volume is ignored on video."
                    }
                    clips += StudioRenderClip(track.id, item.id, uri, item.timelineStartMs,
                        item.sourceInMs, item.sourceOutMs, item.opacity, item.volume)
                }

            clips.zipWithNext().forEach { (left, right) ->
                if (right.timelineStartMs < left.timelineEndMs) {
                    warnings += track.name + ": overlapping items require layer-aware composition."
                }
            }
            if (clips.isNotEmpty()) {
                val planned = StudioRenderTrack(track.id, track.name, renderType, clips)
                if (renderType == StudioRenderTrackType.VIDEO) video += planned else audio += planned
            }
        }

        val actualEnd = (video + audio).flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 0L
        if (actualEnd > project.durationMs) {
            warnings += "Project duration was shorter than its media; render duration uses the media end."
        }
        return StudioRenderPlan(
            durationMs = maxOf(project.durationMs.coerceIn(0L, MAX_DURATION_MS), actualEnd.coerceAtMost(MAX_DURATION_MS)),
            videoTracks = video, audioTracks = audio,
            warnings = warnings.distinct(), errors = errors.distinct()
        )
    }

    private fun isOfflineUri(value: String): Boolean {
        val scheme = runCatching { java.net.URI(value).scheme?.lowercase() }.getOrNull()
        return scheme == "content" || scheme == "file" || scheme == "android.resource"
    }
}

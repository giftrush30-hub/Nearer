package com.nearer.app

/** Pure helpers for the horizontally navigable Sound Desk timeline viewport. */
internal object StudioAudioTimelineViewportPolicy {
    const val DURATION_MS = StudioAudioBeatGridPolicy.MAX_TIMELINE_OFFSET_MS
    val ZOOM_LEVELS = listOf(1, 2, 4)

    fun normalizeZoom(zoom: Int): Int = ZOOM_LEVELS.firstOrNull { it == zoom } ?: 1

    fun contentWidthPx(viewportWidthPx: Float, zoom: Int): Float =
        viewportWidthPx.coerceAtLeast(0f) * normalizeZoom(zoom)

    fun timelinePositionPx(offsetMs: Long, viewportWidthPx: Float, zoom: Int): Float {
        val boundedOffset = offsetMs.coerceIn(0L, DURATION_MS)
        return contentWidthPx(viewportWidthPx, zoom) * boundedOffset / DURATION_MS
    }

    fun visibleDurationMs(zoom: Int): Long =
        (DURATION_MS / normalizeZoom(zoom)).toLong()
}

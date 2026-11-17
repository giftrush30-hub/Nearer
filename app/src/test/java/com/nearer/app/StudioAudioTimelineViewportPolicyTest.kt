package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioAudioTimelineViewportPolicyTest {
    @Test fun acceptsOnlySupportedZoomLevels() {
        assertEquals(1, StudioAudioTimelineViewportPolicy.normalizeZoom(1))
        assertEquals(2, StudioAudioTimelineViewportPolicy.normalizeZoom(2))
        assertEquals(4, StudioAudioTimelineViewportPolicy.normalizeZoom(4))
        assertEquals(1, StudioAudioTimelineViewportPolicy.normalizeZoom(3))
    }

    @Test fun scalesTimelineContentWithoutChangingItsDuration() {
        assertEquals(1_000f, StudioAudioTimelineViewportPolicy.contentWidthPx(1_000f, 1), 0.001f)
        assertEquals(2_000f, StudioAudioTimelineViewportPolicy.contentWidthPx(1_000f, 2), 0.001f)
        assertEquals(4_000f, StudioAudioTimelineViewportPolicy.contentWidthPx(1_000f, 4), 0.001f)
        assertEquals(30_000L, StudioAudioTimelineViewportPolicy.DURATION_MS)
    }

    @Test fun mapsTrackOffsetsIntoZoomedTimelineCoordinates() {
        assertEquals(500f, StudioAudioTimelineViewportPolicy.timelinePositionPx(15_000L, 1_000f, 1), 0.001f)
        assertEquals(1_000f, StudioAudioTimelineViewportPolicy.timelinePositionPx(15_000L, 1_000f, 2), 0.001f)
        assertEquals(2_000f, StudioAudioTimelineViewportPolicy.timelinePositionPx(15_000L, 1_000f, 4), 0.001f)
        assertEquals(0f, StudioAudioTimelineViewportPolicy.timelinePositionPx(-50L, 1_000f, 2), 0.001f)
        assertEquals(2_000f, StudioAudioTimelineViewportPolicy.timelinePositionPx(40_000L, 1_000f, 2), 0.001f)
    }

    @Test fun reportsVisibleTimeWindowForEachZoom() {
        assertEquals(30_000L, StudioAudioTimelineViewportPolicy.visibleDurationMs(1))
        assertEquals(15_000L, StudioAudioTimelineViewportPolicy.visibleDurationMs(2))
        assertEquals(7_500L, StudioAudioTimelineViewportPolicy.visibleDurationMs(4))
    }
}

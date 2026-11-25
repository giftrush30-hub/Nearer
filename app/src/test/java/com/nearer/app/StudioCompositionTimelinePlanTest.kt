package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioCompositionTimelinePlanTest {
    private fun plan() = StudioRenderPlan(
        durationMs = 6_000L,
        videoTracks = listOf(
            StudioRenderTrack("v-main", "Main video", StudioRenderTrackType.VIDEO, listOf(
                StudioRenderClip("v-main", "a", "content://video/a", 0L, 500L, 2_500L, 1f, 1f),
                StudioRenderClip("v-main", "b", "content://video/b", 3_000L, 1_000L, 4_000L, 0.7f, 1f)
            )),
            StudioRenderTrack("v-overlay", "Overlay", StudioRenderTrackType.VIDEO, listOf(
                StudioRenderClip("v-overlay", "overlay", "content://video/overlay", 1_000L, 0L, 2_000L, 0.5f, 1f)
            ))
        ),
        audioTracks = listOf(
            StudioRenderTrack("a-music", "Music", StudioRenderTrackType.AUDIO, listOf(
                StudioRenderClip("a-music", "music", "content://audio/music", 0L, 0L, 6_000L, 1f, 0.8f)
            ))
        ),
        warnings = emptyList(),
        errors = emptyList()
    )

    @Test fun keepsIndependentOverlappingTracksAsSeparateSequences() {
        val result = StudioCompositionTimelinePlanBuilder.build(plan())
        assertTrue(result.isValid)
        assertEquals(2, result.videoSequences.size)
        assertEquals(1, result.audioSequences.size)
        assertEquals(3, result.sequenceCount)
        assertEquals(4, result.clipCount)
        assertEquals(1_000_000L, result.videoSequences[1].clips.single().timelineStartUs)
    }

    @Test fun convertsTimelineAndSourceTrimTimesWithoutLosingTheTrim() {
        val clip = StudioCompositionTimelinePlanBuilder.build(plan()).videoSequences.first().clips.first()
        assertEquals(500_000L, clip.sourceInUs)
        assertEquals(2_500_000L, clip.sourceOutUs)
        assertEquals(2_000_000L, clip.durationUs)
        assertEquals(0L, clip.timelineStartUs)
        assertEquals(2_000_000L, clip.timelineEndUs)
    }

    @Test fun rejectsInconsistentSourceAndTimelineDurations() {
        val bad = plan().copy(videoTracks = listOf(
            StudioRenderTrack("v", "Video", StudioRenderTrackType.VIDEO, listOf(
                StudioRenderClip("v", "bad", "content://video/a", 0L, 0L, 3_000L, 1f, 1f)
            ))
        ))
        val result = StudioCompositionTimelinePlanBuilder.build(bad)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("inconsistent") })
    }

    @Test fun rejectsProjectDurationOutsideMicrosecondRange() {
        val bad = plan().copy(durationMs = Long.MAX_VALUE)
        val result = StudioCompositionTimelinePlanBuilder.build(bad)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("microsecond range") })
    }

    @Test fun rejectsAPlanWhoseExistingErrorsHaveNotBeenResolved() {
        val bad = plan().copy(errors = listOf("Source permission is missing."))
        val result = StudioCompositionTimelinePlanBuilder.build(bad)
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Source permission is missing."))
    }
}

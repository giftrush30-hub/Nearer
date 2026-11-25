package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioRenderPlanTest {
    private fun project() = StudioMultiTrackProject(
        tracks = listOf(
            StudioTimelineTrack("v-main", "Main video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("v2", "content://media/video-b", 3_000L, 500L, 2_500L),
                StudioTimelineItem("v1", "content://media/video-a", 0L, 0L, 3_000L, opacity = 0.75f)
            )),
            StudioTimelineTrack("a-music", "Music", StudioTrackKind.AUDIO, items = listOf(
                StudioTimelineItem("music", "content://media/music", 0L, 0L, 8_000L, volume = 0.8f)
            ))
        ), durationMs = 8_000L
    )

    @Test fun createsIndependentVideoAndAudioRenderLanes() {
        val plan = StudioRenderPlanBuilder.build(project())
        assertTrue(plan.isValid)
        assertEquals(2, plan.totalClipCount)
        assertEquals(1, plan.videoTracks.size)
        assertEquals(1, plan.audioTracks.size)
        assertEquals("v1", plan.videoTracks.single().clips.first().itemId)
        assertEquals(0.75f, plan.videoTracks.single().clips.first().opacity)
        assertEquals(0.8f, plan.audioTracks.single().clips.single().volume)
    }

    @Test fun mutedAudioLaneIsNotIncludedInRenderPlan() {
        val muted = project().copy(tracks = project().tracks.map {
            if (it.id == "a-music") it.copy(muted = true) else it
        })
        val plan = StudioRenderPlanBuilder.build(muted)
        assertTrue(plan.isValid)
        assertTrue(plan.audioTracks.isEmpty())
        assertEquals(1, plan.totalClipCount)
    }

    @Test fun rejectsNetworkSourcesToKeepCoreExportOffline() {
        val network = project().copy(tracks = listOf(
            StudioTimelineTrack("v", "Video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("remote", "https://example.invalid/video.mp4", 0L, 0L, 1_000L)
            ))
        ))
        val plan = StudioRenderPlanBuilder.build(network)
        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("offline") })
    }

    @Test fun rejectsMissingSourcesAndInvalidRanges() {
        val invalid = project().copy(tracks = listOf(
            StudioTimelineTrack("v", "Video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("missing", null, 0L, 0L, 1_000L),
                StudioTimelineItem("bad-range", "content://media/a", 0L, 2_000L, 1_000L)
            ))
        ))
        val plan = StudioRenderPlanBuilder.build(invalid)
        assertFalse(plan.isValid)
        assertEquals(2, plan.errors.size)
    }

    @Test fun proceduralTextAndEffectLanesAreReportedInsteadOfSilentlyRendered() {
        val withProcedural = project().copy(tracks = project().tracks + listOf(
            StudioTimelineTrack("titles", "Titles", StudioTrackKind.TEXT, items = listOf(
                StudioTimelineItem("title", null, 0L, 0L, 1_000L)
            )),
            StudioTimelineTrack("fx", "Effects", StudioTrackKind.EFFECT, items = listOf(
                StudioTimelineItem("fire", null, 500L, 0L, 2_000L)
            ))
        ))
        val plan = StudioRenderPlanBuilder.build(withProcedural)
        assertTrue(plan.isValid)
        assertEquals(2, plan.warnings.size)
        assertTrue(plan.warnings.all { it.contains("renderer asset") })
    }

    @Test fun overlappingItemsArePreservedAndFlaggedForCompositor() {
        val overlap = project().copy(tracks = listOf(
            StudioTimelineTrack("v", "Overlay video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("a", "content://media/a", 0L, 0L, 2_000L),
                StudioTimelineItem("b", "content://media/b", 1_000L, 0L, 2_000L)
            ))
        ))
        val plan = StudioRenderPlanBuilder.build(overlap)
        assertTrue(plan.isValid)
        assertEquals(2, plan.videoTracks.single().clips.size)
        assertTrue(plan.warnings.any { it.contains("layer-aware") })
    }

    @Test fun renderDurationNeverEndsBeforeTheLastMediaItem() {
        val shortProject = project().copy(durationMs = 1_000L)
        val plan = StudioRenderPlanBuilder.build(shortProject)
        assertEquals(8_000L, plan.durationMs)
        assertTrue(plan.warnings.any { it.contains("shorter than its media") })
    }

    @Test fun rejectsTimelineOverTwentyFourHours() {
        val tooLong = project().copy(durationMs = StudioRenderPlanBuilder.MAX_DURATION_MS + 1L)
        val plan = StudioRenderPlanBuilder.build(tooLong)
        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("24-hour") })
    }
}

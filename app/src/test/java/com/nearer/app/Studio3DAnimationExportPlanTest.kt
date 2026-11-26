package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Studio3DAnimationExportPlanTest {
    @Test fun createsExactPresentationTimestampsAtThirtyFramesPerSecond() {
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 30, emptyList()).plan)
        assertEquals(30, plan.frameCount)
        assertEquals(0L, plan.frameAt(0).presentationTimeUs)
        assertEquals(33_333L, plan.frameAt(1).presentationTimeUs)
        assertEquals(966_666L, plan.frameAt(29).presentationTimeUs)
    }

    @Test fun schedulesAnimationOnlyInsideItsTimelineWindow() {
        val track = Studio3DAnimationExportTrack("hero", "Walk", 200L, 800L, 2_000L)
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 30, listOf(track)).plan)
        assertTrue(plan.frameAt(5).activeAnimations.isEmpty())
        assertEquals(1, plan.frameAt(6).activeAnimations.size)
        assertTrue(plan.frameAt(23).activeAnimations.isNotEmpty())
        assertTrue(plan.frameAt(24).activeAnimations.isEmpty())
    }

    @Test fun loopsAnimationTimeUsingSourceDuration() {
        val track = Studio3DAnimationExportTrack("hero", "Run", 0L, 1_000L, 250L, loop = true)
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 24, listOf(track)).plan)
        assertEquals(0L, plan.frameAt(6).activeAnimations.single().animationTimeUs)
    }

    @Test fun clampsNonLoopingAnimationAtItsFinalTime() {
        val track = Studio3DAnimationExportTrack("hero", "Jump", 0L, 1_000L, 100L, loop = false)
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 30, listOf(track)).plan)
        assertEquals(100_000L, plan.frameAt(20).activeAnimations.single().animationTimeUs)
    }

    @Test fun appliesPlaybackRateToAnimationClock() {
        val track = Studio3DAnimationExportTrack("hero", "Walk", 0L, 1_000L, 2_000L, playbackRate = 2f)
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 30, listOf(track)).plan)
        assertEquals(200_000L, plan.frameAt(3).activeAnimations.single().animationTimeUs)
    }

    @Test fun permitsDifferentActorsToAnimateAtTheSameTime() {
        val tracks = listOf(
            Studio3DAnimationExportTrack("hero", "Walk", 0L, 1_000L, 2_000L),
            Studio3DAnimationExportTrack("dragon", "Fly", 0L, 1_000L, 3_000L)
        )
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 30, tracks).plan)
        assertEquals(setOf("hero", "dragon"), plan.frameAt(3).activeAnimations.map { it.actorId }.toSet())
    }

    @Test fun rejectsOverlappingTracksForTheSameActor() {
        val tracks = listOf(
            Studio3DAnimationExportTrack("hero", "Walk", 0L, 600L, 1_000L),
            Studio3DAnimationExportTrack("hero", "Run", 500L, 900L, 1_000L)
        )
        val result = Studio3DAnimationExportPlan.compile(1_000L, 30, tracks)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("overlapping") })
        assertNull(result.plan)
    }

    @Test fun rejectsInvalidRangesRatesFrameRatesAndDurations() {
        val track = Studio3DAnimationExportTrack("hero", "Walk", 500L, 400L, 0L, playbackRate = Float.NaN)
        val result = Studio3DAnimationExportPlan.compile(31L * 60L * 1_000L, 120, listOf(track))
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 4)
    }

    @Test fun rejectsOutOfRangeFrameRequests() {
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_000L, 24, emptyList()).plan)
        assertTrue(runCatching { plan.frameAt(-1) }.isFailure)
        assertTrue(runCatching { plan.frameAt(plan.frameCount) }.isFailure)
    }

    @Test fun roundsUpPartialFinalFrameWithoutDriftingTimestamps() {
        val plan = requireNotNull(Studio3DAnimationExportPlan.compile(1_001L, 30, emptyList()).plan)
        assertEquals(31, plan.frameCount)
        assertEquals(1_000_000L, plan.frameAt(30).presentationTimeUs)
    }
}

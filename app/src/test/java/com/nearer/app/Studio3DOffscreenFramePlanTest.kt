package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class Studio3DOffscreenFramePlanTest {
    private fun animationPlan() = Studio3DAnimationExportPlan.compile(
        durationMs = 1_000L,
        frameRate = 30,
        tracks = listOf(
            Studio3DAnimationExportTrack("hero", "Walk", 0L, 1_000L, 800L, 1f, true),
            Studio3DAnimationExportTrack("bird", "Fly", 250L, 1_000L, 500L, 1.5f, true)
        )
    ).plan!!

    @Test fun emitsFrameAccurateRgbaRequestsOnDemand() {
        val compiled = Studio3DOffscreenFramePlan.compile(animationPlan(), 1920, 1080)
        assertTrue(compiled.isValid)
        val plan = compiled.plan!!
        assertEquals(30, plan.frameRate)
        assertEquals(30, plan.frameCount)
        assertEquals(1920 * 4, plan.frameAt(0).rowStrideBytes)
        assertEquals(1920 * 1080 * 4, plan.frameAt(0).rgbaByteCount)
        assertEquals(0L, plan.frameAt(0).presentationTimeUs)
        assertEquals(33_333L, plan.frameAt(1).presentationTimeUs)
        assertEquals(listOf("hero"), plan.frameAt(0).animations.map { it.actorId })
        assertEquals(listOf("hero", "bird"), plan.frameAt(8).animations.map { it.actorId })
    }

    @Test fun calculatesRawMovieFootprintWithoutAllocatingMovieFrames() {
        val plan = Studio3DOffscreenFramePlan.compile(animationPlan(), 1280, 720).plan!!
        assertEquals(1280L * 720L * 4L, plan.bytesPerFrame.toLong())
        assertEquals(plan.bytesPerFrame.toLong() * plan.frameCount, plan.estimatedRawFrameBytes)
        assertTrue(plan.estimatedRawFrameBytes > plan.bytesPerFrame.toLong())
    }

    @Test fun supports4kAndRejectsOversizedOrInvalidTargets() {
        assertTrue(Studio3DOffscreenFramePlan.compile(animationPlan(), 3840, 2160).isValid)
        assertFalse(Studio3DOffscreenFramePlan.compile(animationPlan(), 4096, 2160).isValid)
        assertFalse(Studio3DOffscreenFramePlan.compile(animationPlan(), 0, 1080).isValid)
        assertFalse(Studio3DOffscreenFramePlan.compile(animationPlan(), 3840, 4320).isValid)
    }

    @Test fun rejectsInvalidAnimationPlanBeforeFrameRendering() {
        val invalid = Studio3DAnimationExportPlan.compile(0L, 30, emptyList())
        assertFalse(invalid.isValid)
        assertNull(invalid.plan)
    }

    @Test fun rejectsFrameIndicesOutsideMovieRange() {
        val plan = Studio3DOffscreenFramePlan.compile(animationPlan(), 640, 360).plan!!
        assertTrue(runCatching { plan.frameAt(-1) }.isFailure)
        assertTrue(runCatching { plan.frameAt(plan.frameCount) }.isFailure)
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioCompositionFramePlannerTest {
    private fun timeline(): StudioCompositionTimelinePlan = StudioCompositionTimelinePlan(
        durationUs = 5_000_000L,
        videoSequences = listOf(
            StudioCompositionSequenceTiming("main", "Main", StudioRenderTrackType.VIDEO, listOf(
                StudioCompositionClipTiming("main", "a", "content://video/a", 0L, 2_000_000L, 0L, 2_000_000L, 1f, 1f),
                StudioCompositionClipTiming("main", "b", "content://video/b", 2_000_000L, 4_000_000L, 500_000L, 2_500_000L, 0.8f, 1f)
            )),
            StudioCompositionSequenceTiming("overlay", "Overlay", StudioRenderTrackType.VIDEO, listOf(
                StudioCompositionClipTiming("overlay", "pip", "content://video/pip", 1_000_000L, 3_000_000L, 0L, 2_000_000L, 0.6f, 1f)
            ))
        ),
        audioSequences = listOf(
            StudioCompositionSequenceTiming("music", "Music", StudioRenderTrackType.AUDIO, listOf(
                StudioCompositionClipTiming("music", "song", "content://audio/song", 0L, 5_000_000L, 1_000_000L, 6_000_000L, 1f, 0.4f)
            ))
        ),
        errors = emptyList()
    )

    @Test fun frameContainsAllConcurrentVideoLayersAndAudio() {
        val frame = StudioCompositionFramePlanner.build(timeline()).frameAt(1_500_000L)
        assertEquals(2, frame.videoLayers.size)
        assertEquals("a", frame.videoLayers.first().itemId)
        assertEquals("pip", frame.videoLayers.last().itemId)
        assertEquals(1, frame.audioLayers.size)
        assertEquals(0.4f, frame.audioLayers.single().volume)
    }

    @Test fun mapsOutputTimestampBackToTrimmedSourceTimestamp() {
        val frame = StudioCompositionFramePlanner.build(timeline()).frameAt(2_250_000L)
        val mainClip = frame.videoLayers.first { it.itemId == "b" }
        assertEquals(750_000L, mainClip.sourceTimeUs)
        assertEquals(250_000L, mainClip.localTimeUs)
    }

    @Test fun evaluatesClipLocalKeyframesForAnimatedProperties() {
        val binding = StudioLayerAnimationBinding("b", listOf(
            StudioPropertyAnimation(StudioAnimatedProperty.OPACITY, listOf(
                StudioKeyframe(0L, 0.2f),
                StudioKeyframe(1_000L, 1f)
            ))
        ))
        val frame = StudioCompositionFramePlanner.build(timeline()).frameAt(2_250_000L, listOf(binding))
        assertEquals(0.4f, frame.videoLayers.first { it.itemId == "b" }.animatedValues[StudioAnimatedProperty.OPACITY]!!, 0.001f)
    }

    @Test fun plansFadeToBlackOnlyForAdjacentNonOverlappingClips() {
        val plan = StudioCompositionFramePlanner.build(timeline(), StudioTransitionKind.FADE_TO_BLACK, 400)
        assertTrue(plan.isValid)
        assertEquals(1, plan.transitions.size)
        val transition = plan.transitions.single()
        assertEquals("a", transition.outgoingItemId)
        assertEquals("b", transition.incomingItemId)
        assertEquals(1_600_000L, transition.outgoingStartUs)
        assertEquals(2_400_000L, transition.incomingEndUs)
    }

    @Test fun propagatesInvalidTimelineAndRejectsNegativeFrameTime() {
        val invalid = timeline().copy(errors = listOf("bad source"))
        val plan = StudioCompositionFramePlanner.build(invalid)
        assertFalse(plan.isValid)
        assertTrue(plan.errors.contains("bad source"))
        try {
            plan.frameAt(-1L)
            throw AssertionError("Expected negative frame timestamp to be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}

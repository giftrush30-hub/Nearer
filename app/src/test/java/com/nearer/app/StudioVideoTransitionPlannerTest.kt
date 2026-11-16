package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVideoTransitionPlannerTest {
    @Test fun middleClipFadesInAndOutWithoutOverlappingRanges() {
        val segments = StudioVideoTransitionPlanner.plan(4_000L, 400, true, true)
        assertEquals(32, segments.size)
        assertEquals(0L, segments.first().startUs)
        assertEquals(4_000_000L, segments.last().endUs)
        assertTrue(segments.take(16).all { it.brightness < 0f })
        assertTrue(segments.take(16).zipWithNext().all { it.first.endUs <= it.second.startUs })
        assertTrue(segments.drop(16).zipWithNext().all { it.first.endUs <= it.second.startUs })
    }

    @Test fun firstClipOnlyFadesOutAndLastClipOnlyFadesIn() {
        val first = StudioVideoTransitionPlanner.plan(2_000L, 300, false, true)
        val last = StudioVideoTransitionPlanner.plan(2_000L, 300, true, false)
        assertEquals(16, first.size)
        assertEquals(16, last.size)
        assertEquals(1_700_000L, first.first().startUs)
        assertEquals(0L, last.first().startUs)
    }

    @Test fun shortClipCapsBothFadesToHalfItsDuration() {
        val segments = StudioVideoTransitionPlanner.plan(300L, 1_000, true, true)
        assertEquals(32, segments.size)
        assertEquals(150_000L, segments[15].endUs)
        assertEquals(150_000L, segments[16].startUs)
    }

    @Test fun invalidAndDisabledTransitionsProduceNoSegments() {
        assertTrue(StudioVideoTransitionPlanner.plan(0L, 400, true, true).isEmpty())
        assertTrue(StudioVideoTransitionPlanner.plan(2_000L, 400, false, false).isEmpty())
    }
}

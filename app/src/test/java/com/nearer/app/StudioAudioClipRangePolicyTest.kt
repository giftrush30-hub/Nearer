package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioAudioClipRangePolicyTest {
    @Test fun trimStartCannotCrossEndOrCreateSubMinimumClip() {
        assertEquals(9_500L, StudioAudioClipRangePolicy.moveStart(1_000L, 10_000L, 20_000L, 20_000L))
        assertEquals(0L, StudioAudioClipRangePolicy.moveStart(1_000L, 10_000L, -500L, 20_000L))
    }

    @Test fun trimEndCannotCrossStartOrExceedDuration() {
        assertEquals(1_500L, StudioAudioClipRangePolicy.moveEnd(1_000L, 8_000L, 0L, 20_000L))
        assertEquals(20_000L, StudioAudioClipRangePolicy.moveEnd(1_000L, 8_000L, 30_000L, 20_000L))
    }

    @Test fun shortSourceUsesItsEntireAvailableDurationAsMinimum() {
        assertEquals(0L, StudioAudioClipRangePolicy.moveStart(0L, 300L, 200L, 300L))
        assertEquals(300L, StudioAudioClipRangePolicy.moveEnd(0L, 300L, 100L, 300L))
    }

    @Test fun unknownDurationDoesNotProduceInvalidPositions() {
        assertEquals(0L, StudioAudioClipRangePolicy.moveStart(0L, 0L, 500L, 0L))
        assertEquals(0L, StudioAudioClipRangePolicy.moveEnd(0L, 0L, 500L, 0L))
    }
}

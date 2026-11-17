package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioAudioBeatGridPolicyTest {
    @Test fun computesQuarterEighthAndSixteenthNoteIntervals() {
        assertEquals(500.0, StudioAudioBeatGridPolicy.gridIntervalMs(120, 1), 0.0001)
        assertEquals(250.0, StudioAudioBeatGridPolicy.gridIntervalMs(120, 2), 0.0001)
        assertEquals(125.0, StudioAudioBeatGridPolicy.gridIntervalMs(120, 4), 0.0001)
    }

    @Test fun snapsToNearestGridPositionAtDifferentTempos() {
        assertEquals(1_000L, StudioAudioBeatGridPolicy.snapOffsetMs(1_120L, 120, 1))
        assertEquals(1_125L, StudioAudioBeatGridPolicy.snapOffsetMs(1_140L, 120, 4))
        assertEquals(750L, StudioAudioBeatGridPolicy.snapOffsetMs(700L, 80, 2))
    }

    @Test fun normalizesUnsupportedTempoAndSubdivisionValues() {
        assertEquals(40, StudioAudioBeatGridPolicy.normalizeBpm(0))
        assertEquals(240, StudioAudioBeatGridPolicy.normalizeBpm(500))
        assertEquals(2, StudioAudioBeatGridPolicy.normalizeBeatDivision(3))
        assertEquals(4, StudioAudioBeatGridPolicy.normalizeBeatDivision(4))
    }

    @Test fun clampsOffsetsToTimelineBounds() {
        assertEquals(0L, StudioAudioBeatGridPolicy.snapOffsetMs(-100L, 120, 2))
        assertEquals(30_000L, StudioAudioBeatGridPolicy.snapOffsetMs(40_000L, 120, 2))
        assertEquals(30_000L, StudioAudioBeatGridPolicy.gridPositionMs(10_000, 120, 4))
    }
}

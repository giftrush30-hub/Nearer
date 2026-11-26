package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPcmTimestampAlignerTest {
    @Test fun preservesPositiveAudioTrackStartOffsetWithSilence() {
        val aligner = StudioPcmTimestampAligner(48_000, 48_000)
        val output = mutableListOf<Float>()

        aligner.accept(4_800.0, 0.75f, 0.75f) { left, _ -> output += left }

        assertEquals(4_801L, aligner.outputFrameCount)
        assertTrue(output.take(4_800).all { it == 0f })
        assertEquals(0.75f, output.last())
    }

    @Test fun resamplesByAbsoluteFrameClockWithoutCumulativeRounding() {
        val aligner = StudioPcmTimestampAligner(24_000, 48_000)
        val output = mutableListOf<Float>()

        aligner.accept(0.0, 0f, 0f) { left, _ -> output += left }
        aligner.accept(1.0, 1f, 1f) { left, _ -> output += left }

        assertEquals(3L, aligner.outputFrameCount)
        assertEquals(listOf(0f, 0.5f, 1f), output)
    }

    @Test fun padsTimestampGapsInsteadOfStretchingPreviousSample() {
        val aligner = StudioPcmTimestampAligner(48_000, 48_000)
        val output = mutableListOf<Float>()

        aligner.accept(0.0, 1f, 1f) { left, _ -> output += left }
        aligner.accept(1.0, 2f, 2f) { left, _ -> output += left }
        aligner.accept(5.0, 6f, 6f) { left, _ -> output += left }

        assertEquals(listOf(1f, 2f, 0f, 0f, 0f, 6f), output)
    }

    @Test fun ignoresOverlappingDuplicateDecoderBoundaryFrames() {
        val aligner = StudioPcmTimestampAligner(48_000, 48_000)
        val output = mutableListOf<Float>()

        aligner.accept(0.0, 1f, 1f) { left, _ -> output += left }
        aligner.accept(1.0, 2f, 2f) { left, _ -> output += left }
        aligner.accept(1.0, 9f, 9f) { left, _ -> output += left }
        aligner.accept(2.0, 3f, 3f) { left, _ -> output += left }

        assertEquals(listOf(1f, 2f, 3f), output)
    }

    @Test fun trimsNegativePresentationTimeBeforeTimelineZero() {
        val aligner = StudioPcmTimestampAligner(48_000, 48_000)
        val output = mutableListOf<Float>()

        aligner.accept(-2.0, -2f, -2f) { left, _ -> output += left }
        aligner.accept(-1.0, -1f, -1f) { left, _ -> output += left }
        aligner.accept(0.0, 0f, 0f) { left, _ -> output += left }
        aligner.accept(1.0, 1f, 1f) { left, _ -> output += left }

        assertEquals(listOf(0f, 1f), output)
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioWaveformAccumulatorTest {
    @Test fun aggregatesPeaksIntoOrderedTimeBuckets() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 4, expectedFrames = 8)
        accumulator.addInterleaved(floatArrayOf(
            0.1f, -0.2f, 0.3f, -0.4f,
            0.5f, -0.6f, 0.7f, -0.8f
        ), channelCount = 1)
        val peaks = accumulator.finish()
        assertEquals(4, peaks.size)
        assertEquals(0.2f, peaks[0], 0.0001f)
        assertEquals(0.4f, peaks[1], 0.0001f)
        assertEquals(0.6f, peaks[2], 0.0001f)
        assertEquals(0.8f, peaks[3], 0.0001f)
    }

    @Test fun stereoUsesTheLouderChannelForEachFrame() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(0.1f, -0.7f, 0.8f, 0.2f), channelCount = 2)
        assertEquals(listOf(0.7f, 0.8f), accumulator.finish())
    }

    @Test fun silenceProducesZeroHeightBars() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 8, expectedFrames = 64)
        accumulator.addInterleaved(FloatArray(128), channelCount = 2)
        assertTrue(accumulator.finish().all { it == 0f })
    }

    @Test fun clampsOutOfRangeAndNonFiniteSamples() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(4f, Float.NaN, Float.POSITIVE_INFINITY, -0.25f), 2)
        val peaks = accumulator.finish()
        assertEquals(1f, peaks[0], 0f)
        assertEquals(0.25f, peaks[1], 0.0001f)
    }

    @Test fun ignoresIncompleteTrailingInterleavedFrame() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(0.5f, 0.1f, 0.9f), channelCount = 2)
        assertEquals(0.5f, accumulator.finish()[0], 0.0001f)
        assertEquals(0f, accumulator.finish()[1], 0f)
    }
}

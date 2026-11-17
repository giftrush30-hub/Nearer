package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StudioAudioWaveformAccumulatorTest {
    @Test fun aggregatesPeaksIntoOrderedTimeBuckets() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 4, expectedFrames = 8)
        accumulator.addInterleaved(floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f, 0.5f, -0.6f, 0.7f, -0.8f).toFloatBuffer(), channelCount = 1)
        val peaks = accumulator.finish()
        assertEquals(4, peaks.size)
        assertEquals(0.2f, peaks[0], 0.0001f)
        assertEquals(0.4f, peaks[1], 0.0001f)
        assertEquals(0.6f, peaks[2], 0.0001f)
        assertEquals(0.8f, peaks[3], 0.0001f)
    }

    @Test fun stereoUsesTheLouderChannelForEachFrame() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(0.1f, -0.7f, 0.8f, 0.2f).toFloatBuffer(), channelCount = 2)
        assertEquals(listOf(0.7f, 0.8f), accumulator.finish())
    }

    @Test fun silenceProducesZeroHeightBars() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 8, expectedFrames = 64)
        accumulator.addInterleaved(FloatArray(128).toFloatBuffer(), channelCount = 2)
        assertTrue(accumulator.finish().all { it == 0f })
    }

    @Test fun clampsOutOfRangeAndNonFiniteSamples() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(4f, Float.NaN, Float.POSITIVE_INFINITY, -0.25f).toFloatBuffer(), 2)
        val peaks = accumulator.finish()
        assertEquals(1f, peaks[0], 0f)
        assertEquals(0.25f, peaks[1], 0.0001f)
    }

    @Test fun ignoresIncompleteTrailingInterleavedFrame() {
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(floatArrayOf(0.5f, 0.1f, 0.9f).toFloatBuffer(), channelCount = 2)
        assertEquals(0.5f, accumulator.finish()[0], 0.0001f)
        assertEquals(0f, accumulator.finish()[1], 0f)
    }
}

private fun FloatArray.toFloatBuffer() = ByteBuffer.allocate(size * 4)
    .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(this@toFloatBuffer); flip() }

class StudioAudioWaveformPcm16Test {
    @Test fun decodesSignedPcm16AndUsesPeakAcrossStereoChannels() {
        val shorts = shortArrayOf(0, -16384, 8192, 0)
        val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(shorts); flip() }
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(buffer, channelCount = 2)
        assertEquals(listOf(0.5f, 0.25f), accumulator.finish())
    }

    @Test fun ignoresIncompleteTrailingPcm16Frame() {
        val buffer = ByteBuffer.allocate(3 * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(shortArrayOf(16384, 0, 32767)); flip() }
        val accumulator = StudioAudioWaveformAccumulator(bucketCount = 2, expectedFrames = 2)
        accumulator.addInterleaved(buffer, channelCount = 2)
        assertEquals(0.5f, accumulator.finish()[0], 0.0001f)
        assertEquals(0f, accumulator.finish()[1], 0f)
    }
}

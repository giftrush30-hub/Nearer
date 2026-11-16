package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPcmFrameAccumulatorTest {
    @Test fun joinsPartialReadsWithoutLosingSamples() {
        val accumulator = StudioPcmFrameAccumulator(512)
        val first = FloatArray(300) { it.toFloat() }
        val second = FloatArray(400) { (300 + it).toFloat() }
        val frames = mutableListOf<FloatArray>()
        accumulator.append(first, first.size) { frames += it.copyOf() }
        assertTrue(frames.isEmpty())
        accumulator.append(second, second.size) { frames += it.copyOf() }
        assertEquals(1, frames.size)
        assertEquals(0f, frames.single().first(), 0f)
        assertEquals(511f, frames.single().last(), 0f)
    }

    @Test fun safelySplitsOversizedReadIntoSeveralFrames() {
        val accumulator = StudioPcmFrameAccumulator(512)
        val input = FloatArray(1_300) { it.toFloat() }
        val frames = mutableListOf<FloatArray>()
        accumulator.append(input, input.size) { frames += it.copyOf() }
        assertEquals(2, frames.size)
        assertEquals(0f, frames[0].first(), 0f)
        assertEquals(511f, frames[0].last(), 0f)
        assertEquals(512f, frames[1].first(), 0f)
        assertEquals(1_023f, frames[1].last(), 0f)
    }

    @Test fun handlesZeroAndExactBoundaryReads() {
        val accumulator = StudioPcmFrameAccumulator(512)
        val frames = mutableListOf<FloatArray>()
        accumulator.append(FloatArray(0), 0) { frames += it.copyOf() }
        accumulator.append(FloatArray(512) { 1f }, 512) { frames += it.copyOf() }
        assertEquals(1, frames.size)
    }

    @Test fun resetDiscardsAnIncompleteFrame() {
        val accumulator = StudioPcmFrameAccumulator(512)
        val frames = mutableListOf<FloatArray>()
        accumulator.append(FloatArray(300) { 1f }, 300) { frames += it.copyOf() }
        accumulator.reset()
        accumulator.append(FloatArray(512) { 2f }, 512) { frames += it.copyOf() }
        assertEquals(1, frames.size)
        assertEquals(2f, frames.single().first(), 0f)
    }
}

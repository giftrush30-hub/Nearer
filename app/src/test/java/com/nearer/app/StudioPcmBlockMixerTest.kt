package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPcmBlockMixerTest {
    private class ConstantSource(private val left: Float, private val right: Float) : StudioPcmStereoSource {
        var lastStartFrame = -1L
        var lastFrameCount = -1
        override fun readStereoFrames(sourceStartFrame: Long, frameCount: Int, destination: FloatArray, destinationOffset: Int): Int {
            lastStartFrame = sourceStartFrame
            lastFrameCount = frameCount
            repeat(frameCount) { frame ->
                destination[destinationOffset + frame * 2] = left
                destination[destinationOffset + frame * 2 + 1] = right
            }
            return frameCount
        }
    }

    @Test fun mixesTrimmedSourceIntoCorrectTimelineOffsetAndPreservesOutsideSamples() {
        val cue = StudioAudioMixCue("voice", "content://voice", 10L, 1_000L, 2_000L)
        val source = ConstantSource(0.25f, -0.25f)
        val output = FloatArray(44) { 9f }
        val result = StudioPcmBlockMixer.mixBlock(
            listOf(cue), mapOf("voice" to source), 70L, 20, 8_000, output, outputOffset = 2
        )
        assertEquals(1, result.activeCueCount)
        assertEquals(8_000L, source.lastStartFrame)
        assertEquals(10, source.lastFrameCount)
        assertEquals(9f, output[0], 0f)
        assertEquals(0f, output[2], 0f)
        assertEquals(0.25f, output[22], 0.0001f)
        assertEquals(-0.25f, output[23], 0.0001f)
    }

    @Test fun mixesOverlappingTracksWithGainAndStereoPan() {
        val cues = listOf(
            StudioAudioMixCue("left", "content://left", 0L, 0L, 100L, gain = 0.5f, pan = -1f),
            StudioAudioMixCue("right", "content://right", 0L, 0L, 100L, gain = 0.5f, pan = 1f)
        )
        val output = FloatArray(8)
        StudioPcmBlockMixer.mixBlock(
            cues, mapOf("left" to ConstantSource(1f, 1f), "right" to ConstantSource(1f, 1f)),
            0L, 4, 8_000, output
        )
        assertEquals(0.5f, output[0], 0.0001f)
        assertEquals(0.5f, output[1], 0.0001f)
        assertEquals(0.5f, output[2], 0.0001f)
        assertEquals(0.5f, output[3], 0.0001f)
    }

    @Test fun appliesFadeInPerSampleAndLimitsSummedPeaks() {
        val fade = StudioAudioMixCue("fade", "content://fade", 0L, 0L, 100L, fadeInMs = 50L)
        val loud = StudioAudioMixCue("loud", "content://loud", 0L, 0L, 100L)
        val output = FloatArray(1_024)
        val result = StudioPcmBlockMixer.mixBlock(
            listOf(fade, loud),
            mapOf("fade" to ConstantSource(0.8f, 0.8f), "loud" to ConstantSource(0.8f, 0.8f)),
            0L, 512, 8_000, output
        )
        assertEquals(0.8f, output[0], 0.0001f)
        assertTrue(output[800] > 0.98f)
        assertTrue(result.peakBeforeLimiter > 1f)
        assertTrue(result.peakAfterLimiter <= 1f)
    }

    @Test fun mutedAndOutOfWindowCuesDoNotReadSources() {
        val muted = StudioAudioMixCue("muted", "content://muted", 0L, 0L, 100L, muted = true)
        val later = StudioAudioMixCue("later", "content://later", 1_000L, 0L, 100L)
        val source = ConstantSource(1f, 1f)
        val output = FloatArray(4)
        val result = StudioPcmBlockMixer.mixBlock(
            listOf(muted, later), emptyMap(), 0L, 2, 8_000, output
        )
        assertEquals(0, result.activeCueCount)
        assertEquals(0f, output[0], 0f)
        assertEquals(-1L, source.lastStartFrame)
    }

    @Test fun rejectsOversizedBlocksAndMissingActiveSources() {
        val cue = StudioAudioMixCue("voice", "content://voice", 0L, 0L, 100L)
        assertTrue(runCatching {
            StudioPcmBlockMixer.mixBlock(listOf(cue), emptyMap(), 0L, 2, 8_000, FloatArray(4))
        }.isFailure)
        assertTrue(runCatching {
            StudioPcmBlockMixer.mixBlock(emptyList(), emptyMap(), 0L, 4_097, 8_000, FloatArray(8_194))
        }.isFailure)
    }
}

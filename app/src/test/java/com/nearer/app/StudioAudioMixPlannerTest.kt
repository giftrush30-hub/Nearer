package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioMixPlannerTest {
    private val cues = listOf(
        StudioAudioMixCue("voice", "content://audio/voice", 0L, 1_000L, 5_000L, 1f, 500L, 500L, -0.5f),
        StudioAudioMixCue("music", "content://audio/music", 1_000L, 0L, 8_000L, 0.4f, 1_000L, 1_000L, 0.5f),
        StudioAudioMixCue("muted", "content://audio/muted", 0L, 0L, 5_000L, 1f, muted = true)
    )

    @Test fun validatesOfflineTracksAndRejectsRemoteSources() {
        assertTrue(StudioAudioMixPlanner.validate(cues, 48_000).isValid)
        val invalid = cues + StudioAudioMixCue("remote", "https://example.invalid/audio", 0L, 0L, 1_000L)
        assertFalse(StudioAudioMixPlanner.validate(invalid, 48_000).isValid)
    }

    @Test fun returnsOnlyActiveUnmutedTracksWithTrimmedSourceFramePositions() {
        val samples = StudioAudioMixPlanner.samplesAt(cues, 2_000L, 48_000)
        assertEquals(2, samples.size)
        val voice = samples.first { it.cueId == "voice" }
        assertEquals(3_000L * 48L, voice.sourceFrame)
        assertTrue(voice.leftFactor > voice.rightFactor)
    }

    @Test fun appliesFadeEnvelopeAndKeepsPanWithinUnitRange() {
        val start = StudioAudioMixPlanner.samplesAt(cues, 0L, 48_000).single()
        assertEquals(0f, start.gain, 0.001f)
        val mid = StudioAudioMixPlanner.samplesAt(cues, 2_000L, 48_000).first { it.cueId == "voice" }
        assertTrue(mid.gain > 0.9f)
        assertTrue(mid.leftFactor in 0f..1f && mid.rightFactor in 0f..1f)
    }

    @Test fun softLimiterNeverExceedsUnityAndPreservesSubCeilingSamples() {
        assertEquals(0.5f, StudioAudioMixPlanner.limitMixedSample(0.5f), 0.0001f)
        assertTrue(kotlin.math.abs(StudioAudioMixPlanner.limitMixedSample(3f)) <= 1f)
        assertTrue(kotlin.math.abs(StudioAudioMixPlanner.limitMixedSample(-3f)) <= 1f)
    }
}

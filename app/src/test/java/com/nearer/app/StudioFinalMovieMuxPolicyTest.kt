package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioFinalMovieMuxPolicyTest {
    @Test fun presentationTimeOrderSelectsEarlierTrackFirst() {
        assertEquals("audio", StudioFinalMovieMuxPolicy.nextTrack(1_000L, 900L, true, true))
        assertEquals("video", StudioFinalMovieMuxPolicy.nextTrack(900L, 1_000L, true, true))
        assertEquals("video", StudioFinalMovieMuxPolicy.nextTrack(900L, 900L, true, true))
    }

    @Test fun exhaustedTrackDoesNotBlockRemainingTrack() {
        assertEquals("audio", StudioFinalMovieMuxPolicy.nextTrack(-1L, 300L, false, true))
        assertEquals("video", StudioFinalMovieMuxPolicy.nextTrack(300L, -1L, true, false))
        assertEquals(null, StudioFinalMovieMuxPolicy.nextTrack(-1L, -1L, false, false))
    }

    @Test fun sampleTimestampMustIncreaseWithinTrack() {
        assertTrue(StudioFinalMovieMuxPolicy.isTimestampAfter(-1L, 0L))
        assertTrue(StudioFinalMovieMuxPolicy.isTimestampAfter(10L, 11L))
        assertFalse(StudioFinalMovieMuxPolicy.isTimestampAfter(10L, 10L))
        assertFalse(StudioFinalMovieMuxPolicy.isTimestampAfter(10L, 9L))
    }

    @Test fun sampleSizeMustFitBoundedBuffer() {
        assertTrue(StudioFinalMovieMuxPolicy.isSafeSampleSize(1L))
        assertTrue(StudioFinalMovieMuxPolicy.isSafeSampleSize(32L * 1024L * 1024L))
        assertFalse(StudioFinalMovieMuxPolicy.isSafeSampleSize(0L))
        assertFalse(StudioFinalMovieMuxPolicy.isSafeSampleSize(32L * 1024L * 1024L + 1L))
    }
}

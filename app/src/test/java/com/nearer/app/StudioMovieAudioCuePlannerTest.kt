package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMovieAudioCuePlannerTest {
    @Test fun videoAudioUsesPictureTrimAndTimelineOffset() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "scene-1", sourceUri = "file:///movies/scene.mp4",
                    timelineStartMs = 2_000L, sourceInMs = 500L, sourceOutMs = 4_500L,
                    sourceDurationMs = 8_000L, hasAudioTrack = true, gain = 0.8f
                )
            ),
            music = null
        )

        assertTrue(plan.isValid)
        assertEquals(1, plan.cues.size)
        val cue = plan.cues.single()
        assertEquals(2_000L, cue.timelineStartMs)
        assertEquals(500L, cue.sourceInMs)
        assertEquals(4_500L, cue.sourceOutMs)
        assertEquals(0.8f, cue.gain)
    }

    @Test fun silentVideoDoesNotCreateBrokenAudioCue() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "silent", sourceUri = "content://media/video/1",
                    timelineStartMs = 0L, sourceInMs = 0L, sourceOutMs = 3_000L,
                    sourceDurationMs = 3_000L, hasAudioTrack = false
                )
            ),
            music = null
        )

        assertTrue(plan.isValid)
        assertTrue(plan.cues.isEmpty())
        assertEquals(1, plan.warnings.size)
    }

    @Test fun shortMusicLoopsAcrossMovieAndKeepsFinalFade() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "scene", sourceUri = "file:///movies/scene.mp4",
                    timelineStartMs = 0L, sourceInMs = 0L, sourceOutMs = 5_500L,
                    sourceDurationMs = 5_500L, hasAudioTrack = false
                )
            ),
            music = StudioMovieMusicInput(
                sourceUri = "file:///music/theme.m4a", sourceDurationMs = 2_000L,
                gain = 0.6f, fadeInMs = 300L, fadeOutMs = 500L
            )
        )

        assertTrue(plan.isValid)
        val music = plan.cues.filter { it.id.startsWith("music-bed:") }
        assertEquals(3, music.size)
        assertEquals(listOf(0L, 2_000L, 4_000L), music.map { it.timelineStartMs })
        assertEquals(listOf(2_000L, 2_000L, 1_500L), music.map { it.durationMs })
        assertEquals(300L, music.first().fadeInMs)
        assertEquals(500L, music.last().fadeOutMs)
    }

    @Test fun mutedMusicAndMutedVideoAudioAreOmitted() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "muted-scene", sourceUri = "file:///movies/scene.mp4",
                    timelineStartMs = 0L, sourceInMs = 0L, sourceOutMs = 4_000L,
                    sourceDurationMs = 4_000L, hasAudioTrack = true, muted = true
                )
            ),
            music = StudioMovieMusicInput(
                sourceUri = "file:///music/theme.mp3", sourceDurationMs = 2_000L, muted = true
            )
        )

        assertTrue(plan.isValid)
        assertTrue(plan.cues.isEmpty())
    }

    @Test fun rejectsRemoteSourceAndInvalidTrim() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "bad", sourceUri = "https://example.com/clip.mp4",
                    timelineStartMs = 0L, sourceInMs = 4_000L, sourceOutMs = 3_000L,
                    sourceDurationMs = 5_000L, hasAudioTrack = true
                )
            ),
            music = null
        )

        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("local") })
        assertTrue(plan.errors.any { it.contains("range") })
    }

    @Test fun capsMusicLoopSegmentsInsteadOfCreatingUnboundedCueList() {
        val plan = StudioMovieAudioCuePlanner.build(
            clips = listOf(
                StudioMovieAudioClipInput(
                    id = "long", sourceUri = "file:///movies/long.mp4",
                    timelineStartMs = 0L, sourceInMs = 0L, sourceOutMs = 86_400_000L,
                    sourceDurationMs = 86_400_000L, hasAudioTrack = false
                )
            ),
            music = StudioMovieMusicInput(
                sourceUri = "file:///music/short.wav", sourceDurationMs = 1L
            )
        )

        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("loop segments") || it.contains("cue safety") })
    }
}

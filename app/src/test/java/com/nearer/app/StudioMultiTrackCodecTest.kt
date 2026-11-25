package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMultiTrackCodecTest {
    @Test fun roundTripsIndependentVideoAudioTextAndEffectTracks() {
        val project = StudioMultiTrackProject(
            tracks = listOf(
                StudioTimelineTrack("v1", "Main picture", StudioTrackKind.VIDEO, items = listOf(
                    StudioTimelineItem("shot-a", "content://video/a", 0L, 1_000L, 6_000L),
                    StudioTimelineItem("shot-b", "content://video/b", 5_000L, 0L, 4_000L)
                )),
                StudioTimelineTrack("a1", "Music", StudioTrackKind.AUDIO, items = listOf(
                    StudioTimelineItem("music-a", "content://audio/music", 0L, 0L, 9_000L, volume = 0.35f)
                )),
                StudioTimelineTrack("t1", "Titles", StudioTrackKind.TEXT, locked = true),
                StudioTimelineTrack("e1", "Effects", StudioTrackKind.EFFECT, muted = true)
            ),
            durationMs = 9_000L
        )
        assertEquals(project, StudioMultiTrackCodec.decode(StudioMultiTrackCodec.encode(project)))
    }

    @Test fun skipsMalformedTracksAndItemsWithoutLosingValidLanes() {
        val raw = """{"version":1,"durationMs":1000,"tracks":[{"id":"bad","name":"Bad","kind":"UNKNOWN","items":[]},{"id":"v","name":"Video","kind":"VIDEO","items":[{"id":"x","timelineStartMs":-1,"sourceInMs":0,"sourceOutMs":10},{"id":"ok","sourceUri":"content://video/a","timelineStartMs":0,"sourceInMs":0,"sourceOutMs":500}]}]}"""
        val restored = StudioMultiTrackCodec.decode(raw)
        assertEquals(1, restored.tracks.size)
        assertEquals(1, restored.tracks.single().items.size)
    }

    @Test fun clampsMixValuesAndRejectsUnknownVersions() {
        val raw = """{"version":1,"tracks":[{"id":"a","name":"Audio","kind":"AUDIO","items":[{"id":"x","sourceUri":"content://audio/a","timelineStartMs":0,"sourceInMs":0,"sourceOutMs":1000,"volume":4.0,"opacity":-1.0}]}]}"""
        val item = StudioMultiTrackCodec.decode(raw).tracks.single().items.single()
        assertEquals(2f, item.volume, 0f)
        assertEquals(0f, item.opacity, 0f)
        assertTrue(StudioMultiTrackCodec.decode("""{"version":9,"tracks":[]}""").tracks.isEmpty())
    }

    @Test fun capsTrackAndItemCountsAndDerivesSafeDuration() {
        val many = (1..40).map { index ->
            StudioTimelineTrack("track-$index", "Track $index", StudioTrackKind.VIDEO)
        }
        assertEquals(32, StudioMultiTrackCodec.decode(StudioMultiTrackCodec.encode(StudioMultiTrackProject(many, 0L))).tracks.size)
        val project = StudioMultiTrackProject(
            listOf(StudioTimelineTrack("v", "Video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("shot", "content://video/a", 2_000L, 0L, 3_000L)
            ))), 0L
        )
        assertEquals(5_000L, StudioMultiTrackCodec.decode(StudioMultiTrackCodec.encode(project)).durationMs)
    }
}

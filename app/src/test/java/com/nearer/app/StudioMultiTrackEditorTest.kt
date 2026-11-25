package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMultiTrackEditorTest {
    private fun project() = StudioMultiTrackProject(
        tracks = listOf(
            StudioTimelineTrack("video-main", "Main video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("clip-a", "content://video/a", 0L, 0L, 5_000L),
                StudioTimelineItem("clip-b", "content://video/b", 5_000L, 500L, 3_500L)
            )),
            StudioTimelineTrack("audio-music", "Music", StudioTrackKind.AUDIO, items = listOf(
                StudioTimelineItem("music", "content://audio/music", 0L, 0L, 10_000L)
            ))
        ), durationMs = 10_000L
    )

    @Test fun buildsSeparateSequentialVideoAndIndependentMusicLanes() {
        val result = StudioMultiTrackEditor.fromVideoSources(
            listOf(
                StudioTimelineItem("a", "content://video/a", 999L, 500L, 2_500L),
                StudioTimelineItem("b", "content://video/b", 999L, 0L, 4_000L)
            ),
            "content://audio/music", 8_000L
        )
        assertEquals(0L, result.tracks[0].items[0].timelineStartMs)
        assertEquals(2_000L, result.tracks[0].items[1].timelineStartMs)
        assertEquals(0L, result.tracks[1].items.single().timelineStartMs)
        assertEquals(8_000L, result.durationMs)
    }

    @Test fun movingAndTrimmingItemsUpdatesProjectDuration() {
        val moved = StudioMultiTrackEditor.moveItem(project(), "video-main", "clip-b", 8_000L)!!
        assertEquals(11_000L, moved.durationMs)
        val trimmed = StudioMultiTrackEditor.trimItem(moved, "video-main", "clip-b", 1_000L, 2_000L)!!
        assertEquals(9_000L, trimmed.durationMs)
        assertEquals(1_000L, trimmed.tracks[0].items[1].sourceInMs)
    }

    @Test fun lockedTracksRejectDestructiveEditsButCanBeUnlocked() {
        val locked = StudioMultiTrackEditor.setLocked(project(), "video-main", true)!!
        assertNull(StudioMultiTrackEditor.moveItem(locked, "video-main", "clip-a", 1_000L))
        assertNull(StudioMultiTrackEditor.trimItem(locked, "video-main", "clip-a", 0L, 1_000L))
        val unlocked = StudioMultiTrackEditor.setLocked(locked, "video-main", false)!!
        assertTrue(StudioMultiTrackEditor.moveItem(unlocked, "video-main", "clip-a", 1_000L) != null)
    }

    @Test fun muteAndRemoveOperationsAreLaneSpecific() {
        val muted = StudioMultiTrackEditor.setMuted(project(), "audio-music", true)!!
        assertTrue(muted.tracks.first { it.id == "audio-music" }.muted)
        val removed = StudioMultiTrackEditor.removeItem(muted, "audio-music", "music")!!
        assertTrue(removed.tracks.first { it.id == "audio-music" }.items.isEmpty())
        assertEquals(8_000L, removed.durationMs)
    }

    @Test fun invalidMovesAndLockedRemovalsAreRejected() {
        assertNull(StudioMultiTrackEditor.moveItem(project(), "video-main", "clip-a", -1L))
        val locked = StudioMultiTrackEditor.setLocked(project(), "video-main", true)!!
        assertNull(StudioMultiTrackEditor.removeItem(locked, "video-main", "clip-a"))
        assertFalse(StudioMultiTrackEditor.setMuted(project(), "missing", true) != null)
    }
}

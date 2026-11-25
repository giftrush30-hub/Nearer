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
        assertEquals(10_000L, trimmed.durationMs)
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

    @Test fun canAddNamedTrackAndRejectsUnsafeTrackGrowth() {
        val expanded = StudioMultiTrackEditor.addTrack(project(), StudioTrackKind.TEXT, "Opening title")!!
        assertEquals(3, expanded.tracks.size)
        assertNull(StudioMultiTrackEditor.addTrack(expanded, StudioTrackKind.TEXT, ""))
        val full = expanded.copy(tracks = (1..32).map { StudioTimelineTrack("t$it", "Track $it", StudioTrackKind.TEXT) })
        assertNull(StudioMultiTrackEditor.addTrack(full, StudioTrackKind.EFFECT, "Too many"))
    }

    @Test fun rejectsEditsThatWouldOverflowTheMaximumTimeline() {
        val nearEnd = StudioMultiTrackProject(
            listOf(StudioTimelineTrack("v", "Video", StudioTrackKind.VIDEO, items = listOf(
                StudioTimelineItem("last", "content://video/last", 86_399_000L, 0L, 2_000L)
            ))), 86_401_000L
        )
        assertNull(StudioMultiTrackEditor.moveItem(nearEnd, "v", "last", 86_400_000L))
    }

    @Test fun invalidMovesAndLockedRemovalsAreRejected() {
        assertNull(StudioMultiTrackEditor.moveItem(project(), "video-main", "clip-a", -1L))
        val locked = StudioMultiTrackEditor.setLocked(project(), "video-main", true)!!
        assertNull(StudioMultiTrackEditor.removeItem(locked, "video-main", "clip-a"))
        assertFalse(StudioMultiTrackEditor.setMuted(project(), "missing", true) != null)
    }

    @Test fun splittingPreservesTotalDurationAndSourceRanges() {
        val split = StudioMultiTrackEditor.splitItem(project(), "video-main", "clip-a", 2_000L)!!
        val items = split.tracks.first { it.id == "video-main" }.items
        assertEquals(3, items.size)
        assertEquals(2_000L, items[0].durationMs)
        assertEquals(3_000L, items[1].durationMs)
        assertEquals(2_000L, items[1].sourceInMs)
        assertEquals(10_000L, split.durationMs)
    }

    @Test fun splitAndDuplicateRejectInvalidOrLockedEdits() {
        assertNull(StudioMultiTrackEditor.splitItem(project(), "video-main", "clip-a", 0L))
        val locked = StudioMultiTrackEditor.setLocked(project(), "video-main", true)!!
        assertNull(StudioMultiTrackEditor.splitItem(locked, "video-main", "clip-a", 2_000L))
        assertNull(StudioMultiTrackEditor.duplicateItem(locked, "video-main", "clip-a", 0L))
    }

    @Test fun duplicateCreatesIndependentTimelineInstanceWithoutChangingSource() {
        val duplicated = StudioMultiTrackEditor.duplicateItem(project(), "video-main", "clip-a", 8_000L)!!
        val items = duplicated.tracks.first { it.id == "video-main" }.items
        assertEquals(3, items.size)
        assertEquals("content://video/a", items.last().sourceUri)
        assertEquals(8_000L, items.last().timelineStartMs)
        assertTrue(items.last().id != "clip-a")
        assertEquals(13_000L, duplicated.durationMs)
    }

    @Test fun itemControlsEnforceOpacityVolumeAndTrackKinds() {
        val opacity = StudioMultiTrackEditor.setItemOpacity(project(), "video-main", "clip-a", 0.4f)!!
        assertEquals(0.4f, opacity.tracks[0].items[0].opacity, 0.001f)
        assertNull(StudioMultiTrackEditor.setItemOpacity(opacity, "video-main", "clip-a", 1.5f))
        val volume = StudioMultiTrackEditor.setItemVolume(project(), "audio-music", "music", 0.6f)!!
        assertEquals(0.6f, volume.tracks[1].items[0].volume, 0.001f)
        assertNull(StudioMultiTrackEditor.setItemVolume(project(), "video-main", "clip-a", 0.5f))
    }

    @Test fun tracksCanBeRenamedAndReorderedSafely() {
        val renamed = StudioMultiTrackEditor.renameTrack(project(), "audio-music", "Dialogue")!!
        assertEquals("Dialogue", renamed.tracks[1].name)
        assertNull(StudioMultiTrackEditor.renameTrack(renamed, "audio-music", " "))
        val reordered = StudioMultiTrackEditor.reorderTrack(renamed, "audio-music", 0)!!
        assertEquals("audio-music", reordered.tracks[0].id)
        assertNull(StudioMultiTrackEditor.reorderTrack(reordered, "missing", 0))
    }

}

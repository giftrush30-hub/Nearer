package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVideoProjectCodecTest {
    @Test fun restoresClipOrderTrimPointsAndMusic() {
        val original = StudioVideoProjectState(
            clips = listOf(
                StudioVideoClipState("content://video/a", "Opening", 10_000L, 500L, 8_500L),
                StudioVideoClipState("content://video/b", "Ending", 6_000L, 0L, 6_000L)
            ),
            backgroundAudioUri = "content://audio/music",
            backgroundAudioName = "Score"
        )
        assertEquals(original, StudioVideoProjectCodec.decode(StudioVideoProjectCodec.encode(original)))
    }

    @Test fun clampsInvalidTrimRangesAndSkipsBrokenClips() {
        val raw = """{"version":1,"clips":[{"uri":"content://video/a","durationMs":5000,"inMs":9000,"outMs":1},{"name":"no uri"},{"uri":"content://video/b","durationMs":200}]}"""
        val restored = StudioVideoProjectCodec.decode(raw)
        assertEquals(1, restored.clips.size)
        assertEquals(4_500L, restored.clips.single().inMs)
        assertEquals(5_000L, restored.clips.single().outMs)
    }

    @Test fun rejectsUnknownVersionsAndLimitsClipCount() {
        assertTrue(StudioVideoProjectCodec.decode("""{"version":9,"clips":[]}""").clips.isEmpty())
        val clips = (1..15).map { StudioVideoClipState("content://video/$it", "Clip $it", 1_000L, 0L, 1_000L) }
        assertEquals(12, StudioVideoProjectCodec.decode(StudioVideoProjectCodec.encode(StudioVideoProjectState(clips, null, ""))).clips.size)
    }
}

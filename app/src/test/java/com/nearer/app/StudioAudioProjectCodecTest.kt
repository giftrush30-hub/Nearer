package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioProjectCodecTest {
    @Test fun restoresEveryTrackControl() {
        val original = listOf(StudioAudioProjectTrackState(
            uri = "content://audio/track-one",
            name = "Lead vocal",
            durationMs = 125_000L,
            muted = true,
            gain = 0.72f,
            pan = -0.35f,
            fadeInMs = 1_500L,
            fadeOutMs = 2_000L
        ))
        val restored = StudioAudioProjectCodec.decode(StudioAudioProjectCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test fun clampsUnsafeValuesAndSkipsInvalidEntries() {
        val raw = """{"version":1,"tracks":[{"uri":"content://audio/a","gain":2.5,"durationMs":-10},{"name":"missing uri"}]}"""
        val restored = StudioAudioProjectCodec.decode(raw)
        assertEquals(1, restored.size)
        assertEquals(1f, restored.single().gain, 0f)
        assertEquals(0L, restored.single().durationMs)
        assertEquals(0f, restored.single().pan, 0f)
    }

    @Test fun migratesVersionOneProjectsWithCentredPan() {
        val raw = """{"version":1,"tracks":[{"uri":"content://audio/legacy","name":"Legacy","gain":0.8}]}"""
        val restored = StudioAudioProjectCodec.decode(raw)
        assertEquals(0f, restored.single().pan, 0f)
    }

    @Test fun ignoresUnknownProjectVersions() {
        assertTrue(StudioAudioProjectCodec.decode("""{"version":99,"tracks":[]}""").isEmpty())
    }
}

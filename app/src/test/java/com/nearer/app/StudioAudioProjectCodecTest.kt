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
            fadeOutMs = 2_000L,
            lowGainDb = -3f,
            midGainDb = 2f,
            highGainDb = 5f,
            reverbMix = 0.2f
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

    @Test fun legacyProjectsDefaultToFlatDryProcessing() {
        val raw = """{"version":2,"tracks":[{"uri":"content://audio/old","name":"Old track"}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertEquals(0f, restored.lowGainDb, 0f)
        assertEquals(0f, restored.midGainDb, 0f)
        assertEquals(0f, restored.highGainDb, 0f)
        assertEquals(0f, restored.reverbMix, 0f)
    }

    @Test fun clampsNewEffectSettings() {
        val raw = """{"version":3,"tracks":[{"uri":"content://audio/unsafe","lowGainDb":30,"midGainDb":-30,"highGainDb":18,"reverbMix":1.2}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertEquals(12f, restored.lowGainDb, 0f)
        assertEquals(-12f, restored.midGainDb, 0f)
        assertEquals(12f, restored.highGainDb, 0f)
        assertEquals(0.35f, restored.reverbMix, 0f)
    }

    @Test fun ignoresUnknownProjectVersions() {
        assertTrue(StudioAudioProjectCodec.decode("""{"version":99,"tracks":[]}""").isEmpty())
    }
}

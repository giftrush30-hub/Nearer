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
            reverbMix = 0.2f,
            clipStartMs = 12_000L,
            clipEndMs = 98_000L,
            startOffsetMs = 4_500L
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

    @Test fun migratesVersionThreeProjectsWithFullSourceRangeAndNoOffset() {
        val raw = """{"version":3,"tracks":[{"uri":"content://audio/old","durationMs":50000}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertEquals(0L, restored.clipStartMs)
        assertEquals(50_000L, restored.clipEndMs)
        assertEquals(0L, restored.startOffsetMs)
    }

    @Test fun extremeImportedTrimTimestampsDoNotOverflow() {
        val raw = org.json.JSONObject()
            .put("version", 5)
            .put("tracks", org.json.JSONArray().put(org.json.JSONObject()
                .put("uri", "content://audio/extreme")
                .put("durationMs", Long.MAX_VALUE)
                .put("clipStartMs", Long.MAX_VALUE)
                .put("clipEndMs", Long.MIN_VALUE)
            )).toString()
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertEquals(Long.MAX_VALUE, restored.durationMs)
        assertEquals(Long.MAX_VALUE, restored.clipStartMs)
        assertEquals(Long.MAX_VALUE, restored.clipEndMs)
    }

    @Test fun clampsTimelineValuesToSourceAndThirtySecondOffsetLimit() {
        val raw = """{"version":4,"tracks":[{"uri":"content://audio/x","durationMs":10000,"clipStartMs":9000,"clipEndMs":20000,"startOffsetMs":90000}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertEquals(9_000L, restored.clipStartMs)
        assertEquals(10_000L, restored.clipEndMs)
        assertEquals(30_000L, restored.startOffsetMs)
    }

    @Test fun persistsTempoAutomationKeyframes() {
        val original = listOf(StudioAudioProjectTrackState(
            uri = "content://audio/tempo",
            name = "Tempo vocal",
            durationMs = 20_000L,
            muted = false,
            gain = 1f,
            pan = 0f,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            tempoPoints = listOf(StudioTempoPoint(0L, 0.75f), StudioTempoPoint(5_000L, 1.5f))
        ))
        val restored = StudioAudioProjectCodec.decode(StudioAudioProjectCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test fun oversizedTempoArraysKeepTheFinalAutomationIntent() {
        val points = (0..39).joinToString(",") { index ->
            """{"timeMs":${index * 1_000L},"speed":${0.5 + index * 0.02}}"""
        }
        val raw = """{"version":5,"tracks":[{"uri":"content://audio/tempo-long","durationMs":30000,"tempoPoints":[$points]}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertTrue(restored.tempoPoints.size <= StudioTempoAutomationPolicy.MAX_KEYFRAMES)
        assertEquals(30_000L, restored.tempoPoints.last().timeMs)
        assertEquals(1.28f, restored.tempoPoints.last().speed, 0.0001f)
    }

    @Test fun versionFourProjectsMigrateWithoutTempoAutomation() {
        val raw = """{"version":4,"tracks":[{"uri":"content://audio/legacy","durationMs":10000}]}"""
        val restored = StudioAudioProjectCodec.decode(raw).single()
        assertTrue(restored.tempoPoints.isEmpty())
    }

    @Test fun ignoresUnknownProjectVersions() {
        assertTrue(StudioAudioProjectCodec.decode("""{"version":99,"tracks":[]}""").isEmpty())
    }
}

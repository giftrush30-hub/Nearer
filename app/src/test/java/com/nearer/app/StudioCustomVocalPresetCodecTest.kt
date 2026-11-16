package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioCustomVocalPresetCodecTest {
    @Test fun roundTripKeepsNameProfileAndControls() {
        val preset = StudioCustomVocalPreset("My Studio Voice", StudioVocalProfile.LEAD_VOCAL,
            StudioVocalControls(warmth = 0.4f, air = 0.2f, outputGain = 1.1f))
        val restored = StudioCustomVocalPresetCodec.decode(StudioCustomVocalPresetCodec.encode(listOf(preset)))
        assertEquals(listOf(preset), restored)
    }

    @Test fun namesAreTrimmedBoundedAndCaseInsensitiveUpsertReplaces() {
        val first = StudioCustomVocalPresetCodec.upsert(emptyList(), "  My Voice  ", StudioVocalProfile.CLEAN,
            StudioVocalControls())!!
        val updated = StudioCustomVocalPresetCodec.upsert(first, "my voice", StudioVocalProfile.PODCAST,
            StudioVocalControls(air = 0.3f))!!
        assertEquals(1, updated.size)
        assertEquals(StudioVocalProfile.PODCAST, updated.single().profile)
        assertEquals(StudioCustomVocalPresetCodec.MAX_NAME_LENGTH,
            StudioCustomVocalPresetCodec.normalizedName("A".repeat(60)).length)
        assertNull(StudioCustomVocalPresetCodec.upsert(emptyList(), "  ", StudioVocalProfile.CLEAN, StudioVocalControls()))
    }

    @Test fun storageIsCappedAtTwelvePresets() {
        var presets = emptyList<StudioCustomVocalPreset>()
        repeat(20) { index ->
            presets = StudioCustomVocalPresetCodec.upsert(presets, "Preset $index", StudioVocalProfile.CLEAN,
                StudioVocalControls())!!
        }
        assertEquals(StudioCustomVocalPresetCodec.MAX_PRESETS, presets.size)
        assertEquals("Preset 19", presets.last().name)
    }

    @Test fun malformedOversizedAndUnknownVersionsResetSafely() {
        assertTrue(StudioCustomVocalPresetCodec.decode("{bad").isEmpty())
        assertTrue(StudioCustomVocalPresetCodec.decode("x".repeat(20_000)).isEmpty())
        assertTrue(StudioCustomVocalPresetCodec.decode("""{"version":8,"presets":[]}""").isEmpty())
    }
}

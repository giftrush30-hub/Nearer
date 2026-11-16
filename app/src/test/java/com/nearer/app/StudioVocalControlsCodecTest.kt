package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVocalControlsCodecTest {
    @Test
    fun roundTripPreservesManualControls() {
        val controls = StudioVocalControls(
            warmth = 0.45f,
            presence = 0.12f,
            deEss = 0.3f,
            compression = 2.5f,
            ambience = 0.08f,
            outputGain = 1.15f,
            air = 0.28f,
            stereoWidth = 1.25f,
            limiterCeiling = 0.88f,
            noiseReduction = 0.45f
        )
        val restored = StudioVocalControlsCodec.decode(StudioVocalControlsCodec.encode(controls))
        assertEquals(controls, restored)
    }

    @Test
    fun clampsUnsafeSettingsToSafeRanges() {
        val safe = StudioVocalControls(
            warmth = 4f,
            presence = -2f,
            deEss = 3f,
            compression = 20f,
            ambience = 2f,
            outputGain = 5f,
            air = 3f,
            stereoWidth = 3f,
            limiterCeiling = 0.1f,
            noiseReduction = 3f
        ).normalized()
        assertEquals(1f, safe.warmth)
        assertEquals(-0.2f, safe.presence)
        assertEquals(0.5f, safe.deEss)
        assertEquals(4f, safe.compression)
        assertEquals(0.15f, safe.ambience)
        assertEquals(1.4f, safe.outputGain)
        assertEquals(0.5f, safe.air)
        assertEquals(1.5f, safe.stereoWidth)
        assertEquals(0.75f, safe.limiterCeiling)
        assertEquals(1f, safe.noiseReduction)
    }

    @Test
    fun defaultsRemainNeutral() {
        val defaults = StudioVocalControlsCodec.decode(null)
        assertEquals(StudioVocalControls(), defaults)
        assertTrue(defaults.outputGain == 1f)
    }

    @Test
    fun versionOnePreferencesRemainReadable() {
        val legacy = StudioVocalControlsCodec.decode("""{"version":1,"warmth":0.4,"outputGain":1.1}""")
        assertEquals(0.4f, legacy.warmth)
        assertEquals(1.1f, legacy.outputGain)
        assertEquals(0f, legacy.air)
        assertEquals(1f, legacy.stereoWidth)
        assertEquals(0.95f, legacy.limiterCeiling)
    }

    @Test
    fun malformedAndUnknownVersionsResetSafely() {
        assertEquals(StudioVocalControls(), StudioVocalControlsCodec.decode("{broken"))
        assertEquals(StudioVocalControls(), StudioVocalControlsCodec.decode("""{"version":8}"""))
    }
}

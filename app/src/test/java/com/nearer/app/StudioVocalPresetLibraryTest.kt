package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVocalPresetLibraryTest {
    @Test
    fun everyPresetHasBoundedControls() {
        StudioVocalProfile.values().forEach { profile ->
            val controls = StudioVocalPresetLibrary.controlsFor(profile)
            assertEquals(controls, controls.normalized())
            assertTrue(controls.outputGain in 0.6f..1.4f)
            assertTrue(controls.warmth in -1f..1f)
            assertTrue(controls.presence in -0.2f..0.25f)
            assertTrue(controls.deEss in 0f..0.5f)
            assertTrue(controls.compression in 0f..4f)
            assertTrue(controls.ambience in 0f..0.15f)
            assertTrue(controls.air in 0f..0.5f)
            assertTrue(controls.stereoWidth in 0f..1.5f)
            assertTrue(controls.limiterCeiling in 0.75f..1f)
            assertTrue(controls.noiseReduction in 0f..1f)
        }
    }

    @Test
    fun presetsProvideDistinctStartingPoints() {
        val clean = StudioVocalPresetLibrary.controlsFor(StudioVocalProfile.CLEAN)
        val warm = StudioVocalPresetLibrary.controlsFor(StudioVocalProfile.WARM_CONDENSER)
        val podcast = StudioVocalPresetLibrary.controlsFor(StudioVocalProfile.PODCAST)
        assertTrue(clean != warm)
        assertTrue(warm != podcast)
        assertTrue(clean.ambience < warm.ambience)
    }

    @Test
    fun narrationAndLeadVocalPresetsAreAvailable() {
        assertTrue(StudioVocalProfile.values().contains(StudioVocalProfile.CLEAN_NARRATION))
        assertTrue(StudioVocalProfile.values().contains(StudioVocalProfile.LEAD_VOCAL))
    }
}

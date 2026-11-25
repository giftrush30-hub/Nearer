package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioChromaKeySettingsTest {
    @Test fun acceptsUsableGreenAndBlueKeySettings() {
        assertTrue(StudioChromaKeySettings().isValid)
        assertTrue(StudioChromaKeySettings(keyColorArgb = 0xFF0000FF.toInt(),
            tolerance = 0.3f, edgeSoftness = 0.08f, despill = 0.4f).isValid)
    }

    @Test fun rejectsInvalidToleranceSoftnessAndDespill() {
        assertFalse(StudioChromaKeySettings(tolerance = -0.1f).isValid)
        assertFalse(StudioChromaKeySettings(edgeSoftness = 0f).isValid)
        assertFalse(StudioChromaKeySettings(despill = 1.1f).isValid)
        assertFalse(StudioChromaKeySettings(tolerance = Float.NaN).isValid)
    }
}

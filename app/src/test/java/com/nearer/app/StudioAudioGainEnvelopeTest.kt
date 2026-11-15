package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioAudioGainEnvelopeTest {
    @Test fun unityGainWithoutFadesStaysConstant() {
        assertEquals(1f, studioAudioGainAtFrame(500, 1_000, 1f, 0, 0), 0.0001f)
    }

    @Test fun gainIsAppliedAlongsideFadeIn() {
        assertEquals(0.25f, studioAudioGainAtFrame(250, 2_000, 0.5f, 500, 0), 0.0001f)
        assertEquals(0.5f, studioAudioGainAtFrame(500, 2_000, 0.5f, 500, 0), 0.0001f)
    }

    @Test fun fadeOutReachesSilenceAtEnd() {
        assertEquals(0.5f, studioAudioGainAtFrame(1_500, 2_000, 1f, 0, 1_000), 0.0001f)
        assertEquals(0f, studioAudioGainAtFrame(2_000, 2_000, 1f, 0, 1_000), 0.0001f)
    }

    @Test fun panKeepsCentreBalancedAndMovesSignalLeftOrRight() {
        assertEquals(1f to 1f, studioAudioPanFactors(0f))
        assertEquals(1f to 0f, studioAudioPanFactors(-1f))
        assertEquals(0f to 1f, studioAudioPanFactors(1f))
        assertEquals(1f to 0.5f, studioAudioPanFactors(-0.5f))
    }

    @Test fun gainNeverExceedsUnity() {
        assertEquals(1f, studioAudioGainAtFrame(10, 100, 1.8f, 0, 0), 0.0001f)
    }
}

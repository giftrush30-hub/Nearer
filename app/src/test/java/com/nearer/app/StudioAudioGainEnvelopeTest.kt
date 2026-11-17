package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StudioAudioGainEnvelopeTest {
    @Test fun unityGainWithoutFadesStaysConstant() {
        assertEquals(1f, studioAudioGainAtFrame(500, 1_000, 1f, 0, 0), 0.0001f)
    }

    @Test fun gainIsAppliedAlongsideEqualPowerFadeIn() {
        val expected = 0.5f * sin(PI / 8.0).toFloat()
        assertEquals(expected, studioAudioGainAtFrame(250, 2_000, 0.5f, 500, 0), 0.0001f)
        assertEquals(0.5f, studioAudioGainAtFrame(500, 2_000, 0.5f, 500, 0), 0.0001f)
    }

    @Test fun fadeOutUsesComplementaryCosineCurveAndReachesSilenceAtEnd() {
        assertEquals(cos(PI / 4.0).toFloat(),
            studioAudioGainAtFrame(1_500, 2_000, 1f, 0, 1_000), 0.0001f)
        assertEquals(0f, studioAudioGainAtFrame(2_000, 2_000, 1f, 0, 1_000), 0.0001f)
    }

    @Test fun alignedFadeInAndFadeOutMaintainEqualPowerAcrossOverlap() {
        val overlapFrames = 10_000L
        val outgoingDuration = 30_000L
        for (step in 0..100) {
            val progressFrame = overlapFrames * step / 100L
            val outgoing = studioAudioGainAtFrame(
                frame = outgoingDuration - overlapFrames + progressFrame,
                totalFrames = outgoingDuration,
                baseGain = 1f,
                fadeInFrames = 0L,
                fadeOutFrames = overlapFrames
            )
            val incoming = studioAudioGainAtFrame(
                frame = progressFrame,
                totalFrames = overlapFrames,
                baseGain = 1f,
                fadeInFrames = overlapFrames,
                fadeOutFrames = 0L
            )
            val combinedPower = outgoing * outgoing + incoming * incoming
            assertTrue("Equal-power overlap drifted at step $step: $combinedPower",
                abs(combinedPower - 1f) < 0.0002f)
        }
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

    @Test fun invalidFrameAndFadeInputsRemainBounded() {
        assertEquals(0f, studioAudioGainAtFrame(-100, 100, 1f, 500, 0), 0.0001f)
        assertEquals(0f, studioAudioGainAtFrame(100, 100, 1f, 0, 500), 0.0001f)
        assertTrue(studioAudioGainAtFrame(10, 100, 0.8f, 20, 30) in 0f..0.8f)
    }
}

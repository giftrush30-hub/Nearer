package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioPitchCorrectionMathTest {
    @Test fun majorScaleChoosesNearestLegalNote() {
        assertEquals(64, StudioPitchCorrectionMath.targetMidi(63.7f, 0, false))
        assertEquals(65, StudioPitchCorrectionMath.targetMidi(64.8f, 0, false))
    }

    @Test fun minorScaleUsesMinorThirdAndSeventh() {
        assertEquals(63, StudioPitchCorrectionMath.targetMidi(62.8f, 0, true))
        assertEquals(70, StudioPitchCorrectionMath.targetMidi(69.2f, 0, true))
    }

    @Test fun naturalAndHardStylesApplyDifferentCorrectionStrength() {
        val natural = StudioPitchCorrectionMath.pitchRatio(69f, 70, StudioTuningStyle.NATURAL.strength)
        val hard = StudioPitchCorrectionMath.pitchRatio(69f, 70, StudioTuningStyle.HARD.strength)
        assertEquals(1.043f, natural, 0.002f)
        assertEquals(1.059f, hard, 0.002f)
    }

    @Test fun zeroRetuneSpeedDisablesSmoothing() {
        assertEquals(0f, StudioPitchCorrectionMath.smoothingCoefficient(44_100, 512, 0), 0f)
    }

    @Test fun slowerRetuneSpeedUsesMoreSmoothing() {
        val fast = StudioPitchCorrectionMath.smoothingCoefficient(44_100, 512, 20)
        val slow = StudioPitchCorrectionMath.smoothingCoefficient(44_100, 512, 180)
        assert(slow > fast)
    }
}

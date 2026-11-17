package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalApi::class, UnstableApi::class)
class StudioTempoAutomationPolicyTest {
    @Test fun normalizationSortsSnapsClampsAndDeduplicatesKeyframes() {
        val points = StudioTempoAutomationPolicy.normalize(
            listOf(
                StudioTempoPoint(199, 2.8f),
                StudioTempoPoint(0, 1f),
                StudioTempoPoint(201, 1.25f),
                StudioTempoPoint(-40, Float.NaN)
            ),
            durationMs = 1_000
        )
        assertEquals(listOf(0L, 200L), points.map { it.timeMs })
        assertEquals(1f, points.first().speed, 0.0001f)
        assertEquals(1.25f, points.last().speed, 0.0001f)
    }

    @Test fun insertsNeutralStartWhenFirstKeyframeStartsLater() {
        val points = StudioTempoAutomationPolicy.normalize(
            listOf(StudioTempoPoint(500, 1.5f)),
            durationMs = 2_000
        )
        assertEquals(StudioTempoPoint(0, 1f), points.first())
        assertEquals(StudioTempoPoint(500, 1.5f), points.last())
    }

    @Test fun linearInterpolationClampsOutsideTheKeyframeRange() {
        val points = listOf(StudioTempoPoint(0, 0.75f), StudioTempoPoint(1_000, 1.75f))
        assertEquals(0.75f, StudioTempoAutomationPolicy.speedAt(points, -100), 0.0001f)
        assertEquals(1.25f, StudioTempoAutomationPolicy.speedAt(points, 500), 0.0001f)
        assertEquals(1.75f, StudioTempoAutomationPolicy.speedAt(points, 2_000), 0.0001f)
    }

    @Test fun twoPointRampHandlesNeutralConstantAndChangingSpeeds() {
        assertTrue(StudioTempoAutomationPolicy.twoPointRamp(1f, 1f, 5_000L).isEmpty())
        assertEquals(
            listOf(StudioTempoPoint(0L, 1.25f)),
            StudioTempoAutomationPolicy.twoPointRamp(1.25f, 1.25f, 5_000L)
        )
        assertEquals(
            listOf(StudioTempoPoint(0L, 0.75f), StudioTempoPoint(5_000L, 1.5f)),
            StudioTempoAutomationPolicy.twoPointRamp(0.75f, 1.5f, 5_000L)
        )
    }

    @Test fun providerSamplesRampOnTwentyMillisecondBoundaries() {
        val provider = StudioTempoAutomationSpeedProvider(
            listOf(StudioTempoPoint(0, 1f), StudioTempoPoint(200, 2f)),
            durationMs = 1_000
        )
        assertEquals(1f, provider.getSpeed(0), 0.0001f)
        assertEquals(1.1f, provider.getSpeed(20_000), 0.0001f)
        assertEquals(1.2f, provider.getSpeed(40_000), 0.0001f)
        assertEquals(1.5f, provider.getSpeed(100_000), 0.0001f)
        assertEquals(1.9f, provider.getSpeed(180_000), 0.0001f)
        assertEquals(2f, provider.getSpeed(200_000), 0.0001f)
        assertEquals(20_000L, provider.getNextSpeedChangeTimeUs(0))
        assertEquals(40_000L, provider.getNextSpeedChangeTimeUs(20_000))
        assertEquals(60_000L, provider.getNextSpeedChangeTimeUs(40_000))
        assertEquals(200_000L, provider.getNextSpeedChangeTimeUs(180_000))
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(200_000))
    }

    @Test fun outputDurationUsesTheSameSampledTempoCurve() {
        val points = listOf(StudioTempoPoint(0, 1f), StudioTempoPoint(200, 2f))
        assertEquals(200L, StudioTempoAutomationPolicy.outputDurationMs(emptyList(), 200L))
        assertEquals(144L, StudioTempoAutomationPolicy.outputDurationMs(points, 200L))
        assertEquals(0L, StudioTempoAutomationPolicy.outputDurationMs(points, 0L))
    }

    @Test fun providerSkipsFlatSectionsUntilTheCurveActuallyChanges() {
        val provider = StudioTempoAutomationSpeedProvider(
            listOf(
                StudioTempoPoint(0, 1f),
                StudioTempoPoint(200, 1f),
                StudioTempoPoint(300, 2f)
            ),
            durationMs = 1_000
        )
        assertEquals(220_000L, provider.getNextSpeedChangeTimeUs(0))
        assertEquals(220_000L, provider.getNextSpeedChangeTimeUs(200_000))
        assertEquals(240_000L, provider.getNextSpeedChangeTimeUs(220_000))
        assertEquals(300_000L, provider.getNextSpeedChangeTimeUs(280_000))
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(300_000))
    }

    @Test fun automationWindowCapsAtThirtySecondsAndHoldsTheLastSpeedAfterward() {
        val ramp = StudioTempoAutomationPolicy.twoPointRamp(1f, 2f, 60_000L)
        assertEquals(30_000L, ramp.last().timeMs)
        val outputDuration = StudioTempoAutomationPolicy.outputDurationMs(ramp, 60_000L)
        assertEquals(35_799L, outputDuration)

        // The ramp ends at 30 seconds; the final speed must remain active for the rest.
        val provider = StudioTempoAutomationSpeedProvider(ramp, durationMs = 60_000L)
        assertEquals(2f, provider.getSpeed(30_000_000L), 0.0001f)
        assertEquals(2f, provider.getSpeed(45_000_000L), 0.0001f)
    }

    @Test fun constantTempoDoesNotScheduleUnnecessaryChanges() {
        val provider = StudioTempoAutomationSpeedProvider(
            listOf(StudioTempoPoint(0, 1f), StudioTempoPoint(1_000, 1f)),
            durationMs = 1_000
        )
        assertEquals(1f, provider.getSpeed(500_000), 0.0001f)
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(0))
    }

    @Test fun automationIsBoundedToThirtySecondsAndThirtyTwoKeyframes() {
        val raw = (0..50).map { index -> StudioTempoPoint(index * 1_000L, 0.5f + index / 30f) }
        val points = StudioTempoAutomationPolicy.normalize(raw, durationMs = 180_000)
        assertTrue(points.size <= StudioTempoAutomationPolicy.MAX_KEYFRAMES)
        assertTrue(points.all { it.timeMs in 0L..StudioTempoAutomationPolicy.MAX_AUTOMATION_MS })
        assertTrue(points.all { it.speed in StudioTempoAutomationPolicy.MIN_SPEED..StudioTempoAutomationPolicy.MAX_SPEED })
    }

    @Test fun speedParametersExplicitlyPreservePitch() {
        val parameters = StudioTempoAutomationPolicy.speedParameters(
            listOf(StudioTempoPoint(0, 1f), StudioTempoPoint(500, 1.5f)),
            durationMs = 1_000
        )
        assertTrue(parameters.shouldMaintainPitch)
        assertEquals(1.24f, parameters.speedProvider.getSpeed(240_000), 0.0001f)
    }
}

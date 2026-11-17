package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
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

    @Test fun providerSamplesRampOnFiftyMillisecondBoundaries() {
        val provider = StudioTempoAutomationSpeedProvider(
            listOf(StudioTempoPoint(0, 1f), StudioTempoPoint(200, 2f)),
            durationMs = 1_000
        )
        assertEquals(1f, provider.getSpeed(0), 0.0001f)
        assertEquals(1.25f, provider.getSpeed(50_000), 0.0001f)
        assertEquals(1.5f, provider.getSpeed(100_000), 0.0001f)
        assertEquals(1.75f, provider.getSpeed(150_000), 0.0001f)
        assertEquals(2f, provider.getSpeed(200_000), 0.0001f)
        assertEquals(50_000L, provider.getNextSpeedChangeTimeUs(0))
        assertEquals(100_000L, provider.getNextSpeedChangeTimeUs(50_000))
        assertEquals(150_000L, provider.getNextSpeedChangeTimeUs(100_000))
        assertEquals(200_000L, provider.getNextSpeedChangeTimeUs(150_000))
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(200_000))
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
        assertEquals(1.25f, parameters.speedProvider.getSpeed(250_000), 0.0001f)
    }
}

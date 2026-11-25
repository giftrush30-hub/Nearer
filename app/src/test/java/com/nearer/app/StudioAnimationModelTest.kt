package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAnimationModelTest {
    @Test fun evaluatesLinearKeyframesAtIntermediateTime() {
        val animation = StudioPropertyAnimation(StudioAnimatedProperty.X, listOf(
            StudioKeyframe(0L, 0f), StudioKeyframe(1_000L, 1f)
        ))
        assertTrue(StudioKeyframeEvaluator.validate(animation).isValid)
        assertEquals(0.25f, animation.valueAt(250L, -1f), 0.0001f)
        assertEquals(0.5f, animation.valueAt(500L, -1f), 0.0001f)
    }

    @Test fun supportsHoldAndSmoothstepInterpolation() {
        val hold = listOf(
            StudioKeyframe(0L, 0f, StudioKeyframeInterpolation.HOLD),
            StudioKeyframe(1_000L, 1f)
        )
        assertEquals(0f, StudioKeyframeEvaluator.valueAt(hold, 750L, -1f), 0f)
        val smooth = listOf(
            StudioKeyframe(0L, 0f, StudioKeyframeInterpolation.SMOOTHSTEP),
            StudioKeyframe(1_000L, 1f)
        )
        assertEquals(0.5f, StudioKeyframeEvaluator.valueAt(smooth, 500L, -1f), 0.0001f)
    }

    @Test fun clampsEvaluationToFirstAndLastKeyframeValues() {
        val frames = listOf(StudioKeyframe(100L, 0.2f), StudioKeyframe(200L, 0.8f))
        assertEquals(0.2f, StudioKeyframeEvaluator.valueAt(frames, 0L, -1f), 0f)
        assertEquals(0.8f, StudioKeyframeEvaluator.valueAt(frames, 500L, -1f), 0f)
    }

    @Test fun rejectsUnorderedDuplicateAndNonFiniteKeyframes() {
        val animation = StudioPropertyAnimation(StudioAnimatedProperty.OPACITY, listOf(
            StudioKeyframe(100L, 0f),
            StudioKeyframe(100L, Float.NaN),
            StudioKeyframe(50L, 1f)
        ))
        val validation = StudioKeyframeEvaluator.validate(animation)
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("strictly increasing") })
        assertTrue(validation.errors.any { it.contains("non-finite") })
    }

    @Test fun validatesNormalizedRectangleEllipseAndPolygonMasks() {
        assertTrue(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "rect", StudioMask.Rectangle(0.1f, 0.1f, 0.9f, 0.9f), 0.02f
        )).isValid)
        assertTrue(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "ellipse", StudioMask.Ellipse(0.5f, 0.5f, 0.3f, 0.2f)
        )).isValid)
        assertTrue(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "polygon", StudioMask.Polygon(listOf(
                StudioMask.Point(0.1f, 0.1f), StudioMask.Point(0.9f, 0.1f),
                StudioMask.Point(0.5f, 0.9f)
            ))
        )).isValid)
    }

    @Test fun rejectsOutOfCanvasAndDegenerateMasks() {
        assertFalse(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "bad-rect", StudioMask.Rectangle(0.7f, 0.2f, 0.3f, 0.8f)
        )).isValid)
        assertFalse(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "bad-ellipse", StudioMask.Ellipse(0.9f, 0.5f, 0.3f, 0.2f)
        )).isValid)
        assertFalse(StudioKeyframeEvaluator.validateMask(StudioMaskLayer(
            "bad-polygon", StudioMask.Polygon(listOf(StudioMask.Point(0f, 0f), StudioMask.Point(1.2f, 0f)))
        )).isValid)
    }

    @Test fun enforcesAnimationDurationAndKeyframeCountBounds() {
        val tooLong = StudioPropertyAnimation(StudioAnimatedProperty.X, listOf(
            StudioKeyframe(StudioRenderPlanBuilder.MAX_DURATION_MS + 1L, 1f)
        ))
        assertFalse(StudioKeyframeEvaluator.validate(tooLong).isValid)
        val tooMany = StudioPropertyAnimation(StudioAnimatedProperty.X,
            (0..2_000).map { StudioKeyframe(it.toLong(), it.toFloat()) })
        assertFalse(StudioKeyframeEvaluator.validate(tooMany).isValid)
    }
}

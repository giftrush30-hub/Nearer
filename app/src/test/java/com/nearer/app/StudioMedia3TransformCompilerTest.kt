package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMedia3TransformCompilerTest {
    private fun transform(
        x: Float = 0f,
        y: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotation: Float = 0f,
        opacity: Float = 1f,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 1f,
        bottom: Float = 1f,
        blur: Float = 0f,
        feather: Float = 0f
    ) = StudioVisualTransform(
        x, y, scaleX, scaleY, rotation, opacity,
        left, top, right, bottom, blur, feather
    )

    @Test fun plansCropScaleRotationBlurAndOpacityWithoutAndroidGraphicsRuntime() {
        val result = StudioTransformEffectPlanner.plan(
            transform(scaleX = 1.5f, scaleY = 0.75f, rotation = -90f, opacity = 0.6f,
                left = 0.1f, top = 0.2f, right = 0.9f, bottom = 0.8f, blur = 4f)
        )
        assertTrue(result.isValid)
        val plan = result.plan!!
        assertEquals(StudioCropNdcBounds(-0.8f, 0.8f, -0.6f, 0.6f), plan.crop)
        assertEquals(1.5f, plan.scaleX, 0f)
        assertEquals(0.75f, plan.scaleY, 0f)
        assertEquals(270f, plan.rotationDegrees, 0.0001f)
        assertEquals(4f, plan.blurRadius, 0f)
        assertEquals(0.6f, plan.opacity, 0f)
        assertTrue(plan.needsScaleOrRotation)
        assertTrue(plan.needsBlur)
        assertTrue(plan.needsOpacity)
    }

    @Test fun identityTransformProducesAnEmptyPurePlan() {
        val result = StudioTransformEffectPlanner.plan(transform())
        assertTrue(result.isValid)
        val plan = result.plan!!
        assertNull(plan.crop)
        assertFalse(plan.needsScaleOrRotation)
        assertFalse(plan.needsBlur)
        assertFalse(plan.needsOpacity)
    }

    @Test fun rejectsTranslationAndFeatheredMaskInsteadOfSilentlyDroppingThem() {
        val result = StudioTransformEffectPlanner.plan(transform(x = 0.2f, feather = 0.02f))
        assertFalse(result.isValid)
        assertNull(result.plan)
        assertTrue(result.errors.any { it.contains("translation") })
        assertTrue(result.errors.any { it.contains("Feathered masks") })
    }

    @Test fun rejectsNonFiniteAndOutOfRangeValues() {
        val result = StudioTransformEffectPlanner.plan(transform(scaleX = Float.NaN))
        assertFalse(result.isValid)
        assertTrue(result.errors.single().contains("non-finite"))
        val invalidOpacity = StudioTransformEffectPlanner.plan(transform(opacity = 1.2f))
        assertFalse(invalidOpacity.isValid)
    }

    @Test fun normalizesNegativeRotationIntoMedia3Range() {
        val result = StudioTransformEffectPlanner.plan(transform(rotation = -90f))
        assertTrue(result.isValid)
        assertNotNull(result.plan)
        assertEquals(270f, result.plan!!.rotationDegrees, 0.0001f)
    }
}

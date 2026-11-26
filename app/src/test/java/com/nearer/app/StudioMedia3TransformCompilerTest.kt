package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Crop
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.ScaleAndRotateTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
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

    @Test fun compilesCropScaleRotationBlurAndOpacityToGpuEffects() {
        val result = StudioMedia3TransformCompiler.compile(
            transform(scaleX = 1.5f, scaleY = 0.75f, rotation = -90f, opacity = 0.6f,
                left = 0.1f, top = 0.2f, right = 0.9f, bottom = 0.8f, blur = 4f)
        )
        assertTrue(result.isValid)
        assertEquals(5, result.effects.size)
        assertTrue(result.effects[0] is Crop)
        assertTrue(result.effects[1] is ScaleAndRotateTransformation)
        assertTrue(result.effects[2] is GaussianBlur)
        assertTrue(result.effects[3] is AlphaScale)
    }

    @Test fun identityTransformProducesNoGpuWork() {
        val result = StudioMedia3TransformCompiler.compile(transform())
        assertTrue(result.isValid)
        assertTrue(result.effects.isEmpty())
    }

    @Test fun rejectsTranslationAndFeatheredMaskInsteadOfSilentlyDroppingThem() {
        val result = StudioMedia3TransformCompiler.compile(transform(x = 0.2f, feather = 0.02f))
        assertFalse(result.isValid)
        assertTrue(result.effects.isEmpty())
        assertTrue(result.errors.any { it.contains("translation") })
        assertTrue(result.errors.any { it.contains("Feathered masks") })
    }

    @Test fun rejectsNonFiniteAndOutOfRangeValues() {
        val result = StudioMedia3TransformCompiler.compile(transform(scaleX = Float.NaN))
        assertFalse(result.isValid)
        assertTrue(result.errors.single().contains("non-finite"))
        val invalidOpacity = StudioMedia3TransformCompiler.compile(transform(opacity = 1.2f))
        assertFalse(invalidOpacity.isValid)
    }

    @Test fun normalizesNegativeRotationIntoMedia3Range() {
        val result = StudioMedia3TransformCompiler.compile(transform(rotation = -90f))
        assertTrue(result.isValid)
        val rotation = result.effects.single() as ScaleAndRotateTransformation
        assertEquals(270f, rotation.rotationDegrees, 0.0001f)
    }
}

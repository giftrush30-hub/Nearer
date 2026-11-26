package com.nearer.app

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioMedia3EffectCompilerTest {
    @Test fun compilesSupportedBlurSaturationAndChromaKeyIntoGpuEffects() {
        val result = StudioMedia3EffectCompiler.compile(listOf(
            StudioVisualEffectNode("soften", StudioVisualEffectKind.BLUR, mapOf("radius" to 5f)),
            StudioVisualEffectNode("colour", StudioVisualEffectKind.COLOR_GRADE, mapOf("saturation" to 1.25f)),
            StudioVisualEffectNode(
                "key", StudioVisualEffectKind.CHROMA_KEY,
                mapOf("tolerance" to 0.2f, "softness" to 0.1f, "despill" to 0.5f),
                keyColorArgb = 0xFF0000FF.toInt()
            )
        ))
        assertTrue(result.isValid)
        assertEquals(3, result.effects.size)
    }

    @Test fun rejectsEffectTypesAndParametersWithoutARealRenderer() {
        val result = StudioMedia3EffectCompiler.compile(listOf(
            StudioVisualEffectNode("glow", StudioVisualEffectKind.GLOW, mapOf("intensity" to 1f)),
            StudioVisualEffectNode("grade", StudioVisualEffectKind.COLOR_GRADE,
                mapOf("saturation" to 1.1f, "exposure" to 1f))
        ))
        assertFalse(result.isValid)
        assertTrue(result.effects.isEmpty())
        assertEquals(2, result.errors.size)
    }

    @Test fun skipsDisabledAndZeroRadiusEffects() {
        val result = StudioMedia3EffectCompiler.compile(listOf(
            StudioVisualEffectNode("disabled", StudioVisualEffectKind.GLOW, enabled = false),
            StudioVisualEffectNode("none", StudioVisualEffectKind.BLUR, mapOf("radius" to 0f))
        ))
        assertTrue(result.isValid)
        assertTrue(result.effects.isEmpty())
    }
}

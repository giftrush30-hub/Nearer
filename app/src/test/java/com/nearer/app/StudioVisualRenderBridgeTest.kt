package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioVisualRenderBridgeTest {
    private fun command(
        effects: List<StudioVisualEffectNode> = emptyList(),
        masks: List<StudioMaskLayer> = emptyList(),
        transform: StudioVisualTransform = StudioVisualTransform(
            0f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f
        )
    ) = StudioVisualDrawCommand(
        trackId = "video",
        itemId = "clip-1",
        sourceUri = "content://local/clip",
        sourceTimeUs = 0L,
        zIndex = 0,
        sourceKind = StudioVisualSourceKind.VIDEO,
        blendMode = StudioVisualBlendMode.NORMAL,
        transform = transform,
        masks = masks,
        effects = effects
    )

    @Test fun compilesEffectAndTransformIntoOneOrderedChain() {
        val result = StudioVisualRenderBridge.compile(command(
            effects = listOf(
                StudioVisualEffectNode("grade", StudioVisualEffectKind.COLOR_GRADE, mapOf("saturation" to 1.2f))
            ),
            transform = StudioVisualTransform(
                0f, 0f, 1f, 1f, 0f, 0.7f, 0f, 0f, 1f, 1f, 3f, 0f
            )
        ))
        assertTrue(result.isValid)
        assertEquals(3, result.effects.size)
        assertTrue(result.effects[0] is HslAdjustment)
        assertTrue(result.effects[1] is GaussianBlur)
        assertTrue(result.effects[2] is AlphaScale)
    }

    @Test fun insertsGpuMaskBetweenPixelEffectsAndGeometryEffects() {
        val result = StudioVisualRenderBridge.compile(command(
            effects = listOf(
                StudioVisualEffectNode("grade", StudioVisualEffectKind.COLOR_GRADE, mapOf("saturation" to 1.2f))
            ),
            masks = listOf(
                StudioMaskLayer("subject", StudioMask.Rectangle(0.1f, 0.1f, 0.9f, 0.9f), feather = 0.02f)
            ),
            transform = StudioVisualTransform(
                0f, 0f, 1.2f, 1.2f, 0f, 0.8f, 0f, 0f, 1f, 1f, 2f, 0.02f
            )
        ))
        assertTrue(result.isValid)
        assertEquals(5, result.effects.size)
        assertTrue(result.effects[0] is HslAdjustment)
        assertTrue(result.effects[1] is StudioGpuMaskEffect)
        assertTrue(result.effects[2] is ScaleAndRotateTransformation)
        assertTrue(result.effects[3] is GaussianBlur)
        assertTrue(result.effects[4] is AlphaScale)
    }

    @Test fun refusesToReturnPartialChainWhenAnyNodeIsUnsupported() {
        val result = StudioVisualRenderBridge.compile(command(
            effects = listOf(StudioVisualEffectNode("glow", StudioVisualEffectKind.GLOW)),
            transform = StudioVisualTransform(
                0.25f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f
            )
        ))
        assertFalse(result.isValid)
        assertTrue(result.effects.isEmpty())
        assertEquals(2, result.errors.size)
    }
}

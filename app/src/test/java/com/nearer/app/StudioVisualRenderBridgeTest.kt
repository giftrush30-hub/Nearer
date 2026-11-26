package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioVisualRenderBridgeTest {
    private fun command(
        effects: List<StudioVisualEffectNode> = emptyList(),
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
        masks = emptyList(),
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

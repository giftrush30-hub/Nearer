package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVisualLayerGraphTest {
    private fun frame() = StudioCompositionFrameState(
        750_000L,
        listOf(
            StudioFrameLayerState("main", "base", "content://video/base", 750_000L, 750_000L, 1f, 1f, emptyMap()),
            StudioFrameLayerState("overlay", "actor", "content://video/actor", 250_000L, 250_000L, 0.8f, 1f, emptyMap())
        ),
        emptyList()
    )

    @Test fun sortsLayersByZOrderAndCarriesBlendAndEffectStack() {
        val specs = listOf(
            StudioVisualLayerSpec("main", "base", 0),
            StudioVisualLayerSpec("overlay", "actor", 10, blendMode = StudioVisualBlendMode.SCREEN,
                effects = listOf(StudioVisualEffectNode("glow", StudioVisualEffectKind.GLOW,
                    mapOf("intensity" to 1.4f, "radius" to 12f)))
        )
        )
        val plan = StudioVisualLayerGraph.build(frame(), specs)
        assertTrue(plan.isValid)
        assertEquals(listOf("base", "actor"), plan.drawCommands.map { it.itemId })
        assertEquals(StudioVisualBlendMode.SCREEN, plan.drawCommands.last().blendMode)
        assertEquals("glow", plan.drawCommands.last().effects.single().id)
    }

    @Test fun evaluatesClipLocalTransformAndCombinesOpacity() {
        val spec = StudioVisualLayerSpec("overlay", "actor", 2, animations = listOf(
            StudioPropertyAnimation(StudioAnimatedProperty.X, listOf(StudioKeyframe(0L, -0.5f), StudioKeyframe(500L, 0.5f))),
            StudioPropertyAnimation(StudioAnimatedProperty.OPACITY, listOf(StudioKeyframe(0L, 0.25f), StudioKeyframe(500L, 0.75f))),
            StudioPropertyAnimation(StudioAnimatedProperty.SCALE_X, listOf(StudioKeyframe(0L, 1f), StudioKeyframe(500L, 2f)))
        ))
        val plan = StudioVisualLayerGraph.build(frame(), listOf(StudioVisualLayerSpec("main", "base", 0), spec))
        val command = plan.drawCommands.last()
        assertEquals(0f, command.transform.x, 0.0001f)
        assertEquals(0.4f, command.transform.opacity, 0.0001f)
        assertEquals(1.5f, command.transform.scaleX, 0.0001f)
        assertEquals(250_000L, command.sourceTimeUs)
    }

    @Test fun preservesMasksAndOmitsDisabledEffects() {
        val mask = StudioMaskLayer("subject", StudioMask.Ellipse(0.5f, 0.5f, 0.3f, 0.4f), 0.02f)
        val spec = StudioVisualLayerSpec("main", "base", 0, masks = listOf(mask), effects = listOf(
            StudioVisualEffectNode("grade", StudioVisualEffectKind.COLOR_GRADE, mapOf("saturation" to 1.2f)),
            StudioVisualEffectNode("disabled", StudioVisualEffectKind.BLUR, enabled = false)
        ))
        val plan = StudioVisualLayerGraph.build(frame(), listOf(spec, StudioVisualLayerSpec("overlay", "actor", 1)))
        assertEquals(listOf(mask), plan.drawCommands.first().masks)
        assertEquals(listOf("grade"), plan.drawCommands.first().effects.map { it.id })
    }

    @Test fun rejectsDuplicateIdsInvalidMasksAndUnsafeEffectParameters() {
        val invalid = StudioVisualLayerSpec("main", "base", 0,
            masks = listOf(StudioMaskLayer("bad", StudioMask.Rectangle(0.8f, 0.2f, 0.1f, 0.9f))),
            effects = listOf(StudioVisualEffectNode("blur", StudioVisualEffectKind.BLUR, mapOf("radius" to 100f)))
        )
        val validation = StudioVisualLayerGraph.validate(listOf(invalid, invalid))
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("duplicated") })
        assertTrue(validation.errors.any { it.contains("positive area") })
        assertTrue(validation.errors.any { it.contains("safe range") })
    }

    @Test fun rejectsMissingDefinitionsAndInvalidAnimatedCrop() {
        val missing = StudioVisualLayerGraph.build(frame(), emptyList())
        assertFalse(missing.isValid)
        assertTrue(missing.errors.any { it.contains("No visual layer definition") })
        val invalidCrop = StudioVisualLayerSpec("main", "base", 0, animations = listOf(
            StudioPropertyAnimation(StudioAnimatedProperty.CROP_LEFT, listOf(StudioKeyframe(0L, 1f)))
        ))
        val plan = StudioVisualLayerGraph.build(frame(), listOf(invalidCrop, StudioVisualLayerSpec("overlay", "actor", 1)))
        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("animated crop bounds") })
    }

    @Test fun enforcesLayerAndEffectResourceBudgets() {
        val tooManyEffects = StudioVisualLayerSpec("main", "base", 0, effects =
            (0..StudioVisualLayerGraph.MAX_EFFECTS_PER_LAYER).map {
                StudioVisualEffectNode("e$it", StudioVisualEffectKind.VIGNETTE)
            })
        val tooManyLayers = (0..StudioVisualLayerGraph.MAX_LAYERS).map {
            StudioVisualLayerSpec("t$it", "item", it)
        }
        assertFalse(StudioVisualLayerGraph.validate(listOf(tooManyEffects)).isValid)
        assertFalse(StudioVisualLayerGraph.validate(tooManyLayers).isValid)
    }
    @Test fun explicitLayerKeyframesTakePrecedenceOverPreEvaluatedFrameValues() {
        val active = frame().copy(videoLayers = frame().videoLayers.map { layer ->
            if (layer.itemId == "actor") layer.copy(
                animatedValues = mapOf(StudioAnimatedProperty.X to 0.3f)
            ) else layer
        })
        val animatedSpec = StudioVisualLayerSpec("overlay", "actor", 2, animations = listOf(
            StudioPropertyAnimation(StudioAnimatedProperty.X, listOf(
                StudioKeyframe(0L, 0f), StudioKeyframe(500L, 1f)
            ))
        ))
        val command = StudioVisualLayerGraph.build(
            active, listOf(StudioVisualLayerSpec("main", "base", 0), animatedSpec)
        ).drawCommands.last()
        assertEquals(0.5f, command.transform.x, 0.0001f)
    }

    @Test fun rejectsUnsafeAnimatedOpacityAndScale() {
        val invalid = StudioVisualLayerSpec("overlay", "actor", 1, animations = listOf(
            StudioPropertyAnimation(StudioAnimatedProperty.OPACITY, listOf(StudioKeyframe(0L, 1.5f)))
        ))
        val plan = StudioVisualLayerGraph.build(
            frame(), listOf(StudioVisualLayerSpec("main", "base", 0), invalid)
        )
        assertFalse(plan.isValid)
        assertTrue(plan.errors.any { it.contains("animated opacity") })
    }

}

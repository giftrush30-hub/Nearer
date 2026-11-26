package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect

internal data class StudioVisualRenderCompilation(
    val effects: List<GlEffect>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Turns one validated draw command into the ordered Media3 effect chain consumed by a
 * single EditedMediaItem. This does not combine separate video layers; the composition
 * compositor still owns inter-layer blending and source scheduling.
 */
@UnstableApi
internal object StudioVisualRenderBridge {
    fun compile(command: StudioVisualDrawCommand): StudioVisualRenderCompilation {
        val visual = StudioMedia3EffectCompiler.compile(command.effects)
        val transform = StudioMedia3TransformCompiler.compile(command.transform)
        val errors = visual.errors + transform.errors
        if (errors.isNotEmpty()) return StudioVisualRenderCompilation(emptyList(), errors)
        val maskEffects = if (command.masks.isEmpty()) emptyList() else listOf(
            StudioGpuMaskEffect(command.masks.map { it.copy(feather = command.transform.maskFeather) })
        )
        // Key/grade first, then the cached GPU alpha mask, then geometry/blur and opacity.
        return StudioVisualRenderCompilation(visual.effects + maskEffects + transform.effects, emptyList())
    }
}

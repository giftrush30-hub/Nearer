package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect

internal data class StudioVisualRenderPlan(
    val pixelEffectCount: Int,
    val maskCount: Int,
    val transformEffectCount: Int,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
    /** Index at which the single combined mask effect is inserted into the final chain. */
    val maskInsertionIndex: Int? get() = if (maskCount == 0) null else pixelEffectCount
    val totalEffectCount: Int get() = pixelEffectCount + (if (maskCount == 0) 0 else 1) + transformEffectCount
}

internal data class StudioVisualRenderCompilation(
    val effects: List<GlEffect>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Turns one validated draw command into the ordered Media3 effect chain consumed by a
 * single EditedMediaItem. The pure plan is separately testable without constructing Android
 * GL effects in a local JVM. This does not combine separate video layers.
 */
@UnstableApi
internal object StudioVisualRenderBridge {
    fun plan(command: StudioVisualDrawCommand): StudioVisualRenderPlan {
        val visual = StudioMedia3EffectCompiler.compile(command.effects)
        val transform = StudioMedia3TransformCompiler.compile(command.transform)
        val errors = visual.errors + transform.errors
        if (errors.isNotEmpty()) return StudioVisualRenderPlan(0, 0, 0, errors)
        return StudioVisualRenderPlan(
            pixelEffectCount = visual.effects.size,
            maskCount = command.masks.size,
            transformEffectCount = transform.effects.size,
            errors = emptyList()
        )
    }

    fun compile(command: StudioVisualDrawCommand): StudioVisualRenderCompilation {
        val plan = plan(command)
        if (!plan.isValid) return StudioVisualRenderCompilation(emptyList(), plan.errors)
        val visual = StudioMedia3EffectCompiler.compile(command.effects)
        val transform = StudioMedia3TransformCompiler.compile(command.transform)
        val maskEffects = if (command.masks.isEmpty()) emptyList() else listOf(
            StudioGpuMaskEffect(command.masks)
        )
        return StudioVisualRenderCompilation(visual.effects + maskEffects + transform.effects, emptyList())
    }
}

package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.GlEffect
import androidx.media3.effect.HslAdjustment

internal data class StudioEffectCompilation(
    val effects: List<GlEffect>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Compiles the subset of the visual effect graph that has a real Media3 GPU implementation.
 * Unsupported effects fail explicitly rather than being silently ignored during export.
 */
@UnstableApi
internal object StudioMedia3EffectCompiler {
    fun compile(nodes: List<StudioVisualEffectNode>): StudioEffectCompilation {
        val compiled = mutableListOf<GlEffect>()
        val errors = mutableListOf<String>()
        nodes.filter { it.enabled }.forEach { node ->
            val parameters = node.parameters
            when (node.kind) {
                StudioVisualEffectKind.BLUR -> {
                    val unknown = parameters.keys - setOf("radius")
                    val radius = parameters["radius"] ?: 0f
                    if (unknown.isNotEmpty()) errors += "${node.id}: unsupported blur parameters: ${unknown.sorted().joinToString()}."
                    else if (!radius.isFinite() || radius !in 0f..64f) errors += "${node.id}: blur radius must be within 0..64."
                    else if (radius > 0f) compiled += GaussianBlur(radius)
                }
                StudioVisualEffectKind.COLOR_GRADE -> {
                    val unknown = parameters.keys - setOf("saturation")
                    val saturation = parameters["saturation"] ?: 1f
                    if (unknown.isNotEmpty()) {
                        errors += "${node.id}: this Media3 pass does not yet support colour-grade controls: ${unknown.sorted().joinToString()}."
                    } else if (!saturation.isFinite() || saturation !in 0f..2f) {
                        errors += "${node.id}: Media3 HSL saturation must be within 0..2."
                    } else if (saturation != 1f) {
                        compiled += HslAdjustment.Builder()
                            .adjustSaturation((saturation - 1f) * 100f)
                            .build()
                    }
                }
                StudioVisualEffectKind.CHROMA_KEY -> {
                    val unknown = parameters.keys - setOf("tolerance", "softness", "despill")
                    val tolerance = parameters["tolerance"] ?: 0.22f
                    val softness = parameters["softness"] ?: 0.12f
                    val despill = parameters["despill"] ?: 0.65f
                    if (unknown.isNotEmpty()) errors += "${node.id}: unsupported chroma-key parameters: ${unknown.sorted().joinToString()}."
                    else if (!tolerance.isFinite() || tolerance !in 0f..1f ||
                        !softness.isFinite() || softness !in 0.001f..1f ||
                        !despill.isFinite() || despill !in 0f..1f) {
                        errors += "${node.id}: chroma-key controls are outside their safe ranges."
                    } else {
                        compiled += StudioChromaKeyEffect(
                            StudioChromaKeySettings(
                                keyColorArgb = node.keyColorArgb ?: 0xFF00FF00.toInt(),
                                tolerance = tolerance,
                                edgeSoftness = softness,
                                despill = despill
                            )
                        )
                    }
                }
                else -> errors += "${node.id}: ${node.kind} has no Media3 GPU implementation wired yet."
            }
        }
        return StudioEffectCompilation(compiled, errors)
    }
}

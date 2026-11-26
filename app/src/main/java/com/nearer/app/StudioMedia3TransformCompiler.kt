package com.nearer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Crop
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.GlEffect
import androidx.media3.effect.ScaleAndRotateTransformation

internal data class StudioTransformCompilation(
    val effects: List<GlEffect>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

internal data class StudioCropNdcBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
)

internal data class StudioTransformEffectPlan(
    val crop: StudioCropNdcBounds?,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val blurRadius: Float,
    val opacity: Float
) {
    val needsScaleOrRotation: Boolean
        get() = scaleX != 1f || scaleY != 1f || rotationDegrees != 0f
    val needsBlur: Boolean get() = blurRadius > 0f
    val needsOpacity: Boolean get() = opacity < 1f
}

internal data class StudioTransformPlanCompilation(
    val plan: StudioTransformEffectPlan?,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty() && plan != null
}

/** Pure validation and coordinate conversion; safe to exercise in local JVM tests. */
internal object StudioTransformEffectPlanner {
    fun plan(transform: StudioVisualTransform): StudioTransformPlanCompilation {
        val values = listOf(
            transform.x, transform.y, transform.scaleX, transform.scaleY,
            transform.rotationDegrees, transform.opacity, transform.cropLeft,
            transform.cropTop, transform.cropRight, transform.cropBottom,
            transform.blurRadius, transform.maskFeather
        )
        if (values.any { !it.isFinite() }) {
            return StudioTransformPlanCompilation(null, listOf("Transform contains a non-finite value."))
        }

        val errors = mutableListOf<String>()
        if (transform.x !in -2f..2f || transform.y !in -2f..2f) {
            errors += "Layer translation is outside the supported range."
        } else if (transform.x != 0f || transform.y != 0f) {
            errors += "Layer translation requires the custom compositor matrix pass and is not yet supported."
        }
        if (transform.scaleX !in 0.01f..20f || transform.scaleY !in 0.01f..20f) {
            errors += "Layer scale must be within 0.01..20."
        }
        if (transform.rotationDegrees !in -36_000f..36_000f) {
            errors += "Layer rotation is outside the supported range."
        }
        if (transform.opacity !in 0f..1f) errors += "Layer opacity must be within 0..1."
        if (transform.cropLeft !in 0f..1f || transform.cropTop !in 0f..1f ||
            transform.cropRight !in 0f..1f || transform.cropBottom !in 0f..1f ||
            transform.cropLeft >= transform.cropRight || transform.cropTop >= transform.cropBottom) {
            errors += "Layer crop bounds are invalid."
        }
        if (transform.blurRadius !in 0f..64f) errors += "Layer blur radius must be within 0..64."
        if (transform.maskFeather !in 0f..0.25f) errors += "Mask feather must be within 0..0.25."
        else if (transform.maskFeather > 0f) errors += "Feathered masks require the custom mask compositor and are not yet supported."

        if (errors.isNotEmpty()) return StudioTransformPlanCompilation(null, errors)

        val crop = if (transform.cropLeft != 0f || transform.cropTop != 0f ||
            transform.cropRight != 1f || transform.cropBottom != 1f) {
            StudioCropNdcBounds(
                left = transform.cropLeft * 2f - 1f,
                right = transform.cropRight * 2f - 1f,
                bottom = 1f - transform.cropBottom * 2f,
                top = 1f - transform.cropTop * 2f
            )
        } else null

        val rotation = ((transform.rotationDegrees % 360f) + 360f) % 360f
        return StudioTransformPlanCompilation(
            StudioTransformEffectPlan(
                crop = crop,
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                rotationDegrees = rotation,
                blurRadius = transform.blurRadius,
                opacity = transform.opacity
            ),
            emptyList()
        )
    }
}

/**
 * Compiles the pure transform plan into Media3 GPU effects.
 * Translation and feathered masks are rejected until a custom compositor pass exists.
 */
@UnstableApi
internal object StudioMedia3TransformCompiler {
    fun compile(transform: StudioVisualTransform): StudioTransformCompilation {
        val planned = StudioTransformEffectPlanner.plan(transform)
        val plan = planned.plan ?: return StudioTransformCompilation(emptyList(), planned.errors)
        val effects = mutableListOf<GlEffect>()

        plan.crop?.let { bounds ->
            effects += Crop(bounds.left, bounds.right, bounds.bottom, bounds.top)
        }
        if (plan.needsScaleOrRotation) {
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(plan.scaleX, plan.scaleY)
                .setRotationDegrees(plan.rotationDegrees)
                .build()
        }
        if (plan.needsBlur) effects += GaussianBlur(plan.blurRadius)
        if (plan.needsOpacity) effects += AlphaScale(plan.opacity)
        return StudioTransformCompilation(effects, emptyList())
    }
}

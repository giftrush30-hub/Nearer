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

/**
 * Compiles frame-plan transforms to real Media3 GPU effects.
 *
 * Translation and feathered masks are deliberately rejected until a compositor shader can
 * implement them correctly. Returning a partial effect list with errors is not renderable.
 */
@UnstableApi
internal object StudioMedia3TransformCompiler {
    fun compile(transform: StudioVisualTransform): StudioTransformCompilation {
        val effects = mutableListOf<GlEffect>()
        val errors = mutableListOf<String>()

        val values = listOf(
            transform.x, transform.y, transform.scaleX, transform.scaleY,
            transform.rotationDegrees, transform.opacity, transform.cropLeft,
            transform.cropTop, transform.cropRight, transform.cropBottom,
            transform.blurRadius, transform.maskFeather
        )
        if (values.any { !it.isFinite() }) {
            return StudioTransformCompilation(emptyList(), listOf("Transform contains a non-finite value."))
        }
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

        if (errors.isNotEmpty()) return StudioTransformCompilation(emptyList(), errors)

        if (transform.cropLeft != 0f || transform.cropTop != 0f ||
            transform.cropRight != 1f || transform.cropBottom != 1f) {
            val leftNdc = transform.cropLeft * 2f - 1f
            val rightNdc = transform.cropRight * 2f - 1f
            val bottomNdc = 1f - transform.cropBottom * 2f
            val topNdc = 1f - transform.cropTop * 2f
            effects += Crop(leftNdc, rightNdc, bottomNdc, topNdc)
        }

        val rotation = ((transform.rotationDegrees % 360f) + 360f) % 360f
        if (transform.scaleX != 1f || transform.scaleY != 1f || rotation != 0f) {
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(transform.scaleX, transform.scaleY)
                .setRotationDegrees(rotation)
                .build()
        }
        if (transform.blurRadius > 0f) effects += GaussianBlur(transform.blurRadius)
        if (transform.opacity < 1f) effects += AlphaScale(transform.opacity)

        return StudioTransformCompilation(effects, emptyList())
    }
}

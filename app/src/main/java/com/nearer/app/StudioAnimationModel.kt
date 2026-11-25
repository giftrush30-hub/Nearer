package com.nearer.app

/** Animatable properties shared by 2D clips, overlays, masks and future 3D objects. */
internal enum class StudioAnimatedProperty {
    X, Y, SCALE_X, SCALE_Y, ROTATION_DEGREES, OPACITY, CROP_LEFT, CROP_TOP,
    CROP_RIGHT, CROP_BOTTOM, BLUR, MASK_FEATHER
}

internal enum class StudioKeyframeInterpolation { HOLD, LINEAR, SMOOTHSTEP }

internal data class StudioKeyframe(
    val timeMs: Long,
    val value: Float,
    val interpolation: StudioKeyframeInterpolation = StudioKeyframeInterpolation.LINEAR
)

internal data class StudioPropertyAnimation(
    val property: StudioAnimatedProperty,
    val keyframes: List<StudioKeyframe>
) {
    fun valueAt(timeMs: Long, fallback: Float): Float =
        StudioKeyframeEvaluator.valueAt(keyframes, timeMs, fallback)
}

/** Normalized mask geometry; coordinates remain resolution-independent at export sizes. */
internal sealed interface StudioMask {
    data class Rectangle(val left: Float, val top: Float, val right: Float, val bottom: Float) : StudioMask
    data class Ellipse(val centerX: Float, val centerY: Float, val radiusX: Float, val radiusY: Float) : StudioMask
    data class Polygon(val points: List<Point>) : StudioMask
    data class Point(val x: Float, val y: Float)
}

internal data class StudioMaskLayer(
    val id: String,
    val mask: StudioMask,
    val feather: Float = 0f,
    val inverted: Boolean = false
)

internal data class StudioAnimationValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Deterministic keyframe evaluation; renderer can sample this at any output frame timestamp. */
internal object StudioKeyframeEvaluator {
    fun validate(animation: StudioPropertyAnimation): StudioAnimationValidation {
        val errors = mutableListOf<String>()
        if (animation.keyframes.isEmpty()) errors += "Animation must contain at least one keyframe."
        if (animation.keyframes.size > 2_000) errors += "Animation exceeds the 2,000-keyframe limit."
        animation.keyframes.forEachIndexed { index, frame ->
            if (frame.timeMs < 0L || frame.timeMs > StudioRenderPlanBuilder.MAX_DURATION_MS) {
                errors += "Keyframe $index has an invalid timestamp."
            }
            if (!frame.value.isFinite()) errors += "Keyframe $index has a non-finite value."
            if (index > 0 && frame.timeMs <= animation.keyframes[index - 1].timeMs) {
                errors += "Keyframe timestamps must be strictly increasing."
            }
        }
        return StudioAnimationValidation(errors.distinct())
    }

    fun valueAt(keyframes: List<StudioKeyframe>, timeMs: Long, fallback: Float): Float {
        if (keyframes.isEmpty() || !fallback.isFinite()) return fallback
        if (timeMs <= keyframes.first().timeMs) return keyframes.first().value
        if (timeMs >= keyframes.last().timeMs) return keyframes.last().value
        val rightIndex = keyframes.indexOfFirst { it.timeMs >= timeMs }
        if (rightIndex <= 0) return keyframes.first().value
        val left = keyframes[rightIndex - 1]
        val right = keyframes[rightIndex]
        if (left.interpolation == StudioKeyframeInterpolation.HOLD) return left.value
        val span = (right.timeMs - left.timeMs).coerceAtLeast(1L).toFloat()
        val raw = ((timeMs - left.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val progress = if (left.interpolation == StudioKeyframeInterpolation.SMOOTHSTEP) {
            raw * raw * (3f - 2f * raw)
        } else raw
        return left.value + (right.value - left.value) * progress
    }

    fun validateMask(mask: StudioMaskLayer): StudioAnimationValidation {
        val errors = mutableListOf<String>()
        if (mask.id.isBlank() || mask.id.length > 128) errors += "Mask ID must contain 1–128 characters."
        if (!mask.feather.isFinite() || mask.feather !in 0f..0.25f) errors += "Mask feather must be between 0 and 0.25."
        fun validPoint(x: Float, y: Float) = x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f
        when (val shape = mask.mask) {
            is StudioMask.Rectangle -> {
                if (!validPoint(shape.left, shape.top) || !validPoint(shape.right, shape.bottom) ||
                    shape.left >= shape.right || shape.top >= shape.bottom) errors += "Rectangle bounds must be normalized and have positive area."
            }
            is StudioMask.Ellipse -> {
                if (!validPoint(shape.centerX, shape.centerY) || !shape.radiusX.isFinite() ||
                    !shape.radiusY.isFinite() || shape.radiusX <= 0f || shape.radiusY <= 0f ||
                    shape.centerX - shape.radiusX < 0f || shape.centerX + shape.radiusX > 1f ||
                    shape.centerY - shape.radiusY < 0f || shape.centerY + shape.radiusY > 1f) {
                    errors += "Ellipse must fit inside normalized canvas bounds."
                }
            }
            is StudioMask.Polygon -> {
                if (shape.points.size !in 3..256 || shape.points.any { !validPoint(it.x, it.y) }) {
                    errors += "Polygon needs 3–256 normalized points."
                }
            }
        }
        return StudioAnimationValidation(errors)
    }
}

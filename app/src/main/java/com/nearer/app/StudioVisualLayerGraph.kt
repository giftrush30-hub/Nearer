package com.nearer.app

internal enum class StudioVisualBlendMode { NORMAL, ADD, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN }
internal enum class StudioVisualSourceKind { VIDEO, IMAGE, SHAPE, PARTICLE }
internal enum class StudioVisualEffectKind { COLOR_GRADE, BLUR, CHROMA_KEY, GLOW, DROP_SHADOW, VIGNETTE, FILM_GRAIN, OUTLINE, WARP }

internal data class StudioVisualEffectNode(
    val id: String,
    val kind: StudioVisualEffectKind,
    val parameters: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true,
    /** ARGB key colour for chroma removal; green is the default. */
    val keyColorArgb: Int? = null
)

internal data class StudioVisualLayerSpec(
    val trackId: String,
    val itemId: String,
    val zIndex: Int,
    val sourceKind: StudioVisualSourceKind = StudioVisualSourceKind.VIDEO,
    val visible: Boolean = true,
    val blendMode: StudioVisualBlendMode = StudioVisualBlendMode.NORMAL,
    val masks: List<StudioMaskLayer> = emptyList(),
    val effects: List<StudioVisualEffectNode> = emptyList(),
    val animations: List<StudioPropertyAnimation> = emptyList()
)

internal data class StudioVisualLayerValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

internal data class StudioVisualTransform(
    val x: Float, val y: Float, val scaleX: Float, val scaleY: Float,
    val rotationDegrees: Float, val opacity: Float,
    val cropLeft: Float, val cropTop: Float, val cropRight: Float, val cropBottom: Float,
    val blurRadius: Float, val maskFeather: Float
)

internal data class StudioVisualDrawCommand(
    val trackId: String, val itemId: String, val sourceUri: String, val sourceTimeUs: Long,
    val zIndex: Int, val sourceKind: StudioVisualSourceKind, val blendMode: StudioVisualBlendMode,
    val transform: StudioVisualTransform, val masks: List<StudioMaskLayer>,
    val effects: List<StudioVisualEffectNode>
)

internal data class StudioVisualFramePlan(
    val timestampUs: Long, val drawCommands: List<StudioVisualDrawCommand>, val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Deterministic, graphics-independent bridge between the edit timeline and a pixel compositor.
 * It creates draw commands; it does not itself render pixels or export a movie.
 */
internal object StudioVisualLayerGraph {
    const val MAX_LAYERS = 64
    const val MAX_EFFECTS_PER_LAYER = 16
    const val MAX_MASKS_PER_LAYER = 8
    private const val MAX_BLUR_RADIUS = 64f

    fun validate(specs: List<StudioVisualLayerSpec>): StudioVisualLayerValidation {
        val errors = mutableListOf<String>()
        if (specs.size > MAX_LAYERS) errors += "Visual composition exceeds the 64-layer limit."
        val ids = mutableSetOf<String>()
        specs.forEach { spec ->
            val key = spec.trackId + "/" + spec.itemId
            if (spec.trackId.isBlank() || spec.itemId.isBlank()) errors += "Visual layer track and item IDs must not be blank."
            if (!ids.add(key)) errors += "Visual layer $key is duplicated."
            if (spec.effects.size > MAX_EFFECTS_PER_LAYER) errors += "$key exceeds the 16-effect limit."
            if (spec.masks.size > MAX_MASKS_PER_LAYER) errors += "$key exceeds the 8-mask limit."
            spec.masks.forEach { mask ->
                errors += StudioKeyframeEvaluator.validateMask(mask).errors.map { "$key: $it" }
            }
            val effectIds = mutableSetOf<String>()
            spec.effects.forEach { effect ->
                if (effect.id.isBlank() || !effectIds.add(effect.id)) errors += "$key has a blank or duplicate effect ID."
                errors += validateEffect(effect).map { "$key/${effect.id}: $it" }
            }
            val properties = mutableSetOf<StudioAnimatedProperty>()
            spec.animations.forEach { animation ->
                if (!properties.add(animation.property)) errors += "$key has more than one animation for ${animation.property}."
                errors += StudioKeyframeEvaluator.validate(animation).errors.map { "$key: $it" }
            }
        }
        return StudioVisualLayerValidation(errors.distinct())
    }

    fun build(frame: StudioCompositionFrameState, specs: List<StudioVisualLayerSpec>): StudioVisualFramePlan {
        val errors = validate(specs).errors.toMutableList()
        val byLayer = specs.associateBy { it.trackId to it.itemId }
        val commands = frame.videoLayers.mapNotNull { active ->
            val spec = byLayer[active.trackId to active.itemId]
            if (spec == null) {
                errors += "No visual layer definition exists for ${active.trackId}/${active.itemId}."
                return@mapNotNull null
            }
            if (!spec.visible) return@mapNotNull null
            val animated = spec.animations.associate { animation ->
                animation.property to animation.valueAt(active.localTimeUs / 1_000L, Float.NaN)
            }.filterValues { it.isFinite() }.toMutableMap()
            active.animatedValues.forEach { (property, value) ->
                if (property !in animated && value.isFinite()) animated[property] = value
            }
            fun value(property: StudioAnimatedProperty, fallback: Float) = animated[property] ?: fallback
            val rawOpacity = value(StudioAnimatedProperty.OPACITY, 1f)
            if (!rawOpacity.isFinite() || rawOpacity !in 0f..1f) {
                errors += "${active.trackId}/${active.itemId} has animated opacity outside 0..1."
                return@mapNotNull null
            }
            val opacity = (active.opacity * rawOpacity).coerceIn(0f, 1f)
            val left = value(StudioAnimatedProperty.CROP_LEFT, 0f)
            val top = value(StudioAnimatedProperty.CROP_TOP, 0f)
            val right = value(StudioAnimatedProperty.CROP_RIGHT, 1f)
            val bottom = value(StudioAnimatedProperty.CROP_BOTTOM, 1f)
            if (left !in 0f..1f || top !in 0f..1f || right !in 0f..1f || bottom !in 0f..1f ||
                left >= right || top >= bottom) {
                errors += "${active.trackId}/${active.itemId} has invalid animated crop bounds."
                return@mapNotNull null
            }
            val blur = value(StudioAnimatedProperty.BLUR, 0f)
            val maskFeatherOverride = animated[StudioAnimatedProperty.MASK_FEATHER]
            val resolvedMasks = if (maskFeatherOverride == null) spec.masks else
                spec.masks.map { it.copy(feather = maskFeatherOverride) }
            val feather = value(StudioAnimatedProperty.MASK_FEATHER, spec.masks.maxOfOrNull { it.feather } ?: 0f)
            val x = value(StudioAnimatedProperty.X, 0f)
            val y = value(StudioAnimatedProperty.Y, 0f)
            val scaleX = value(StudioAnimatedProperty.SCALE_X, 1f)
            val scaleY = value(StudioAnimatedProperty.SCALE_Y, 1f)
            val rotation = value(StudioAnimatedProperty.ROTATION_DEGREES, 0f)
            if (!blur.isFinite() || blur !in 0f..MAX_BLUR_RADIUS || !feather.isFinite() || feather !in 0f..0.25f ||
                !x.isFinite() || x !in -2f..2f || !y.isFinite() || y !in -2f..2f ||
                !scaleX.isFinite() || scaleX !in 0.01f..20f || !scaleY.isFinite() || scaleY !in 0.01f..20f ||
                !rotation.isFinite() || rotation !in -36_000f..36_000f) {
                errors += "${active.trackId}/${active.itemId} has an unsafe animated transform, blur or mask-feather value."
                return@mapNotNull null
            }
            StudioVisualDrawCommand(
                active.trackId, active.itemId, active.sourceUri, active.sourceTimeUs,
                spec.zIndex, spec.sourceKind, spec.blendMode,
                StudioVisualTransform(
                    x, y, scaleX, scaleY, rotation, opacity,
                    left, top, right, bottom, blur, feather
                ),
                resolvedMasks, spec.effects.filter { it.enabled }
            )
        }.sortedWith(compareBy<StudioVisualDrawCommand> { it.zIndex }.thenBy { it.trackId }.thenBy { it.itemId })
        return StudioVisualFramePlan(frame.timestampUs, commands, errors.distinct())
    }

    private fun validateEffect(effect: StudioVisualEffectNode): List<String> {
        if (!effect.enabled) return emptyList()
        val ranges: Map<String, ClosedFloatingPointRange<Float>> = when (effect.kind) {
            StudioVisualEffectKind.COLOR_GRADE -> mapOf(
                "exposure" to (-4f..4f), "contrast" to (0f..4f), "saturation" to (0f..3f),
                "temperature" to (-1f..1f), "tint" to (-1f..1f)
            )
            StudioVisualEffectKind.BLUR -> mapOf("radius" to (0f..MAX_BLUR_RADIUS))
            StudioVisualEffectKind.CHROMA_KEY -> mapOf(
                "tolerance" to (0f..1f), "softness" to (0.001f..1f), "despill" to (0f..1f)
            )
            StudioVisualEffectKind.GLOW -> mapOf("intensity" to (0f..3f), "radius" to (0f..64f))
            StudioVisualEffectKind.DROP_SHADOW -> mapOf(
                "opacity" to (0f..1f), "blur" to (0f..64f), "offsetX" to (-1f..1f), "offsetY" to (-1f..1f)
            )
            StudioVisualEffectKind.VIGNETTE -> mapOf("intensity" to (0f..1f), "radius" to (0f..1f))
            StudioVisualEffectKind.FILM_GRAIN -> mapOf("intensity" to (0f..1f), "size" to (0.1f..8f))
            StudioVisualEffectKind.OUTLINE -> mapOf("thickness" to (0f..0.1f), "opacity" to (0f..1f))
            StudioVisualEffectKind.WARP -> mapOf("amount" to (-1f..1f), "frequency" to (0f..20f))
        }
        return effect.parameters.mapNotNull { (name, value) ->
            val range = ranges[name]
            when {
                range == null -> "Unknown ${effect.kind} parameter '$name'."
                !value.isFinite() || value !in range -> "Parameter '$name' is outside its safe range."
                else -> null
            }
        }
    }
}

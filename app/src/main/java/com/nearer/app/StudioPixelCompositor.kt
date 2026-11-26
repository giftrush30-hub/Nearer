package com.nearer.app

import kotlin.math.max
import kotlin.math.min

internal data class StudioPixelLayer(
    val id: String,
    val pixels: IntArray,
    val opacity: Float = 1f,
    val blendMode: StudioVisualBlendMode = StudioVisualBlendMode.NORMAL,
    val mask: StudioAlphaMask? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

internal data class StudioPixelCompositionResult(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Deterministic CPU reference compositor for local image, shape and particle layers.
 * Video export should use a GPU compositor for full-resolution, real-time performance.
 */
internal object StudioPixelCompositor {
    const val MAX_PIXELS = 16_777_216
    const val MAX_LAYERS = 64

    fun compose(width: Int, height: Int, layers: List<StudioPixelLayer>): StudioPixelCompositionResult {
        val errors = validate(width, height, layers)
        if (errors.isNotEmpty()) return StudioPixelCompositionResult(width, height, IntArray(0), errors)
        val output = IntArray(width * height)
        layers.forEach { layer ->
            val left = max(0, layer.offsetX)
            val top = max(0, layer.offsetY)
            val right = min(width, layer.offsetX + width)
            val bottom = min(height, layer.offsetY + height)
            for (y in top until bottom) {
                val sourceY = y - layer.offsetY
                for (x in left until right) {
                    val sourceX = x - layer.offsetX
                    val sourceIndex = sourceY * width + sourceX
                    val maskAlpha = layer.mask?.alphaAt(sourceX, sourceY)?.div(255f) ?: 1f
                    val source = applyOpacity(layer.pixels[sourceIndex], layer.opacity * maskAlpha)
                    if ((source ushr 24) == 0) continue
                    val destinationIndex = y * width + x
                    output[destinationIndex] = blend(source, output[destinationIndex], layer.blendMode)
                }
            }
        }
        return StudioPixelCompositionResult(width, height, output, emptyList())
    }

    fun validate(width: Int, height: Int, layers: List<StudioPixelLayer>): List<String> {
        val errors = mutableListOf<String>()
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) {
            errors += "Composition dimensions must be positive and no larger than 16 megapixels."
            return errors
        }
        if (layers.size > MAX_LAYERS) errors += "Composition exceeds the 64-layer limit."
        val ids = mutableSetOf<String>()
        layers.forEach { layer ->
            if (layer.id.isBlank() || !ids.add(layer.id)) errors += "Pixel layer IDs must be non-empty and unique."
            if (layer.pixels.size != width * height) errors += "${layer.id}: source pixel count does not match the frame."
            if (!layer.opacity.isFinite() || layer.opacity !in 0f..1f) errors += "${layer.id}: opacity must be within 0..1."
            layer.mask?.let {
                if (it.width != width || it.height != height) errors += "${layer.id}: mask dimensions do not match the frame."
            }
        }
        return errors.distinct()
    }

    private fun applyOpacity(argb: Int, opacity: Float): Int {
        val alpha = (((argb ushr 24) and 0xFF) * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun blend(source: Int, destination: Int, mode: StudioVisualBlendMode): Int {
        val sa = ((source ushr 24) and 0xFF) / 255f
        val da = ((destination ushr 24) and 0xFF) / 255f
        if (sa <= 0f) return destination
        if (da <= 0f) return source
        val sr = ((source ushr 16) and 0xFF) / 255f
        val sg = ((source ushr 8) and 0xFF) / 255f
        val sb = (source and 0xFF) / 255f
        val dr = ((destination ushr 16) and 0xFF) / 255f
        val dg = ((destination ushr 8) and 0xFF) / 255f
        val db = (destination and 0xFF) / 255f
        val outA = sa + da * (1f - sa)
        fun channel(cs: Float, cd: Float): Int {
            val blended = blendChannel(cs, cd, mode)
            val premultiplied = (1f - sa) * da * cd + (1f - da) * sa * cs + sa * da * blended
            return (premultiplied / outA * 255f).toInt().coerceIn(0, 255)
        }
        val alpha = (outA * 255f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (channel(sr, dr) shl 16) or (channel(sg, dg) shl 8) or channel(sb, db)
    }

    private fun blendChannel(source: Float, destination: Float, mode: StudioVisualBlendMode): Float =
        when (mode) {
            StudioVisualBlendMode.NORMAL -> source
            StudioVisualBlendMode.ADD -> min(1f, source + destination)
            StudioVisualBlendMode.MULTIPLY -> source * destination
            StudioVisualBlendMode.SCREEN -> 1f - (1f - source) * (1f - destination)
            StudioVisualBlendMode.OVERLAY -> if (destination <= 0.5f) 2f * source * destination
                else 1f - 2f * (1f - source) * (1f - destination)
            StudioVisualBlendMode.DARKEN -> min(source, destination)
            StudioVisualBlendMode.LIGHTEN -> max(source, destination)
        }
}

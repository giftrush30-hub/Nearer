package com.nearer.app

internal data class StudioAlphaMask(val width: Int, val height: Int, val alpha: ByteArray) {
    init { require(alpha.size == width * height) }
    fun alphaAt(x: Int, y: Int): Int = alpha[y * width + x].toInt() and 0xFF
}

/**
 * Rasterizes normalized rectangle, ellipse and polygon masks to an 8-bit alpha plane.
 * This is a CPU-side reference implementation suitable for small preview masks and deterministic
 * tests; full-resolution export should upload/cache the result as a GPU texture.
 */
internal object StudioMaskRasterizer {
    const val MAX_PIXELS = 16_777_216

    fun rasterize(layer: StudioMaskLayer, width: Int, height: Int): StudioAlphaMask {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
            "Mask dimensions must be positive and no larger than 16 megapixels."
        }
        val validation = StudioKeyframeEvaluator.validateMask(layer)
        require(validation.isValid) { validation.errors.joinToString() }
        val polygon = toPixelPolygon(layer.mask, width, height)
        val featherPixels = layer.feather * minOf(width, height)
        val result = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = x + 0.5
                val py = y + 0.5
                val inside = contains(polygon, px, py)
                val distance = polygon.indices.minOf { index ->
                    val point = polygon[index]
                    val next = polygon[(index + 1) % polygon.size]
                    distanceToSegment(px, py, point.first, point.second, next.first, next.second)
                }
                val signedDistance = if (inside) distance else -distance
                var alpha = if (featherPixels <= 0.001f) {
                    if (inside) 1f else 0f
                } else {
                    val t = (signedDistance / featherPixels + 0.5).coerceIn(0.0, 1.0).toFloat()
                    t * t * (3f - 2f * t)
                }
                if (layer.inverted) alpha = 1f - alpha
                result[y * width + x] = (alpha * 255f).toInt().coerceIn(0, 255).toByte()
            }
        }
        return StudioAlphaMask(width, height, result)
    }

    private fun toPixelPolygon(mask: StudioMask, width: Int, height: Int): List<Pair<Double, Double>> =
        when (mask) {
            is StudioMask.Rectangle -> listOf(
                mask.left to mask.top, mask.right to mask.top,
                mask.right to mask.bottom, mask.left to mask.bottom
            ).map { (x, y) -> x * width to y * height }
            is StudioMask.Ellipse -> (0 until 96).map { index ->
                val angle = 2.0 * Math.PI * index / 96.0
                (mask.centerX + kotlin.math.cos(angle) * mask.radiusX) * width to
                    (mask.centerY + kotlin.math.sin(angle) * mask.radiusY) * height
            }
            is StudioMask.Polygon -> mask.points.map { it.x * width to it.y * height }
        }

    private fun contains(points: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        var previous = points.lastIndex
        for (current in points.indices) {
            val a = points[current]
            val b = points[previous]
            if ((a.second > y) != (b.second > y) &&
                x < (b.first - a.first) * (y - a.second) / (b.second - a.second) + a.first) {
                inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun distanceToSegment(
        px: Double, py: Double,
        ax: Double, ay: Double, bx: Double, by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-12) return kotlin.math.hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}

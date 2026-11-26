package com.nearer.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class StudioParticleRasterization(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val blendMode: StudioVisualBlendMode,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun asPixelLayer(id: String, opacity: Float = 1f): StudioPixelLayer? {
        if (!isValid || id.isBlank()) return null
        return StudioPixelLayer(id, pixels, opacity, blendMode)
    }
}

/**
 * Rasterizes deterministic particle instances into a local RGBA frame. The output can be passed
 * directly to StudioPixelCompositor and later uploaded as a Media3 texture overlay.
 */
internal object StudioParticleRasterizer {
    const val MAX_RASTER_OPERATIONS = 100_000_000L
    private const val EDGE_SOFTNESS_START = 0.72f

    fun rasterize(
        width: Int,
        height: Int,
        frame: StudioParticleFrame,
        blendMode: StudioVisualBlendMode = StudioVisualBlendMode.NORMAL
    ): StudioParticleRasterization {
        if (width <= 0 || height <= 0 || width.toLong() * height > StudioPixelCompositor.MAX_PIXELS) {
            return invalid(width, height, blendMode, "Particle raster dimensions exceed the 16-megapixel safety limit.")
        }
        if (!frame.isValid) {
            return invalid(width, height, blendMode, frame.errors.joinToString(" ").ifBlank { "Particle frame is invalid." })
        }
        if (frame.particles.size > StudioOfflineParticleEngine.MAX_PARTICLES_PER_FRAME) {
            return invalid(width, height, blendMode, "Particle frame exceeds the 50000-instance safety limit.")
        }
        val output = IntArray(width * height)
        val minDimension = min(width, height).toFloat()
        var operations = 0L
        for (particle in frame.particles) {
            if (!particle.x.isFinite() || !particle.y.isFinite() || !particle.size.isFinite() ||
                !particle.opacity.isFinite() || !particle.rotationDegrees.isFinite()
            ) return invalid(width, height, blendMode, "Particle contains a non-finite render value.")
            if (particle.size <= 0f || particle.opacity !in 0f..1f) {
                return invalid(width, height, blendMode, "Particle size or opacity is outside its valid range.")
            }
            val centerX = ((particle.x + 1f) * 0.5f * (width - 1)).roundToInt()
            val centerY = ((1f - particle.y) * 0.5f * (height - 1)).roundToInt()
            val radius = max(1f, particle.size * minDimension * 0.5f)
            val isStreak = particle.preset == StudioParticlePreset.SPARKS ||
                particle.preset == StudioParticlePreset.LIGHTNING
            val radiusX = if (isStreak) max(1f, radius * 0.42f) else radius
            val radiusY = if (isStreak) max(1f, radius * if (particle.preset == StudioParticlePreset.LIGHTNING) 4f else 2.6f) else radius
            val angle = particle.rotationDegrees * (PI.toFloat() / 180f)
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            val bound = max(radiusX, radiusY).roundToInt() + 1
            val left = max(0, centerX - bound)
            val right = min(width - 1, centerX + bound)
            val top = max(0, centerY - bound)
            val bottom = min(height - 1, centerY + bound)
            if (left > right || top > bottom) continue
            val area = (right - left + 1).toLong() * (bottom - top + 1).toLong()
            operations += area
            if (operations > MAX_RASTER_OPERATIONS) {
                return invalid(width, height, blendMode, "Particle raster exceeds the 100-million-pixel work budget.")
            }
            val colorAlpha = (particle.colorArgb ushr 24) and 0xFF
            if (colorAlpha == 0 || particle.opacity == 0f) continue
            for (y in top..bottom) {
                for (x in left..right) {
                    val dx = x - centerX.toFloat()
                    val dy = y - centerY.toFloat()
                    val localX = dx * cosAngle + dy * sinAngle
                    val localY = -dx * sinAngle + dy * cosAngle
                    val normalized = sqrt(
                        (localX / radiusX) * (localX / radiusX) +
                            (localY / radiusY) * (localY / radiusY)
                    )
                    if (normalized >= 1f) continue
                    val edge = if (normalized <= EDGE_SOFTNESS_START) 1f else {
                        ((1f - normalized) / (1f - EDGE_SOFTNESS_START)).coerceIn(0f, 1f)
                    }
                    val alpha = (colorAlpha * particle.opacity * edge).roundToInt().coerceIn(0, 255)
                    if (alpha == 0) continue
                    val color = (particle.colorArgb and 0x00FFFFFF) or (alpha shl 24)
                    val index = y * width + x
                    output[index] = sourceOver(color, output[index])
                }
            }
        }
        return StudioParticleRasterization(width, height, output, blendMode, emptyList())
    }

    private fun sourceOver(source: Int, destination: Int): Int {
        val sourceAlpha = ((source ushr 24) and 0xFF) / 255f
        if (sourceAlpha >= 1f) return source
        if (sourceAlpha <= 0f) return destination
        val destinationAlpha = ((destination ushr 24) and 0xFF) / 255f
        val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
        fun channel(shift: Int): Int {
            val sourceChannel = ((source ushr shift) and 0xFF).toFloat()
            val destinationChannel = ((destination ushr shift) and 0xFF).toFloat()
            val premultiplied = sourceChannel * sourceAlpha +
                destinationChannel * destinationAlpha * (1f - sourceAlpha)
            return (premultiplied / outputAlpha).roundToInt().coerceIn(0, 255)
        }
        return (outputAlpha * 255f).roundToInt().coerceIn(0, 255).let { alpha ->
            (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }

    private fun invalid(
        width: Int,
        height: Int,
        blendMode: StudioVisualBlendMode,
        message: String
    ) = StudioParticleRasterization(width, height, IntArray(0), blendMode, listOf(message))
}

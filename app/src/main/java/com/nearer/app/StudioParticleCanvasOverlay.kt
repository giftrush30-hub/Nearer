package com.nearer.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.media3.effect.CanvasOverlay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Timestamp-driven particle overlay shared by the timeline preview and Transformer export. */
@androidx.media3.common.util.UnstableApi
internal class StudioParticleCanvasOverlay(
    private val emitter: StudioParticleEmitter
) : CanvasOverlay(true) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (presentationTimeUs < 0L || presentationTimeUs >= emitter.endTimeUs) return
        val frame = StudioOfflineParticleEngine.frameAt(presentationTimeUs, listOf(emitter))
        if (!frame.isValid || frame.particles.isEmpty()) return
        val minDimension = min(canvas.width, canvas.height).toFloat()
        frame.particles.forEach { particle ->
            val x = ((particle.x + 1f) * 0.5f * canvas.width).coerceIn(-minDimension, canvas.width + minDimension)
            val y = ((1f - particle.y) * 0.5f * canvas.height).coerceIn(-minDimension, canvas.height + minDimension)
            val radius = max(1f, particle.size * minDimension * 0.5f)
            val sourceAlpha = Color.alpha(particle.colorArgb)
            val red = Color.red(particle.colorArgb)
            val green = Color.green(particle.colorArgb)
            val blue = Color.blue(particle.colorArgb)
            val alpha = (sourceAlpha * particle.opacity).toInt().coerceIn(0, 255)
            if (alpha == 0) return@forEach
            paint.color = Color.argb((alpha * 0.18f).toInt(), red, green, blue)
            if (particle.preset == StudioParticlePreset.FIRE || particle.preset == StudioParticlePreset.EXPLOSION) {
                canvas.drawCircle(x, y, radius * 1.8f, paint)
            } else if (particle.preset == StudioParticlePreset.SMOKE) {
                canvas.drawCircle(x, y, radius * 1.45f, paint)
            }
            paint.color = Color.argb(alpha, red, green, blue)
            if (particle.preset == StudioParticlePreset.SPARKS || particle.preset == StudioParticlePreset.LIGHTNING) {
                val angle = Math.toRadians(particle.rotationDegrees.toDouble())
                val halfLength = radius * if (particle.preset == StudioParticlePreset.LIGHTNING) 4f else 2.6f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, radius * 0.7f)
                canvas.drawLine(
                    x - cos(angle).toFloat() * halfLength,
                    y - sin(angle).toFloat() * halfLength,
                    x + cos(angle).toFloat() * halfLength,
                    y + sin(angle).toFloat() * halfLength,
                    paint
                )
                paint.style = Paint.Style.FILL
            } else {
                canvas.drawCircle(x, y, radius, paint)
            }
        }
    }
}

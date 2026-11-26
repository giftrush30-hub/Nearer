package com.nearer.app

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.GlEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings

/** Timestamp-driven bitmap overlay backed by the local deterministic particle engine. */
@UnstableApi
internal class StudioParticleBitmapOverlay(
    private val emitters: List<StudioParticleEmitter>
) : BitmapOverlay() {
    private var outputBitmap: Bitmap? = null
    private var outputWidth = 0
    private var outputHeight = 0

    override fun configure(videoSize: Size) {
        require(videoSize.width > 0 && videoSize.height > 0) { "Particle overlay received invalid video dimensions." }
        require(videoSize.width.toLong() * videoSize.height <= StudioPixelCompositor.MAX_PIXELS) {
            "Particle overlay exceeds the 16-megapixel safety limit."
        }
        if (videoSize.width != outputWidth || videoSize.height != outputHeight || outputBitmap == null) {
            outputBitmap?.recycle()
            outputWidth = videoSize.width
            outputHeight = videoSize.height
            outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = outputBitmap ?: throw VideoFrameProcessingException(
            "Particle overlay was not configured before frame rendering.", presentationTimeUs
        )
        val particleFrame = StudioOfflineParticleEngine.frameAt(presentationTimeUs, emitters)
        if (!particleFrame.isValid) {
            throw VideoFrameProcessingException(
                particleFrame.errors.joinToString(" ").ifBlank { "Particle simulation failed." },
                presentationTimeUs
            )
        }
        val raster = StudioParticleRasterizer.rasterize(outputWidth, outputHeight, particleFrame)
        if (!raster.isValid) {
            throw VideoFrameProcessingException(
                raster.errors.joinToString(" ").ifBlank { "Particle rasterization failed." },
                presentationTimeUs
            )
        }
        bitmap.setPixels(raster.pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .setScale(1f, 1f)
            .setAlphaScale(1f)
            .build()

    override fun release() {
        super.release()
        outputBitmap?.recycle()
        outputBitmap = null
        outputWidth = 0
        outputHeight = 0
    }
}

@UnstableApi
internal object StudioParticleMedia3OverlayFactory {
    fun create(emitters: List<StudioParticleEmitter>): GlEffect {
        require(emitters.isNotEmpty()) { "At least one particle emitter is required." }
        val validation = StudioOfflineParticleEngine.validate(emitters)
        require(validation.isEmpty()) { validation.joinToString(" ") }
        return OverlayEffect(listOf(StudioParticleBitmapOverlay(emitters)))
    }
}

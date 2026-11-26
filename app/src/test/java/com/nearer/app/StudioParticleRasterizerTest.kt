package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioParticleRasterizerTest {
    private fun particle(
        preset: StudioParticlePreset = StudioParticlePreset.FIRE,
        x: Float = 0f,
        y: Float = 0f,
        size: Float = 0.2f,
        color: Int = 0xFFFF4000.toInt(),
        opacity: Float = 1f,
        rotation: Float = 0f
    ) = StudioParticleInstance(
        emitterId = "test",
        particleIndex = 0,
        x = x,
        y = y,
        size = size,
        opacity = opacity,
        rotationDegrees = rotation,
        colorArgb = color,
        preset = preset
    )

    @Test fun rasterizesSoftParticleAtNormalizedFramePosition() {
        val frame = StudioParticleFrame(0L, listOf(particle()), emptyList())
        val raster = StudioParticleRasterizer.rasterize(64, 64, frame)
        assertTrue(raster.isValid)
        assertEquals(64 * 64, raster.pixels.size)
        val center = raster.pixels[32 * 64 + 32]
        assertEquals(255, (center ushr 24) and 0xFF)
        assertEquals(255, (center ushr 16) and 0xFF)
    }

    @Test fun rasterizationIsDeterministicAndCanBecomePixelLayer() {
        val frame = StudioParticleFrame(100L, listOf(particle(), particle(x = -0.4f, y = 0.3f)), emptyList())
        val a = StudioParticleRasterizer.rasterize(80, 60, frame, StudioVisualBlendMode.SCREEN)
        val b = StudioParticleRasterizer.rasterize(80, 60, frame, StudioVisualBlendMode.SCREEN)
        assertTrue(a.isValid)
        assertTrue(a.pixels.contentEquals(b.pixels))
        val layer = a.asPixelLayer("particles")
        assertNotNull(layer)
        assertEquals(StudioVisualBlendMode.SCREEN, layer!!.blendMode)
        assertNull(a.asPixelLayer(""))
    }

    @Test fun clipsParticlesOutsideTheFrameWithoutFailing() {
        val frame = StudioParticleFrame(0L, listOf(particle(x = 5f, y = 5f)), emptyList())
        val raster = StudioParticleRasterizer.rasterize(32, 32, frame)
        assertTrue(raster.isValid)
        assertTrue(raster.pixels.all { it == 0 })
    }

    @Test fun rendersSparksAsNarrowStreaks() {
        val frame = StudioParticleFrame(
            0L,
            listOf(particle(preset = StudioParticlePreset.SPARKS, size = 0.25f, rotation = 0f)),
            emptyList()
        )
        val raster = StudioParticleRasterizer.rasterize(96, 96, frame)
        assertTrue(raster.isValid)
        val vertical = raster.pixels[24 * 96 + 48]
        val horizontal = raster.pixels[48 * 96 + 58]
        assertTrue((vertical ushr 24) > 0)
        assertEquals(0, (horizontal ushr 24))
    }

    @Test fun reusesAndClearsProvidedPixelBufferBetweenFrames() {
        val buffer = IntArray(16 * 16) { 0xFFFFFFFF.toInt() }
        val emptyFrame = StudioParticleFrame(0L, emptyList(), emptyList())
        val raster = StudioParticleRasterizer.rasterize(16, 16, emptyFrame, reusablePixels = buffer)
        assertTrue(raster.isValid)
        assertTrue(raster.pixels === buffer)
        assertTrue(buffer.all { it == 0 })
        val mismatch = StudioParticleRasterizer.rasterize(16, 16, emptyFrame, reusablePixels = IntArray(4))
        assertFalse(mismatch.isValid)
    }

    @Test fun rejectsInvalidFramesDimensionsAndNonFiniteParticles() {
        val invalidFrame = StudioParticleFrame(0L, emptyList(), listOf("bad simulation"))
        assertFalse(StudioParticleRasterizer.rasterize(32, 32, invalidFrame).isValid)
        assertFalse(StudioParticleRasterizer.rasterize(0, 32, StudioParticleFrame(0L, emptyList(), emptyList())).isValid)
        val oversized = StudioParticleFrame(0L, listOf(particle(size = 2.5f)), emptyList())
        assertFalse(StudioParticleRasterizer.rasterize(32, 32, oversized).isValid)
        val nonFinite = StudioParticleFrame(0L, listOf(particle(x = Float.NaN)), emptyList())
        val rejected = StudioParticleRasterizer.rasterize(32, 32, nonFinite)
        assertFalse(rejected.isValid)
        assertTrue(rejected.pixels.isEmpty())
    }

    @Test fun rasterizedParticleLayerCompositesWithExistingPixelEngine() {
        val frame = StudioParticleFrame(0L, listOf(particle()), emptyList())
        val particles = StudioParticleRasterizer.rasterize(16, 16, frame).asPixelLayer("particles")
        assertNotNull(particles)
        val base = IntArray(16 * 16) { 0xFF000000.toInt() }
        val result = StudioPixelCompositor.compose(16, 16, listOf(
            StudioPixelLayer("background", base),
            particles!!
        ))
        assertTrue(result.isValid)
        assertEquals(0xFFFF4000.toInt(), result.pixels[8 * 16 + 8])
    }
}

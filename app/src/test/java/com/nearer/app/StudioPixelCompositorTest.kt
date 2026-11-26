package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPixelCompositorTest {
    private fun pixel(red: Int, green: Int, blue: Int, alpha: Int = 255) =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    @Test fun compositesOpaqueNormalLayerOverBackground() {
        val background = IntArray(1) { pixel(0, 0, 255) }
        val foreground = IntArray(1) { pixel(255, 0, 0) }
        val result = StudioPixelCompositor.compose(1, 1, listOf(
            StudioPixelLayer("background", background),
            StudioPixelLayer("foreground", foreground)
        ))
        assertTrue(result.isValid)
        assertEquals(pixel(255, 0, 0), result.pixels.single())
    }

    @Test fun appliesOpacityAndMaskAlphaToSourceAlpha() {
        val background = IntArray(1) { pixel(0, 0, 0) }
        val foreground = IntArray(1) { pixel(255, 255, 255) }
        val mask = StudioAlphaMask(1, 1, byteArrayOf(0x80.toByte()))
        val result = StudioPixelCompositor.compose(1, 1, listOf(
            StudioPixelLayer("background", background),
            StudioPixelLayer("masked", foreground, opacity = 0.5f, mask = mask)
        ))
        assertTrue(result.isValid)
        val alpha = (result.pixels.single() ushr 24) and 0xFF
        val red = (result.pixels.single() ushr 16) and 0xFF
        assertEquals(64, alpha)
        assertTrue(red in 250..255)
    }

    @Test fun supportsAllDeclaredBlendModes() {
        val modes = StudioVisualBlendMode.entries
        modes.forEach { mode ->
            val result = StudioPixelCompositor.compose(1, 1, listOf(
                StudioPixelLayer("base", intArrayOf(pixel(80, 120, 160))),
                StudioPixelLayer("overlay", intArrayOf(pixel(180, 100, 40)), blendMode = mode)
            ))
            assertTrue("Mode $mode should render", result.isValid)
            assertEquals(1, result.pixels.size)
        }
    }

    @Test fun appliesLayerOffsetsAndClipsOutsideFrame() {
        val source = IntArray(4) { pixel(255, 0, 0) }
        val result = StudioPixelCompositor.compose(2, 2, listOf(
            StudioPixelLayer("shifted", source, offsetX = 1, offsetY = 1)
        ))
        assertTrue(result.isValid)
        assertEquals(pixel(0, 0, 0, 0), result.pixels[0])
        assertEquals(pixel(0, 0, 0, 0), result.pixels[1])
        assertEquals(pixel(0, 0, 0, 0), result.pixels[2])
        assertEquals(pixel(255, 0, 0), result.pixels[3])
    }

    @Test fun rejectsInvalidDimensionsPixelCountsAndOpacity() {
        val invalid = StudioPixelCompositor.compose(2, 2, listOf(
            StudioPixelLayer("bad", intArrayOf(1, 2), opacity = 2f)
        ))
        assertFalse(invalid.isValid)
        assertTrue(invalid.pixels.isEmpty())
        assertTrue(invalid.errors.any { it.contains("pixel count") })
        assertTrue(invalid.errors.any { it.contains("opacity") })
    }

    @Test fun rejectsMaskWithDifferentDimensions() {
        val result = StudioPixelCompositor.compose(2, 2, listOf(
            StudioPixelLayer("bad-mask", IntArray(4), mask = StudioAlphaMask(1, 1, byteArrayOf(0xFF.toByte())))
        ))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("mask dimensions") })
    }
}

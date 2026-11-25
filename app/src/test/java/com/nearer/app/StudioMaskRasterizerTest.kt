package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMaskRasterizerTest {
    @Test fun rasterizesRectangleToHardAlphaMask() {
        val mask = StudioMaskRasterizer.rasterize(
            StudioMaskLayer("rect", StudioMask.Rectangle(0.25f, 0.25f, 0.75f, 0.75f)),
            20, 20
        )
        assertEquals(255, mask.alphaAt(10, 10))
        assertEquals(0, mask.alphaAt(1, 1))
        assertEquals(0, mask.alphaAt(5, 10))
    }

    @Test fun supportsInvertedMasks() {
        val mask = StudioMaskRasterizer.rasterize(
            StudioMaskLayer("inverted", StudioMask.Rectangle(0.25f, 0.25f, 0.75f, 0.75f), inverted = true),
            20, 20
        )
        assertEquals(0, mask.alphaAt(10, 10))
        assertEquals(255, mask.alphaAt(1, 1))
    }

    @Test fun rasterizesEllipseAndPolygon() {
        val ellipse = StudioMaskRasterizer.rasterize(
            StudioMaskLayer("ellipse", StudioMask.Ellipse(0.5f, 0.5f, 0.3f, 0.2f)), 40, 40
        )
        assertEquals(255, ellipse.alphaAt(20, 20))
        assertEquals(0, ellipse.alphaAt(2, 2))
        val polygon = StudioMaskRasterizer.rasterize(
            StudioMaskLayer("triangle", StudioMask.Polygon(listOf(
                StudioMask.Point(0.1f, 0.1f), StudioMask.Point(0.9f, 0.1f), StudioMask.Point(0.5f, 0.9f)
            ))), 40, 40
        )
        assertEquals(255, polygon.alphaAt(20, 12))
        assertEquals(0, polygon.alphaAt(2, 36))
    }

    @Test fun featherProducesPartialAlphaNearTheEdge() {
        val mask = StudioMaskRasterizer.rasterize(
            StudioMaskLayer("soft", StudioMask.Rectangle(0.25f, 0.25f, 0.75f, 0.75f), feather = 0.2f),
            40, 40
        )
        assertEquals(255, mask.alphaAt(20, 20))
        assertTrue(mask.alphaAt(10, 20) in 1..254)
        assertEquals(0, mask.alphaAt(1, 1))
    }

    @Test fun rejectsInvalidMasksAndOversizedBuffers() {
        assertTrue(runCatching {
            StudioMaskRasterizer.rasterize(StudioMaskLayer("bad", StudioMask.Rectangle(0.8f, 0f, 0.2f, 1f)), 20, 20)
        }.isFailure)
        assertTrue(runCatching {
            StudioMaskRasterizer.rasterize(StudioMaskLayer("large", StudioMask.Rectangle(0f, 0f, 1f, 1f)), 5_000, 5_000)
        }.isFailure)
    }
}

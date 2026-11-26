package com.nearer.app

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioGpuMaskTexturePlannerTest {
    @Test fun preservesSingleRectangleMaskInsideAndOutside() {
        val mask = StudioMaskLayer("rect", StudioMask.Rectangle(0.25f, 0.25f, 0.75f, 0.75f))
        val result = StudioGpuMaskTexturePlanner.rasterizeCombined(listOf(mask), 8, 8)
        assertEquals(8, result.width)
        assertEquals(8, result.height)
        assertEquals(255, result.alphaAt(3, 3))
        assertEquals(0, result.alphaAt(0, 0))
    }

    @Test fun intersectsMultipleMasksInsteadOfReplacingEarlierMask() {
        val first = StudioMaskLayer("left", StudioMask.Rectangle(0f, 0f, 0.75f, 1f))
        val second = StudioMaskLayer("right", StudioMask.Rectangle(0.25f, 0f, 1f, 1f))
        val result = StudioGpuMaskTexturePlanner.rasterizeCombined(listOf(first, second), 8, 8)
        assertEquals(255, result.alphaAt(3, 3))
        assertEquals(0, result.alphaAt(0, 3))
        assertEquals(0, result.alphaAt(7, 3))
    }

    @Test fun invertedMaskKeepsTheOutsideRegion() {
        val mask = StudioMaskLayer("hole", StudioMask.Rectangle(0.25f, 0.25f, 0.75f, 0.75f), inverted = true)
        val result = StudioGpuMaskTexturePlanner.rasterizeCombined(listOf(mask), 8, 8)
        assertEquals(0, result.alphaAt(3, 3))
        assertEquals(255, result.alphaAt(0, 0))
    }

    @Test fun rejectsEmptyExcessiveAndInvalidMasks() {
        assertTrue(runCatching { StudioGpuMaskTexturePlanner.rasterizeCombined(emptyList(), 8, 8) }.isFailure)
        val many = (0..8).map { StudioMaskLayer("m" + it, StudioMask.Rectangle(0f, 0f, 1f, 1f)) }
        assertTrue(runCatching { StudioGpuMaskTexturePlanner.rasterizeCombined(many, 8, 8) }.isFailure)
        val invalid = StudioMaskLayer("bad", StudioMask.Rectangle(0.8f, 0f, 0.2f, 1f))
        assertTrue(runCatching { StudioGpuMaskTexturePlanner.rasterizeCombined(listOf(invalid), 8, 8) }.isFailure)
    }
}

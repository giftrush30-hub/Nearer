package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class StudioGpuBlendPlannerTest {
    private fun input(id: Int, z: Int, mode: StudioVisualBlendMode = StudioVisualBlendMode.NORMAL,
        opacity: Float = 1f, timeUs: Long = 1_000_000L, width: Int = 1920, height: Int = 1080) =
        StudioGpuBlendInput(id, z, mode, opacity, timeUs, width, height)

    @Test fun ordersPassesByZIndexAndStableInputId() {
        val r = StudioGpuBlendPlanner.plan(listOf(
            input(2, 5, StudioVisualBlendMode.SCREEN), input(1, 1, StudioVisualBlendMode.MULTIPLY), input(0, 1)))
        assertTrue(r.isValid); assertEquals(listOf(0, 1, 2), r.passes.map { it.inputId })
        assertEquals(listOf(0, 2, 3), r.passes.map { it.modeCode })
        assertTrue(r.requiresNonNormalBlend); assertEquals(1920, r.outputWidth); assertEquals(1080, r.outputHeight)
    }
    @Test fun mapsAllCpuModesToStableShaderCodes() {
        StudioVisualBlendMode.entries.forEachIndexed { i, mode -> assertEquals(i, StudioGpuBlendModeCode.from(mode)) }
    }
    @Test fun rejectsInputsOutsideTimestampTolerance() {
        val r = StudioGpuBlendPlanner.plan(listOf(input(0, 0), input(1, 1, timeUs = 1_002_001L)))
        assertFalse(r.isValid); assertTrue(r.errors.any { it.contains("frame-synchronized") })
    }
    @Test fun acceptsTimestampDifferenceAtToleranceBoundary() {
        assertTrue(StudioGpuBlendPlanner.plan(listOf(input(0, 0), input(1, 1, timeUs = 1_001_000L))).isValid)
    }
    @Test fun rejectsDuplicateIdsInvalidOpacityDimensionsAndNegativeTimestamps() {
        val r = StudioGpuBlendPlanner.plan(listOf(input(0, 0), input(0, 1, opacity = 1.2f, width = 0, timeUs = -1L)))
        assertFalse(r.isValid); assertTrue(r.errors.any { it.contains("duplicated") })
        assertTrue(r.errors.any { it.contains("opacity") }); assertTrue(r.errors.any { it.contains("dimensions") })
        assertTrue(r.errors.any { it.contains("negative") })
    }
    @Test fun capsSimultaneousGpuInputs() {
        val r = StudioGpuBlendPlanner.plan((0..StudioGpuBlendPlanner.MAX_SIMULTANEOUS_LAYERS).map { input(it, it) })
        assertFalse(r.isValid); assertTrue(r.errors.any { it.contains("8 simultaneous") })
    }
}

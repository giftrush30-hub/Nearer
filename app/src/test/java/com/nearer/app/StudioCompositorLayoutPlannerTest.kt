package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioCompositorLayoutPlannerTest {
    @Test fun convertsFullFrameAndPictureInPictureBounds() {
        val result = StudioCompositorLayoutPlanner.plan(listOf(
            StudioCompositorLayerLayout(0, -1f, -1f, 1f, 1f, zIndex = 0),
            StudioCompositorLayerLayout(1, 0.2f, -0.8f, 0.9f, -0.1f, rotationDegrees = -90f, opacity = 0.7f, zIndex = 2)
        ))
        assertTrue(result.isValid)
        val full = result.placements.first { it.inputId == 0 }
        assertEquals(0f, full.centerX, 0f)
        assertEquals(0f, full.centerY, 0f)
        assertEquals(1f, full.scaleX, 0f)
        assertEquals(1f, full.scaleY, 0f)
        val pip = result.placements.first { it.inputId == 1 }
        assertEquals(0.55f, pip.centerX, 0.0001f)
        assertEquals(-0.45f, pip.centerY, 0.0001f)
        assertEquals(0.35f, pip.scaleX, 0.0001f)
        assertEquals(0.35f, pip.scaleY, 0.0001f)
        assertEquals(270f, pip.rotationDegrees, 0.0001f)
        assertEquals(0.7f, pip.opacity, 0f)
    }

    @Test fun ordersPlacementsByZIndexThenInputId() {
        val result = StudioCompositorLayoutPlanner.plan(listOf(
            StudioCompositorLayerLayout(2, -1f, -1f, 1f, 1f, zIndex = 5),
            StudioCompositorLayerLayout(1, -1f, -1f, 1f, 1f, zIndex = 1),
            StudioCompositorLayerLayout(0, -1f, -1f, 1f, 1f, zIndex = 1)
        ))
        assertTrue(result.isValid)
        assertEquals(listOf(0, 1, 2), result.placements.map { it.inputId })
    }

    @Test fun rejectsDuplicateIdsInvalidBoundsOpacityAndNonFiniteValues() {
        val result = StudioCompositorLayoutPlanner.plan(listOf(
            StudioCompositorLayerLayout(0, 0f, -1f, -0.5f, 1f),
            StudioCompositorLayerLayout(0, -1f, -1f, 1f, 1f, opacity = 1.5f),
            StudioCompositorLayerLayout(2, Float.NaN, -1f, 1f, 1f)
        ))
        assertFalse(result.isValid)
        assertTrue(result.placements.isEmpty())
        assertTrue(result.errors.any { it.contains("duplicated") })
        assertTrue(result.errors.any { it.contains("bounds") })
        assertTrue(result.errors.any { it.contains("opacity") })
        assertTrue(result.errors.any { it.contains("non-finite") })
    }

    @Test fun rejectsOutOfRangeInputAndMoreThanThirtyTwoInputs() {
        val outOfRange = StudioCompositorLayoutPlanner.plan(
            listOf(StudioCompositorLayerLayout(32, -1f, -1f, 1f, 1f))
        )
        assertFalse(outOfRange.isValid)
        val tooMany = (0..StudioCompositorLayoutPlanner.MAX_INPUTS).map {
            StudioCompositorLayerLayout(it, -1f, -1f, 1f, 1f)
        }
        assertTrue(StudioCompositorLayoutPlanner.plan(tooMany).errors.any { it.contains("32 visual-input") })
    }

    @Test fun rejectsInvalidRotationRange() {
        val result = StudioCompositorLayoutPlanner.plan(
            listOf(StudioCompositorLayerLayout(0, -1f, -1f, 1f, 1f, rotationDegrees = 36_001f))
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("rotation") })
    }
}

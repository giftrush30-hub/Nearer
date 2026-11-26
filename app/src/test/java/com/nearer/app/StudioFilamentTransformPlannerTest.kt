package com.nearer.app

import org.junit.Assert.assertTrue
import org.junit.Test

class StudioFilamentTransformPlannerTest {
    @Test fun acceptsDefaultAndBoundarySettings() {
        assertTrue(StudioFilamentTransformPlanner.validate(StudioFilamentTransformSettings()).isEmpty())
        assertTrue(StudioFilamentTransformPlanner.validate(
            StudioFilamentTransformSettings(scale = 4f, rotationYDegrees = 360f, verticalOffset = 1f)
        ).isEmpty())
    }

    @Test fun rejectsNonFiniteAndOutOfRangeSettings() {
        val errors = StudioFilamentTransformPlanner.validate(
            StudioFilamentTransformSettings(scale = Float.NaN, rotationYDegrees = 500f, verticalOffset = -2f)
        )
        assertTrue(errors.any { it.contains("scale") })
        assertTrue(errors.any { it.contains("rotation") })
        assertTrue(errors.any { it.contains("height") })
    }
}

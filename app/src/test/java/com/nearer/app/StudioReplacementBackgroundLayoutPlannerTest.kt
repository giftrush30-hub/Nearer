package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioReplacementBackgroundLayoutPlannerTest {
    @Test fun fullFrameLayoutKeepsBothInputsFullSizeWithForegroundOnTop() {
        val result = StudioReplacementBackgroundLayoutPlanner.layouts(StudioReplacementBackgroundLayout.FULL_FRAME)
        assertEquals(2, result.size)
        assertEquals(0, result[0].inputId)
        assertEquals(1, result[1].inputId)
        assertEquals(1f, result[1].right, 0f)
        assertEquals(-1f, result[1].bottom, 0f)
    }

    @Test fun pictureInPictureLayoutPlacesBackgroundInLowerRightWithoutMovingForeground() {
        val result = StudioReplacementBackgroundLayoutPlanner.layouts(StudioReplacementBackgroundLayout.PICTURE_IN_PICTURE)
        val foreground = result.first { it.inputId == 0 }
        val background = result.first { it.inputId == 1 }
        assertEquals(-1f, foreground.left, 0f)
        assertEquals(1f, foreground.right, 0f)
        assertTrue(background.left > 0f)
        assertTrue(background.right <= 1f)
        assertTrue(background.bottom < 0f)
        assertTrue(background.top < 0f)
        assertTrue(background.left < background.right)
        assertTrue(background.bottom < background.top)
    }
}

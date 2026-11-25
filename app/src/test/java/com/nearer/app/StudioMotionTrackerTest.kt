package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMotionTrackerTest {
    private fun frame(centerX: Int, centerY: Int, blank: Boolean = false): StudioGrayFrame {
        val width = 48
        val height = 48
        val pixels = ByteArray(width * height) { 8 }
        if (!blank) {
            for (dy in -4..4) for (dx in -4..4) {
                val value = (35 + ((dx + 4) * 13 + (dy + 4) * 7) % 190).toByte()
                pixels[(centerY + dy) * width + centerX + dx] = value
            }
        }
        return StudioGrayFrame(width, height, pixels)
    }

    @Test fun followsAVisiblePatchAcrossFrames() {
        val result = StudioTemplateMotionTracker.track(
            listOf(frame(20, 20), frame(22, 21), frame(24, 22)),
            startX = 20f / 47f, startY = 20f / 47f,
            patchRadius = 4, searchRadius = 5, minimumConfidence = 0.8f
        )
        assertNull(result.lostAtFrame)
        assertEquals(3, result.points.size)
        assertEquals(24f / 47f, result.points.last().x, 0.001f)
        assertEquals(22f / 47f, result.points.last().y, 0.001f)
        assertTrue(result.points.all { it.isTracked })
    }

    @Test fun reportsTrackingLossWhenTheTemplateDisappears() {
        val result = StudioTemplateMotionTracker.track(
            listOf(frame(20, 20), frame(20, 20, blank = true)),
            startX = 20f / 47f, startY = 20f / 47f,
            patchRadius = 4, searchRadius = 5, minimumConfidence = 0.8f
        )
        assertEquals(1, result.lostAtFrame)
        assertFalse(result.points.last().isTracked)
    }

    @Test fun rejectsInvalidFrameDimensionsAndTrackingSettings() {
        val frame = frame(20, 20)
        val otherSize = StudioGrayFrame(32, 32, ByteArray(32 * 32))
        assertTrue(runCatching {
            StudioTemplateMotionTracker.track(listOf(frame, otherSize), 0.5f, 0.5f)
        }.isFailure)
        assertTrue(runCatching {
            StudioTemplateMotionTracker.track(listOf(frame), 0.5f, 0.5f, patchRadius = 1)
        }.isFailure)
        assertTrue(runCatching {
            StudioTemplateMotionTracker.track(listOf(frame), Float.NaN, 0.5f)
        }.isFailure)
    }

    @Test fun rejectsFramesTooSmallForSearchWindow() {
        val tiny = StudioGrayFrame(12, 12, ByteArray(12 * 12))
        assertTrue(runCatching {
            StudioTemplateMotionTracker.track(listOf(tiny), 0.5f, 0.5f, patchRadius = 4, searchRadius = 12)
        }.isFailure)
    }
}

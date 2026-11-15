package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioVideoGradeCodecTest {
    @Test
    fun roundTripPreservesGrade() {
        val grade = StudioVideoGrade(0.25f, -0.15f, 32f)
        assertEquals(grade, StudioVideoGradeCodec.decode(StudioVideoGradeCodec.encode(grade)))
    }

    @Test
    fun clampsUnsafeValues() {
        val grade = StudioVideoGrade(2f, -3f, 150f).normalized()
        assertEquals(0.5f, grade.brightness)
        assertEquals(-0.5f, grade.contrast)
        assertEquals(50f, grade.saturation)
    }

    @Test
    fun malformedAndUnknownVersionsResetSafely() {
        assertEquals(StudioVideoGrade(), StudioVideoGradeCodec.decode("{broken"))
        assertEquals(StudioVideoGrade(), StudioVideoGradeCodec.decode("""{"version":99}"""))
    }
}

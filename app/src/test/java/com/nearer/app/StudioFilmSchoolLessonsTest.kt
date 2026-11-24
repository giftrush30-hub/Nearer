package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioFilmSchoolLessonsTest {
    @Test fun lessonsHaveUniqueIdsAndUsefulSteps() {
        val lessons = StudioFilmSchoolLessons.all
        assertTrue(lessons.isNotEmpty())
        assertEquals(lessons.size, lessons.map { it.id }.toSet().size)
        lessons.forEach { lesson ->
            assertTrue(lesson.id.isNotBlank())
            assertTrue(lesson.title.isNotBlank())
            assertTrue(lesson.summary.isNotBlank())
            assertTrue(lesson.steps.isNotEmpty())
            assertFalse(lesson.steps.any { it.isBlank() })
            assertTrue(lesson.practice.isNotBlank())
        }
    }

    @Test fun firstMovieLessonUsesPlainLanguageAndStartsWithAnAction() {
        val lesson = StudioFilmSchoolLessons.all.first { it.id == "first-movie" }
        assertTrue(lesson.title.contains("first movie", ignoreCase = true))
        assertTrue(lesson.steps.first().contains("Open"))
        assertTrue(lesson.practice.contains("two or three videos"))
    }
}

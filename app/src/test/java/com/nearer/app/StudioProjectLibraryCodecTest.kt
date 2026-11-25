package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioProjectLibraryCodecTest {
    private fun project(
        id: String = "movie-1",
        title: String = "My first movie",
        payload: String = """{"version":1,"clips":[]}""",
        updatedAt: Long = 1234L
    ) = StudioProjectRecord(id, title, "VIDEO", updatedAt, payload)

    @Test fun roundTripKeepsNamedProjectsAndPayloads() {
        val original = StudioProjectLibrary(
            listOf(project(), project("movie-2", "A second movie", """{"version":2}""", 5678L))
        )
        val restored = StudioProjectLibraryCodec.decode(StudioProjectLibraryCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test fun duplicateIdsKeepTheFirstRecord() {
        val first = project(title = "First title")
        val second = project(title = "Second title", updatedAt = 9999L)
        val restored = StudioProjectLibraryCodec.decode(
            StudioProjectLibraryCodec.encode(StudioProjectLibrary(listOf(first, second)))
        )
        assertEquals(1, restored.projects.size)
        assertEquals("First title", restored.projects.single().title)
    }

    @Test fun invalidRecordsAreSkippedWithoutLosingValidProjects() {
        val raw = """{
            "version": 1,
            "projects": [
                {"id":"ok","title":"Valid","kind":"VIDEO","updatedAtMs":1,"payloadJson":"{}"},
                {"id":"","title":"No ID","kind":"VIDEO","updatedAtMs":2,"payloadJson":"{}"},
                {"id":"empty-payload","title":"No payload","kind":"VIDEO","updatedAtMs":3,"payloadJson":""},
                null
            ]
        }"""
        val restored = StudioProjectLibraryCodec.decode(raw)
        assertEquals(1, restored.projects.size)
        assertEquals("ok", restored.projects.single().id)
    }

    @Test fun blankOrUnknownVersionsProduceAnEmptyLibrary() {
        assertTrue(StudioProjectLibraryCodec.decode(null).projects.isEmpty())
        assertTrue(StudioProjectLibraryCodec.decode("not json").projects.isEmpty())
        assertTrue(StudioProjectLibraryCodec.decode("""{"version":99,"projects":[]}""").projects.isEmpty())
    }

    @Test fun negativeTimestampsAreNormalizedAndLongTitlesAreCapped() {
        val longTitle = "x".repeat(120)
        val raw = """{"version":1,"projects":[{"id":"x","title":"$longTitle","kind":"VIDEO","updatedAtMs":-10,"payloadJson":"{}"}]}"""
        val restored = StudioProjectLibraryCodec.decode(raw).projects.single()
        assertEquals(80, restored.title.length)
        assertEquals(0L, restored.updatedAtMs)
        assertFalse(restored.archived)
    }
}

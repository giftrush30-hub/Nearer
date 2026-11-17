package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioProjectLibraryCodecTest {
    private fun project(id: String, name: String, uri: String) = StudioAudioProjectLibraryEntry(
        id, name, 1234L, listOf(StudioAudioProjectTrackState(
            uri, "Lead", 60_000L, false, 0.8f, -0.2f, 500L, 1_000L
        ))
    )

    @Test fun roundTripsIndependentNamedProjectsAndTrackSettings() {
        val original = listOf(project("one", "Main Vocal", "content://audio/one"),
            project("two", "Beat & Effects", "content://audio/two"))
        assertEquals(original, StudioAudioProjectLibraryCodec.decode(StudioAudioProjectLibraryCodec.encode(original)))
    }

    @Test fun boundsProjectCountAndNormalizesNames() {
        val entries = (0..24).map { project("p$it", "  Project   $it  ", "content://audio/$it") }
        val restored = StudioAudioProjectLibraryCodec.decode(StudioAudioProjectLibraryCodec.encode(entries))
        assertEquals(20, restored.size)
        assertEquals("Project 0", restored.first().name)
        assertEquals(48, StudioAudioProjectLibraryCodec.normalizeName("x".repeat(100)).length)
    }

    @Test fun skipsDuplicateAndBlankIds() {
        val raw = """{"version":1,"projects":[
            {"id":"same","name":"First","tracksJson":"{\"version\":3,\"tracks\":[]}"},
            {"id":"same","name":"Duplicate","tracksJson":"{\"version\":3,\"tracks\":[]}"},
            {"id":" ","name":"Blank","tracksJson":"{\"version\":3,\"tracks\":[]}"}
        ]}"""
        val restored = StudioAudioProjectLibraryCodec.decode(raw)
        assertEquals(1, restored.size)
        assertEquals("First", restored.single().name)
    }

    @Test fun renameUpdatesNameTimestampAndKeepsCurrentTrackEdits() {
        val original = listOf(
            project("one", "Old name", "content://audio/old"),
            project("two", "Untouched", "content://audio/other")
        )
        val activeTracks = listOf(StudioAudioProjectTrackState(
            "content://audio/current", "Current vocal", 90_000L, false,
            0.65f, 0.25f, 250L, 750L
        ))

        val renamed = StudioAudioProjectLibraryCodec.rename(
            projects = original,
            projectId = "one",
            rawName = "  New   project  ",
            activeTracks = activeTracks,
            updatedAtMs = 9876L
        )
        val restored = StudioAudioProjectLibraryCodec.decode(StudioAudioProjectLibraryCodec.encode(renamed))

        assertEquals("New project", restored[0].name)
        assertEquals(9876L, restored[0].updatedAtMs)
        assertEquals(activeTracks, restored[0].tracks)
        assertEquals("Untouched", restored[1].name)
        assertEquals(original[1].tracks, restored[1].tracks)
    }

    @Test fun bpmAndBeatDivisionPersistIndependentlyPerProject() {
        val projects = listOf(
            project("one", "Fast Mix", "content://audio/one").copy(bpm = 137, beatDivision = 4, snapEnabled = false),
            project("two", "Slow Mix", "content://audio/two").copy(bpm = 82, beatDivision = 1)
        )
        val restored = StudioAudioProjectLibraryCodec.decode(StudioAudioProjectLibraryCodec.encode(projects))
        assertEquals(137, restored[0].bpm)
        assertEquals(4, restored[0].beatDivision)
        assertEquals(false, restored[0].snapEnabled)
        assertEquals(82, restored[1].bpm)
        assertEquals(1, restored[1].beatDivision)
        assertEquals(true, restored[1].snapEnabled)
    }

    @Test fun versionOneProjectsMigrateToDefaultTempoWithoutLosingTracks() {
        val track = StudioAudioProjectTrackState(
            "content://audio/legacy", "Legacy vocal", 45_000L, false, 0.8f, 0f, 0L, 0L
        )
        val legacy = org.json.JSONObject()
            .put("version", 1)
            .put("projects", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", "legacy")
                .put("name", "Old project")
                .put("updatedAtMs", 55L)
                .put("tracksJson", StudioAudioProjectCodec.encode(listOf(track))))
            ).toString()
        val migrated = StudioAudioProjectLibraryCodec.decode(legacy).single()
        assertEquals(StudioAudioBeatGridPolicy.DEFAULT_BPM, migrated.bpm)
        assertEquals(2, migrated.beatDivision)
        assertEquals(false, migrated.snapEnabled)
        assertEquals(track, migrated.tracks.single())
    }

    @Test fun invalidTempoAndSubdivisionAreNormalizedOnDecode() {
        val raw = org.json.JSONObject()
            .put("version", 2)
            .put("projects", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", "one")
                .put("name", "Mix")
                .put("bpm", 999)
                .put("beatDivision", 3)
                .put("tracksJson", StudioAudioProjectCodec.encode(emptyList()))
            )).toString()
        val restored = StudioAudioProjectLibraryCodec.decode(raw).single()
        assertEquals(StudioAudioBeatGridPolicy.MAX_BPM, restored.bpm)
        assertEquals(2, restored.beatDivision)
    }

    @Test fun malformedAndUnknownVersionsAreSafe() {
        assertTrue(StudioAudioProjectLibraryCodec.decode("not-json").isEmpty())
        assertTrue(StudioAudioProjectLibraryCodec.decode("""{"version":99,"projects":[]}""").isEmpty())
    }
}

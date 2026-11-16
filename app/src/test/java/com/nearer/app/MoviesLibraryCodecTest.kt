package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoviesLibraryCodecTest {
    private fun movie(id: Int, title: String = "Film $id") = NearerMovie(
        uri = "content://media/external/video/media/$id",
        title = title,
        favourite = id % 2 == 0,
        addedAt = 1_700_000_000_000L + id,
        resumePositionMs = id * 1_000L,
        lastWatchedAt = 1_700_000_100_000L + id
    )

    @Test fun versionedLibraryRoundTripsResumeAndFavouriteState() {
        val decoded = MoviesLibraryCodec.decode(MoviesLibraryCodec.encode(listOf(movie(1), movie(2))))
        assertEquals(2, decoded.size)
        assertEquals("Film 2", decoded[1].title)
        assertTrue(decoded[1].favourite)
        assertEquals(2_000L, decoded[1].resumePositionMs)
        assertEquals(1_700_000_100_002L, decoded[1].lastWatchedAt)
    }

    @Test fun readsLegacyArrayAndOldAddedField() {
        val legacy = JSONArray().put(
            JSONObject().put("uri", "content://media/video/4")
                .put("title", "Old Film").put("favourite", true).put("added", 99L)
        ).toString()
        val decoded = MoviesLibraryCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals(99L, decoded.single().addedAt)
        assertEquals(0L, decoded.single().resumePositionMs)
    }

    @Test fun normalizesLongTitlesAndResumeBounds() {
        val normalized = MoviesLibraryCodec.normalize(
            movie(5, "T".repeat(300)).copy(resumePositionMs = Long.MAX_VALUE)
        )!!
        assertEquals(MoviesLibraryCodec.MAX_TITLE_LENGTH, normalized.title.length)
        assertEquals(MoviesLibraryCodec.MAX_RESUME_POSITION_MS, normalized.resumePositionMs)
        assertTrue(MoviesLibraryCodec.normalize(movie(6).copy(uri = "file:///private/video.mp4")) == null)
    }

    @Test fun skipsMalformedAndDuplicateUris() {
        val rows = JSONArray()
            .put(JSONObject().put("uri", "content://media/video/1").put("title", "First"))
            .put(JSONObject().put("uri", "content://media/video/1").put("title", "Duplicate"))
            .put(JSONObject().put("uri", "file:///private/movie.mp4").put("title", "Unsafe"))
            .put("bad row")
        val decoded = MoviesLibraryCodec.decode(JSONObject().put("version", 2).put("movies", rows).toString())
        assertEquals(1, decoded.size)
        assertEquals("First", decoded.single().title)
    }

    @Test fun enforcesMaximumLibrarySize() {
        val items = (1..(MoviesLibraryCodec.MAX_MOVIES + 10)).map { movie(it) }
        assertEquals(MoviesLibraryCodec.MAX_MOVIES, MoviesLibraryCodec.decode(MoviesLibraryCodec.encode(items)).size)
    }

    @Test fun unknownOrMalformedFutureLibraryIsProtected() {
        assertTrue(!MoviesLibraryCodec.canDecode("""{"version":99,"movies":[]}"""))
        assertTrue(!MoviesLibraryCodec.canDecode("""{"version":2}"""))
        assertTrue(!MoviesLibraryCodec.canDecode("{broken"))
        assertTrue(MoviesLibraryCodec.canDecode(null))
    }
}

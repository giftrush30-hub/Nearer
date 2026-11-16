package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class NearerMovie(
    val uri: String,
    val title: String,
    val favourite: Boolean,
    val addedAt: Long,
    val resumePositionMs: Long = 0L,
    val lastWatchedAt: Long = 0L
)

internal object MoviesLibraryCodec {
    const val VERSION = 2
    const val MAX_MOVIES = 500
    const val MAX_TITLE_LENGTH = 180
    const val MAX_URI_LENGTH = 2_048
    const val MAX_RESUME_POSITION_MS = 7L * 24L * 60L * 60L * 1_000L

    fun normalize(movie: NearerMovie): NearerMovie? {
        val uri = movie.uri.trim()
        if (uri.length > MAX_URI_LENGTH || !uri.startsWith("content://", ignoreCase = true)) return null
        val title = movie.title.trim().take(MAX_TITLE_LENGTH).ifBlank { "Untitled video" }
        return NearerMovie(
            uri = uri,
            title = title,
            favourite = movie.favourite,
            addedAt = movie.addedAt.coerceAtLeast(0L),
            resumePositionMs = movie.resumePositionMs.coerceIn(0L, MAX_RESUME_POSITION_MS),
            lastWatchedAt = movie.lastWatchedAt.coerceAtLeast(0L)
        )
    }

    fun canDecode(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                if (root.optInt("version", -1) != VERSION || root.optJSONArray("movies") == null) return false
            }
            true
        }.getOrDefault(false)
    }

    fun encode(movies: List<NearerMovie>): String {
        val items = JSONArray()
        val seen = HashSet<String>()
        movies.asSequence().take(MAX_MOVIES).mapNotNull(::normalize)
            .filter { seen.add(it.uri) }
            .forEach { movie ->
                items.put(
                    JSONObject()
                        .put("uri", movie.uri)
                        .put("title", movie.title)
                        .put("favourite", movie.favourite)
                        .put("added_at", movie.addedAt)
                        .put("resume_ms", movie.resumePositionMs)
                        .put("last_watched_at", movie.lastWatchedAt)
                )
            }
        return JSONObject().put("version", VERSION).put("movies", items).toString()
    }

    fun decode(raw: String?): List<NearerMovie> {
        if (raw.isNullOrBlank() || !canDecode(raw)) return emptyList()
        return runCatching {
            val trimmed = raw.trim()
            val items = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
                JSONObject(trimmed).optJSONArray("movies") ?: JSONArray()
            }
            val result = ArrayList<NearerMovie>(minOf(items.length(), MAX_MOVIES))
            val seen = HashSet<String>()
            for (index in 0 until minOf(items.length(), MAX_MOVIES)) {
                val item = items.optJSONObject(index) ?: continue
                val normalized = normalize(
                    NearerMovie(
                        uri = item.optString("uri"),
                        title = item.optString("title", "Untitled video"),
                        favourite = item.optBoolean("favourite", false),
                        addedAt = item.optLong("added_at", item.optLong("added", 0L)),
                        resumePositionMs = item.optLong("resume_ms", 0L),
                        lastWatchedAt = item.optLong("last_watched_at", 0L)
                    )
                ) ?: continue
                if (seen.add(normalized.uri)) result += normalized
            }
            result
        }.getOrDefault(emptyList())
    }
}

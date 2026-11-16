package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Document URI grants are application-wide, not owned by one screen.
 * Release a grant only after every NeaRer feature with a persisted reference
 * has stopped using it. Unknown future data is treated conservatively.
 */
internal object MediaUriGrantRegistry {
    fun releaseIfUnreferenced(context: Context, uri: Uri) {
        val value = uri.toString()
        if (isUsedByMovies(context, value) || isUsedByLibrary(context, value) || isUsedByStudioVideo(context, value)) {
            return
        }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isUsedByMovies(context: Context, uri: String): Boolean {
        val prefs = context.getSharedPreferences("nearer_movies_local_library", Context.MODE_PRIVATE)
        val raw = prefs.getString("movies", "[]")
        if (!MoviesLibraryCodec.canDecode(raw)) return true
        return MoviesLibraryCodec.decode(raw).any { it.uri == uri }
    }

    private fun isUsedByLibrary(context: Context, uri: String): Boolean {
        val prefs = context.getSharedPreferences("nearer_library_collection", Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", "[]")
        if (raw.isNullOrBlank()) return false
        return runCatching {
            val entries = JSONArray(raw)
            (0 until entries.length()).any { index ->
                entries.optJSONObject(index)?.optString("uri") == uri
            }
        }.getOrDefault(true)
    }

    private fun isUsedByStudioVideo(context: Context, uri: String): Boolean {
        val prefs = context.getSharedPreferences("nearer_studio_video_timeline", Context.MODE_PRIVATE)
        val raw = prefs.getString("project_v1", null) ?: return false
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != 1) return true
            val project = StudioVideoProjectCodec.decode(raw)
            project.clips.any { it.uri == uri } || project.backgroundAudioUri == uri
        }.getOrDefault(true)
    }
}

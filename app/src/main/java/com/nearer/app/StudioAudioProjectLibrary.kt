package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioAudioProjectLibraryEntry(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
    val tracks: List<StudioAudioProjectTrackState>
)

/** Bounded, versioned local library for independent Sound Desk arrangements. */
internal object StudioAudioProjectLibraryCodec {
    private const val VERSION = 1
    const val MAX_PROJECTS = 20
    const val MAX_NAME_LENGTH = 48

    fun normalizeName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifBlank { "Untitled Mix" }

    fun encode(projects: List<StudioAudioProjectLibraryEntry>): String {
        val items = JSONArray()
        projects.distinctBy { it.id.trim() }.take(MAX_PROJECTS).forEach { project ->
            val id = project.id.trim().take(80)
            if (id.isNotBlank()) items.put(JSONObject()
                .put("id", id)
                .put("name", normalizeName(project.name))
                .put("updatedAtMs", project.updatedAtMs.coerceAtLeast(0L))
                .put("tracksJson", StudioAudioProjectCodec.encode(project.tracks)))
        }
        return JSONObject().put("version", VERSION).put("projects", items).toString()
    }

    fun decode(raw: String?): List<StudioAudioProjectLibraryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return emptyList()
            val items = root.optJSONArray("projects") ?: return emptyList()
            buildList {
                val ids = HashSet<String>()
                for (index in 0 until items.length()) {
                    if (size >= MAX_PROJECTS) break
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id", "").trim().take(80)
                    if (id.isBlank() || !ids.add(id)) continue
                    add(StudioAudioProjectLibraryEntry(
                        id = id,
                        name = normalizeName(item.optString("name", "Untitled Mix")),
                        updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                        tracks = StudioAudioProjectCodec.decode(item.optString("tracksJson", null))
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }
}
